package com.fc.v2.service.zw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 本期/上期差异：必须按同一行次口径对齐，明确指出第几行、两条原始行各是什么。
 */
public class IssueDiffTest {

    private static IssueDiff.Snapshot snap(int row, String raw) {
        return new IssueDiff.Snapshot(row, raw);
    }

    @Test
    public void identicalBatchesHaveNoDiff() {
        List<IssueDiff.Snapshot> a = Arrays.asList(snap(1, "r1"), snap(2, "r2"));
        assertTrue(IssueDiff.compare(a, a).isEmpty());
        assertTrue(IssueDiff.compare(a, Arrays.asList(snap(1, "r1"), snap(2, "r2"))).isEmpty());
    }

    @Test
    public void changedRowPointsAtRowNoAndBothRawLines() {
        List<IssueDiff.Snapshot> cur = Arrays.asList(snap(1, "r1"), snap(2, "CUR-2"));
        List<IssueDiff.Snapshot> prev = Arrays.asList(snap(1, "r1"), snap(2, "PREV-2"));
        List<IssueDiff.RowDiff> diffs = IssueDiff.compare(cur, prev);
        assertEquals(1, diffs.size());
        IssueDiff.RowDiff d = diffs.get(0);
        assertEquals(2, d.getRowNo());
        assertEquals("CHANGED", d.getKind());
        assertEquals("CUR-2", d.getRawLineCurrent());
        assertEquals("PREV-2", d.getRawLinePrevious());
    }

    @Test
    public void onlyCurrentAndOnlyPreviousAreBothReported() {
        List<IssueDiff.RowDiff> diffs = IssueDiff.compare(
                Arrays.asList(snap(1, "r1"), snap(3, "r3")),
                Arrays.asList(snap(1, "r1"), snap(2, "r2")));
        assertEquals(2, diffs.size());
        // 行次升序
        assertEquals(2, diffs.get(0).getRowNo());
        assertEquals("ONLY_PREVIOUS", diffs.get(0).getKind());
        assertEquals(3, diffs.get(1).getRowNo());
        assertEquals("ONLY_CURRENT", diffs.get(1).getKind());
    }

    @Test
    public void neverMatchesByValueAlwaysByRowNo() {
        // 行 1/2 互换了内容：按行次口径，两行都对不上（不许按原文自己配对当成没变）
        List<IssueDiff.RowDiff> diffs = IssueDiff.compare(
                Arrays.asList(snap(1, "AAA"), snap(2, "BBB")),
                Arrays.asList(snap(1, "BBB"), snap(2, "AAA")));
        assertEquals(2, diffs.size());
    }

    @Test
    public void emptySideReportsAllAsOnly() {
        List<IssueDiff.RowDiff> diffs = IssueDiff.compare(
                Arrays.asList(snap(1, "r1"), snap(2, "r2")),
                Collections.<IssueDiff.Snapshot>emptyList());
        assertEquals(2, diffs.size());
        assertEquals("ONLY_CURRENT", diffs.get(0).getKind());
    }
}
