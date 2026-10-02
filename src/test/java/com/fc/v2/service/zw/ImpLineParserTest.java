package com.fc.v2.service.zw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 解析口径单测：七列结构、读数明细三段、唯一时间尺、批次整表同号。
 * 越界但合法的数字读数必须放行（那是软错，归异常台账，不是整批硬拦）。
 */
public class ImpLineParserTest {

    private static String[] row(String batch, String detail, String rule, String site,
                                String time, String valid, String review) {
        return new String[]{batch, detail, rule, site, time, valid, review};
    }

    @Test
    public void validRowsParse() {
        List<String[]> rows = Arrays.asList(
                row("B202610", "CL|出厂水|0.18", "CL-FW-2026", "ZS00", "2026-10-02 09:00:00", "是", "否"),
                row("B202610", "CL｜出厂水｜0.21", "", "ZS00", "2026-10-03", "1", "0"));
        ImpLineParser.ParseOutcome out = ImpLineParser.parse(rows, null);
        assertFalse(out.isRejected(), "结构全格不应被整批拦截");
        assertEquals(2, out.getLines().size());
        ImpLine first = out.getLines().get(0);
        assertEquals(1, first.getRowNo());
        assertEquals("B202610", first.getBatchNo());
        assertEquals("CL", first.getItemCode());
        assertEquals("出厂水", first.getSampleType());
        assertEquals(new BigDecimal("0.18"), first.getQty());
        assertEquals(1, first.getValidFlag());
        assertEquals(0, first.getReviewFlag());
        assertEquals("CL-FW-2026", first.getRuleCodeDecl());
        // 全角分隔符与只给日期（补 00:00:00）都走同一把尺
        ImpLine second = out.getLines().get(1);
        assertEquals(new BigDecimal("0.21"), second.getQty());
        assertEquals("2026-10-03 00:00:00", ImpLineParser.formatTime(second.getTestAt()));
    }

    @Test
    public void outOfRangeButNumericIsNotHardRejected() {
        // 120 越过合理量域，但它是数字：解析必须放行，交给裁决出口作 INVALID 异常登记
        List<String[]> rows = Collections.singletonList(
                row("B202610", "CL|出厂水|120", "CL-FW-2026", "ZS00", "2026-10-02 09:00:00", "是", "否"));
        ImpLineParser.ParseOutcome out = ImpLineParser.parse(rows, null);
        assertFalse(out.isRejected());
        assertEquals(1, out.getLines().size());
        assertEquals(new BigDecimal("120"), out.getLines().get(0).getQty());
    }

    @Test
    public void nonNumericQtyIsHardError() {
        List<String[]> rows = Collections.singletonList(
                row("B202610", "CL|出厂水|零点一八", "CL-FW-2026", "ZS00", "2026-10-02 09:00:00", "是", "否"));
        ImpLineParser.ParseOutcome out = ImpLineParser.parse(rows, null);
        assertTrue(out.isRejected());
        assertEquals(1, out.getErrors().size());
        assertTrue(out.getErrors().get(0).getReason().contains("读数不是数字"));
        assertTrue(out.getLines().isEmpty());
    }

    @Test
    public void badDetailSegmentCountIsHardError() {
        List<String[]> rows = Collections.singletonList(
                row("B202610", "CL|出厂水", "CL-FW-2026", "ZS00", "2026-10-02 09:00:00", "是", "否"));
        ImpLineParser.ParseOutcome out = ImpLineParser.parse(rows, null);
        assertTrue(out.isRejected());
        assertTrue(out.getErrors().get(0).getReason().contains("不是三段"));
    }

    @Test
    public void unknownSampleTypeIsHardError() {
        List<String[]> rows = Collections.singletonList(
                row("B202610", "CL|直饮水|0.1", "CL-FW-2026", "ZS00", "2026-10-02 09:00:00", "是", "否"));
        ImpLineParser.ParseOutcome out = ImpLineParser.parse(rows, null);
        assertTrue(out.isRejected());
        assertTrue(out.getErrors().get(0).getReason().contains("样本类别不在三类"));
    }

    @Test
    public void unparsableTimeIsHardError() {
        List<String[]> rows = Collections.singletonList(
                row("B202610", "CL|出厂水|0.1", "CL-FW-2026", "ZS00", "2026/10/02 上午", "是", "否"));
        ImpLineParser.ParseOutcome out = ImpLineParser.parse(rows, null);
        assertTrue(out.isRejected());
        assertTrue(out.getErrors().get(0).getReason().contains("检测时间认不出"));
    }

    @Test
    public void illegalCalendarDateIsHardError() {
        // lenient=false：10 月没有 32 号，不许悄悄滚成 11 月
        List<String[]> rows = Collections.singletonList(
                row("B202610", "CL|出厂水|0.1", "CL-FW-2026", "ZS00", "2026-10-32 09:00:00", "是", "否"));
        ImpLineParser.ParseOutcome out = ImpLineParser.parse(rows, null);
        assertTrue(out.isRejected());
    }

    @Test
    public void unrecognizableYesNoIsHardError() {
        List<String[]> rows = Collections.singletonList(
                row("B202610", "CL|出厂水|0.1", "CL-FW-2026", "ZS00", "2026-10-02 09:00:00", "对对对", "否"));
        ImpLineParser.ParseOutcome out = ImpLineParser.parse(rows, null);
        assertTrue(out.isRejected());
        assertTrue(out.getErrors().get(0).getReason().contains("是否有效认不出"));
    }

    @Test
    public void missingSiteIsHardError() {
        List<String[]> rows = Collections.singletonList(
                row("B202610", "CL|出厂水|0.1", "CL-FW-2026", "  ", "2026-10-02 09:00:00", "是", "否"));
        ImpLineParser.ParseOutcome out = ImpLineParser.parse(rows, null);
        assertTrue(out.isRejected());
        assertTrue(out.getErrors().get(0).getReason().contains("点位编号为空"));
    }

    @Test
    public void twoBatchesInOneSheetIsRejected() {
        // 没声明批次：以第一行立口径，第二行换号即整批拦
        List<String[]> rows = Arrays.asList(
                row("B202610", "CL|出厂水|0.1", "CL-FW-2026", "ZS00", "2026-10-02 09:00:00", "是", "否"),
                row("B202611", "CL|出厂水|0.1", "CL-FW-2026", "ZS00", "2026-10-03 09:00:00", "是", "否"));
        ImpLineParser.ParseOutcome out = ImpLineParser.parse(rows, null);
        assertTrue(out.isRejected());
        assertTrue(out.getErrors().get(0).getReason().contains("批次号对不上"));
    }

    @Test
    public void declaredBatchMismatchIsRejected() {
        List<String[]> rows = Collections.singletonList(
                row("B202611", "CL|出厂水|0.1", "CL-FW-2026", "ZS00", "2026-10-02 09:00:00", "是", "否"));
        ImpLineParser.ParseOutcome out = ImpLineParser.parse(rows, "B202610");
        assertTrue(out.isRejected());
        assertTrue(out.getErrors().get(0).getReason().contains("与上传批次"));
    }

    @Test
    public void emptySheetIsNotRejected() {
        ImpLineParser.ParseOutcome out = ImpLineParser.parse(Collections.<String[]>emptyList(), "B202610");
        assertFalse(out.isRejected());
        assertEquals(0, out.getLines().size());
    }

    @Test
    public void rawLineKeepsSevenColumnsInOrder() {
        List<String[]> rows = Collections.singletonList(
                row("B202610", "CL|出厂水|0.1", "CL-FW-2026", "ZS00", "2026-10-02 09:00:00", "是", "否"));
        ImpLine line = ImpLineParser.parse(rows, null).getLines().get(0);
        assertEquals("B202610 | CL|出厂水|0.1 | CL-FW-2026 | ZS00 | 2026-10-02 09:00:00 | 是 | 否",
                line.getRawLine());
    }
}
