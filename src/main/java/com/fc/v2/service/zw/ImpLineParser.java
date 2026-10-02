package com.fc.v2.service.zw;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 化验读数 Excel 原始单元格 → {@link ImpLine} 的唯一解析口径（纯函数、无 Spring、无 POI、无库）。
 *
 * <p>列序七列固定：采样批次 | 读数明细 | 规则代号 | 点位编号 | 检测时间 | 是否有效 | 是否已复核。
 * 读数明细一列内部三段「项目代号|样本类别|读数」，半角「|」、全角「｜」都收。
 *
 * <p><b>硬错（结构不合格）</b>：列不够、批次空、点位空、明细不是三段、项目空、样本类别不在三类、
 * 读数不是数字、时间解析不出、有效/复核认不出、行间批次号不一致——任一条命中，整批拦下，一笔不落。
 * 读数虽是数字但越出合理量域（负数/超大怪值）不在此处拦，交给裁决出口作 INVALID 异常登记：
 * 那条读数要让人当场核实，而不是让整批合法数据停在库外。
 *
 * <p>时间只有一把尺：{@link #TIME_PATTERN}，与实体 {@code @JsonFormat} 同串，
 * 页面展示和接口收发永远是一个尺子；只给到日期时按当日 00:00:00 补全，不留第二种口径。
 *
 * @author fuce
 * @date 2026-10-02
 */
public final class ImpLineParser {

    /** 全站检测时间的唯一格式串（页面、接口、导出同此一串） */
    public static final String TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final int COLUMN_COUNT = 7;
    private static final int MAX_RAW_LINE = 900;

    private ImpLineParser() {
    }

    /** 一行解析失败：原表行次 + 原因（整批拦截时逐行列给前台看） */
    public static final class LineError {
        private final int rowNo;
        private final String reason;

        public LineError(int rowNo, String reason) {
            this.rowNo = rowNo;
            this.reason = reason;
        }

        public int getRowNo() {
            return rowNo;
        }

        public String getReason() {
            return reason;
        }
    }

    /** 整页解析结果：有硬错时 {@link #rejected} 为真，lines 不得落库 */
    public static final class ParseOutcome {
        private final boolean rejected;
        private final List<LineError> errors;
        private final List<ImpLine> lines;

        private ParseOutcome(boolean rejected, List<LineError> errors, List<ImpLine> lines) {
            this.rejected = rejected;
            this.errors = errors;
            this.lines = lines;
        }

        public boolean isRejected() {
            return rejected;
        }

        public List<LineError> getErrors() {
            return errors;
        }

        public List<ImpLine> getLines() {
            return lines;
        }
    }

    /**
     * 解析整张表。{@code rawRows} 为按行读出的七列原文（已去表头、已跳空行），
     * {@code declaredBatch} 为上传时前台声明的批次号（可空；不空时每一行都得与它同号）。
     */
    public static ParseOutcome parse(List<String[]> rawRows, String declaredBatch) {
        List<LineError> errors = new ArrayList<LineError>();
        List<ImpLine> lines = new ArrayList<ImpLine>();
        if (rawRows == null || rawRows.isEmpty()) {
            // 空文件不叫执行出错，但也没东西可落
            return new ParseOutcome(false, errors, lines);
        }
        String expectBatch = trimToNull(declaredBatch);
        if (expectBatch == null) {
            // 前台没声明批次，就以第一行写的号立为整批口径——一张当月表只准一个批次号
            for (String[] r : rawRows) {
                if (r != null && r.length > 0) {
                    String b = trimToNull(r[0]);
                    if (b != null) {
                        expectBatch = b;
                        break;
                    }
                }
            }
        }
        final String batchScope = expectBatch;
        for (int i = 0; i < rawRows.size(); i++) {
            int rowNo = i + 1;
            String[] cells = rawRows.get(i);
            String rowText = joinRaw(cells);
            String err = parseOne(rowNo, cells, batchScope, lines, rowText);
            if (err != null) {
                errors.add(new LineError(rowNo, err));
            }
        }
        return new ParseOutcome(!errors.isEmpty(), errors, lines);
    }

    /** 解析单行；通过则追加进 lines 并返回 null，否则返回原因，不追加 */
    private static String parseOne(int rowNo, String[] cells, String declaredBatch,
                                   List<ImpLine> out, String rawLine) {
        if (cells == null || cells.length < COLUMN_COUNT) {
            return "列数不足：应有七列（采样批次/读数明细/规则代号/点位编号/检测时间/是否有效/是否已复核），实际 "
                    + (cells == null ? 0 : cells.length) + " 列";
        }
        String batchNo = trimToNull(cells[0]);
        if (batchNo == null) {
            return "采样批次为空";
        }
        if (declaredBatch != null && !declaredBatch.equals(batchNo)) {
            return "批次号对不上：本行「" + batchNo + "」与上传批次「" + declaredBatch + "」不一致";
        }

        String detail = trimToNull(cells[1]);
        if (detail == null) {
            return "读数明细为空（应为 项目代号|样本类别|读数 三段）";
        }
        String[] parts = detail.split("[|｜]", -1);
        if (parts.length != 3) {
            return "读数明细不是三段「项目代号|样本类别|读数」：「" + detail + "」";
        }
        String itemCode = trimToNull(parts[0]);
        String sampleType = trimToNull(parts[1]);
        String qtyText = trimToNull(parts[2]);
        if (itemCode == null) {
            return "读数明细缺项目代号：「" + detail + "」";
        }
        if (!WaterGrade.TYPE_SOURCE.equals(sampleType)
                && !WaterGrade.TYPE_FINISHED.equals(sampleType)
                && !WaterGrade.TYPE_TAP.equals(sampleType)) {
            return "样本类别不在三类（水源水/出厂水/管网末梢）：「" + nullToEmpty(sampleType) + "」";
        }
        if (qtyText == null) {
            return "读数明细缺读数：「" + detail + "」";
        }
        BigDecimal qty;
        try {
            qty = new BigDecimal(qtyText);
        } catch (NumberFormatException e) {
            // 读数不是数字＝格式不合格，整批拦下；数字但越界才是逐行异常
            return "读数不是数字：「" + qtyText + "」";
        }

        String ruleCodeDecl = trimToNull(cells[2]);
        String siteNo = trimToNull(cells[3]);
        if (siteNo == null) {
            return "点位编号为空";
        }
        Date testAt = parseTime(cells[4]);
        if (testAt == null) {
            return "检测时间认不出（须为 " + TIME_PATTERN + " 或 " + DATE_PATTERN
                    + "）：「" + nullToEmpty(cells[4]).trim() + "」";
        }
        Integer valid = parseYesNo(cells[5]);
        if (valid == null) {
            return "是否有效认不出（是/否）：「" + nullToEmpty(cells[5]).trim() + "」";
        }
        Integer reviewed = parseYesNo(cells[6]);
        if (reviewed == null) {
            return "是否已复核认不出（是/否）：「" + nullToEmpty(cells[6]).trim() + "」";
        }
        out.add(new ImpLine(rowNo, batchNo, siteNo, itemCode, sampleType, qty, testAt,
                valid.intValue(), reviewed.intValue(), ruleCodeDecl,
                rawLine.length() > MAX_RAW_LINE ? rawLine.substring(0, MAX_RAW_LINE) : rawLine));
        return null;
    }

    /** 检测时间唯一解析口：先长后短，严格不容错（lenient=false） */
    public static Date parseTime(String text) {
        String t = trimToNull(text);
        if (t == null) {
            return null;
        }
        Date d = tryParse(t, TIME_PATTERN);
        if (d == null && t.length() == DATE_PATTERN.length()) {
            d = tryParse(t, DATE_PATTERN);
        }
        return d;
    }

    private static Date tryParse(String text, String pattern) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(pattern);
            sdf.setLenient(false);
            return sdf.parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    /** 是/否、1/0 都认；其余一律认不出（硬错，不许默认成「否」塞进库） */
    public static Integer parseYesNo(String text) {
        String t = trimToNull(text);
        if (t == null) {
            return null;
        }
        if ("是".equals(t) || "1".equals(t) || "Y".equalsIgnoreCase(t) || "TRUE".equalsIgnoreCase(t)) {
            return Integer.valueOf(1);
        }
        if ("否".equals(t) || "0".equals(t) || "N".equalsIgnoreCase(t) || "FALSE".equalsIgnoreCase(t)) {
            return Integer.valueOf(0);
        }
        return null;
    }

    /** 时间统一按唯一格式串转文面（页面/接口/导出都调它，不许多写一个 SimpleDateFormat） */
    public static String formatTime(Date date) {
        if (date == null) {
            return "";
        }
        return new SimpleDateFormat(TIME_PATTERN).format(date);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String joinRaw(String[] cells) {
        if (cells == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < COLUMN_COUNT; i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append(i < cells.length ? nullToEmpty(cells[i]).trim() : "");
        }
        return sb.toString();
    }

    /** 表头七列名目（模板下载与上传读表同一套） */
    public static List<String> headers() {
        return Arrays.asList("采样批次", "读数明细", "规则代号", "点位编号", "检测时间", "是否有效", "是否已复核");
    }
}
