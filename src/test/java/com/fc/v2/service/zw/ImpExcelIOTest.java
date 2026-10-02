package com.fc.v2.service.zw;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Excel 读写口径：模板能被自己的读口读回；同一份明细导出两次字节完全一致
 * （同一行点位、同一导出时间两次导出必须完全一样）。
 */
public class ImpExcelIOTest {

    @Test
    public void templateRoundTripsThroughReader() throws Exception {
        byte[] tpl = ImpExcelIO.writeTemplate();
        assertNotNull(tpl);
        List<String[]> rows = ImpExcelIO.readRows(new ByteArrayInputStream(tpl));
        // 表头被识别并跳过，只剩一行示范
        assertEquals(1, rows.size());
        assertEquals("B202610", rows.get(0)[0]);
        assertEquals("ZS00", rows.get(0)[3]);
    }

    @Test
    public void reportBytesAreDeterministicAcrossExports() {
        List<String> head = ImpLineParser.headers();
        List<List<String>> data = Arrays.asList(
                Arrays.asList("B202610", "1", "ZS00", "CL", "出厂水", "0.18",
                        "2026-10-02 09:00:00", "是", "否", "合格", "CL-FW-2026"),
                Arrays.asList("B202610", "2", "ZS00", "CL", "出厂水", "0.42",
                        "2026-10-02 10:00:00", "是", "否", "超标", "CL-FW-2026"));
        byte[] first = ImpExcelIO.writeReport(head, data, "检测明细");
        byte[] second = ImpExcelIO.writeReport(head, data, "检测明细");
        assertArrayEquals(first, second, "同内容两次导出必须字节一致");
    }

    @Test
    public void twoSheetExportIsAlsoDeterministic() {
        byte[] a = ImpExcelIO.writeTwoSheets(
                Arrays.asList("批次", "行次"), Arrays.asList(Arrays.asList("B202610", "1")), "明细",
                Arrays.asList("点位", "计数"), Arrays.asList(Arrays.asList("ZS00", "1")), "汇总");
        byte[] b = ImpExcelIO.writeTwoSheets(
                Arrays.asList("批次", "行次"), Arrays.asList(Arrays.asList("B202610", "1")), "明细",
                Arrays.asList("点位", "计数"), Arrays.asList(Arrays.asList("ZS00", "1")), "汇总");
        assertArrayEquals(a, b);
        assertTrue(a.length > 0);
    }
}
