package com.fc.v2.service.zw;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 化验读数 Excel 的唯一读写口（xlsx；本机不装 Office、不做在线预览，文件交给业务人员自行打开）。
 *
 * <p>读：首行表头（首格为「采样批次」）跳过；空行跳过；日期单元格按 {@link ImpLineParser#TIME_PATTERN}
 * 取值，数字按 ROOT locale 取纯数字文本——读数列是不是数字仍由 {@link ImpLineParser} 判，这里不越权。
 *
 * <p>写：只产字符串单元格，绝不在内容里塞「导出时刻」一类随时间变的东西；最后把 xlsx（zip）
 * 的条目时间统一钉死，保证同一份明细导出两次字节完全相同。
 *
 * @author fuce
 * @date 2026-10-02
 */
public final class ImpExcelIO {

    /** 单个工作簿数据行上限（与导入服务一致，防超大文件压垮） */
    public static final int MAX_DATA_ROWS = 500;

    private ImpExcelIO() {
    }

    /** 读成七列原文（已跳表头与空行）。文件不是有效 Excel 时抛 IOException 由上层整批挡回 */
    public static List<String[]> readRows(InputStream in) throws IOException {
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        List<String[]> rows = new ArrayList<String[]>();
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getNumberOfSheets() == 0 ? null : wb.getSheetAt(0);
            if (sheet == null) {
                return rows;
            }
            int last = sheet.getLastRowNum();
            for (int r = 0; r <= last; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String[] cells = new String[7];
                boolean allBlank = true;
                for (int c = 0; c < 7; c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String text;
                    if (cell == null) {
                        text = "";
                    } else if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                            && DateUtil.isCellDateFormatted(cell)) {
                        // 日期单元格按全站唯一时间串取，页面与接口都认这一把尺
                        Date d = cell.getDateCellValue();
                        text = new SimpleDateFormat(ImpLineParser.TIME_PATTERN).format(d);
                    } else {
                        text = formatter.formatCellValue(cell);
                    }
                    text = text == null ? "" : text.trim();
                    cells[c] = text;
                    if (!text.isEmpty()) {
                        allBlank = false;
                    }
                }
                if (allBlank) {
                    continue;
                }
                // 首行表头：第一格写着「采样批次」即认定为表头
                if (r == sheet.getFirstRowNum() && "采样批次".equals(cells[0])) {
                    continue;
                }
                rows.add(cells);
                if (rows.size() > MAX_DATA_ROWS) {
                    throw new IOException("数据行超过单批上限 " + MAX_DATA_ROWS + " 行，请拆批后重传");
                }
            }
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Excel 解析失败：" + e.getMessage(), e);
        }
        return rows;
    }

    /** 生成导入模板（表头 + 一行填法示范） */
    public static byte[] writeTemplate() {
        List<String> example = new ArrayList<String>();
        example.add("B202610");
        example.add("CL|出厂水|0.18");
        example.add("CL-FW-2026");
        example.add("ZS00");
        example.add("2026-10-02 09:00:00");
        example.add("是");
        example.add("否");
        return writeWorkbook(ImpLineParser.headers(), java.util.Collections.singletonList(example), "当月读数");
    }

    /**
     * 生成明细报表：表头 + 逐行字符串。不写任何随导出时刻变化的内容。
     */
    public static byte[] writeReport(List<String> headers, List<List<String>> dataRows, String sheetName) {
        return writeWorkbook(headers, dataRows, sheetName);
    }

    /** 两个固定工作表一次生成（明细 + 汇总），确定性口径与单表完全一致 */
    public static byte[] writeTwoSheets(List<String> head1, List<List<String>> rows1, String name1,
                                        List<String> head2, List<List<String>> rows2, String name2) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.getProperties().getCoreProperties().setCreated(java.util.Optional.of(new Date(0L)));
            wb.getProperties().getCoreProperties().setModified(java.util.Optional.of(new Date(0L)));
            fillSheet(wb, head1, rows1, name1);
            fillSheet(wb, head2, rows2, name2);
            wb.write(out);
            return freezeZipEntries(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Excel 生成失败：" + e.getMessage(), e);
        }
    }

    private static void fillSheet(XSSFWorkbook wb, List<String> headers,
                                  List<List<String>> dataRows, String sheetName) {
        Sheet sheet = wb.createSheet(sheetName == null ? "sheet1" : sheetName);
        CellStyle head = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setBold(true);
        head.setFont(font);
        int r = 0;
        Row hr = sheet.createRow(r++);
        for (int i = 0; i < headers.size(); i++) {
            Cell c = hr.createCell(i);
            c.setCellValue(headers.get(i));
            c.setCellStyle(head);
        }
        for (List<String> data : dataRows) {
            Row row = sheet.createRow(r++);
            for (int i = 0; i < headers.size(); i++) {
                row.createCell(i).setCellValue(i < data.size() && data.get(i) != null ? data.get(i) : "");
            }
        }
    }

    private static byte[] writeWorkbook(List<String> headers, List<List<String>> dataRows, String sheetName) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // 文档属性里的创建/修改时间也钉死，不留下随导出时刻变的字节
            wb.getProperties().getCoreProperties().setCreated(java.util.Optional.of(new Date(0L)));
            wb.getProperties().getCoreProperties().setModified(java.util.Optional.of(new Date(0L)));
            fillSheet(wb, headers, dataRows, sheetName);
            wb.write(out);
            // xlsx 是 zip；把条目时间统一钉到固定值，同样内容导出两次字节完全一致
            return freezeZipEntries(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Excel 生成失败：" + e.getMessage(), e);
        }
    }

    /** 重写 zip 中央目录：同名条目顺序不变，时间钉死，deflate 对同一输入产出同一字节 */
    private static byte[] freezeZipEntries(byte[] xlsxBytes) throws IOException {
        ByteArrayOutputStream fixed = new ByteArrayOutputStream();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(xlsxBytes));
             ZipOutputStream zout = new ZipOutputStream(fixed)) {
            ZipEntry entry;
            byte[] buf = new byte[4096];
            while ((entry = zin.getNextEntry()) != null) {
                ZipEntry ne = new ZipEntry(entry.getName());
                ne.setTime(0L);
                zout.putNextEntry(ne);
                int n;
                while ((n = zin.read(buf)) > 0) {
                    zout.write(buf, 0, n);
                }
                zout.closeEntry();
                zin.closeEntry();
            }
        }
        return fixed.toByteArray();
    }

    /** 供下载接口直接写流（保持字节级产物一致） */
    public static void writeBytes(OutputStream out, byte[] bytes) throws IOException {
        out.write(bytes);
        out.flush();
    }
}
