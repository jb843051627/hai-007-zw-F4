package com.fc.v2.service.zw;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本期与上期差异的唯一比对口径（纯函数，可直接单测）。
 *
 * <p>两个批次按<b>同一个行次口径</b>对齐：原表行次就是比对键，第 N 行只跟第 N 行比，
 * 不许按读数或点位自己配对。逐行比原始行原文：原文不同（含仅一批有此行）即挑出，
 * 明确指出是第几行、两条原始行分别长什么样。比对的输入是明细表（正式账＋异常台账）
 * 合并后的逐行快照，报表本身不参与比对——报表改了不算数，明细里那条原始行才是准。
 *
 * @author fuce
 * @date 2026-10-02
 */
public final class IssueDiff {

    private IssueDiff() {
    }

    /** 一条明细的最小比对快照（行次 + 原始行原文） */
    public static final class Snapshot {
        private final int rowNo;
        private final String rawLine;

        public Snapshot(int rowNo, String rawLine) {
            this.rowNo = rowNo;
            this.rawLine = rawLine == null ? "" : rawLine;
        }

        public int getRowNo() {
            return rowNo;
        }

        public String getRawLine() {
            return rawLine;
        }
    }

    /** 一行差异：行次 + 本期/上期两条原始行（仅一批有时另一条为空） */
    public static final class RowDiff {
        private final int rowNo;
        private final String rawLineCurrent;
        private final String rawLinePrevious;
        private final String kind;

        RowDiff(int rowNo, String cur, String prev, String kind) {
            this.rowNo = rowNo;
            this.rawLineCurrent = cur;
            this.rawLinePrevious = prev;
            this.kind = kind;
        }

        public int getRowNo() {
            return rowNo;
        }

        public String getRawLineCurrent() {
            return rawLineCurrent;
        }

        public String getRawLinePrevious() {
            return rawLinePrevious;
        }

        /** CHANGED 两行都在但原文对不上；ONLY_CURRENT 本期新增；ONLY_PREVIOUS 本期缺失 */
        public String getKind() {
            return kind;
        }

        public String getKindText() {
            if ("CHANGED".equals(kind)) {
                return "原始行对不上";
            }
            if ("ONLY_CURRENT".equals(kind)) {
                return "上期无此行";
            }
            return "本期无此行";
        }
    }

    /**
     * 按行次口径比对两批。返回差异列表按行次升序；完全一致返回空表。
     *
     * @param current  本期明细快照
     * @param previous 上期明细快照
     */
    public static List<RowDiff> compare(List<Snapshot> current, List<Snapshot> previous) {
        Map<Integer, String> cur = index(current);
        Map<Integer, String> prev = index(previous);
        List<Integer> keys = new ArrayList<Integer>();
        for (Integer k : cur.keySet()) {
            if (!keys.contains(k)) {
                keys.add(k);
            }
        }
        for (Integer k : prev.keySet()) {
            if (!keys.contains(k)) {
                keys.add(k);
            }
        }
        Collections.sort(keys);
        List<RowDiff> diffs = new ArrayList<RowDiff>();
        for (Integer rowNo : keys) {
            String a = cur.get(rowNo);
            String b = prev.get(rowNo);
            boolean hasA = a != null;
            boolean hasB = b != null;
            if (hasA && hasB) {
                if (!a.equals(b)) {
                    diffs.add(new RowDiff(rowNo.intValue(), a, b, "CHANGED"));
                }
            } else if (hasA) {
                diffs.add(new RowDiff(rowNo.intValue(), a, "", "ONLY_CURRENT"));
            } else {
                diffs.add(new RowDiff(rowNo.intValue(), "", b, "ONLY_PREVIOUS"));
            }
        }
        return diffs;
    }

    private static Map<Integer, String> index(List<Snapshot> rows) {
        Map<Integer, String> map = new LinkedHashMap<Integer, String>();
        if (rows != null) {
            for (Snapshot s : rows) {
                // 同批行次唯一（库上有 uk）；重复键保留首条，口径不抖
                if (!map.containsKey(Integer.valueOf(s.getRowNo()))) {
                    map.put(Integer.valueOf(s.getRowNo()), s.getRawLine());
                }
            }
        }
        return map;
    }
}
