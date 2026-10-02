package com.fc.v2.service.zw;

import java.util.List;

/**
 * 一次整批导入的回值——前台只按这份东西向人交代：
 * <ul>
 *   <li>{@code rejected=true}：结构不合格（点位/时间/读数格式/批次口径），整批拦下、一笔未落，
 *       errors 逐行写明第几行为什么；</li>
 *   <li>否则 landed 为正式落账行数，issues 为当场挑出待人工处理的异常条数，
 *       skipped 为同批重导时按行跳过（已落账/已有异常、含已归档）的条数。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-02
 */
public class ImportOutcome {

    private boolean rejected;
    private String batchNo;
    private int total;
    private int landed;
    private int issues;
    private int skipped;
    private List<ImpLineParser.LineError> errors;

    public static ImportOutcome rejected(String batchNo, int total, List<ImpLineParser.LineError> errors) {
        ImportOutcome o = new ImportOutcome();
        o.rejected = true;
        o.batchNo = batchNo;
        o.total = total;
        o.errors = errors;
        return o;
    }

    public static ImportOutcome ok(String batchNo, int total, int landed, int issues, int skipped) {
        ImportOutcome o = new ImportOutcome();
        o.rejected = false;
        o.batchNo = batchNo;
        o.total = total;
        o.landed = landed;
        o.issues = issues;
        o.skipped = skipped;
        return o;
    }

    public boolean isRejected() {
        return rejected;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public int getTotal() {
        return total;
    }

    public int getLanded() {
        return landed;
    }

    public int getIssues() {
        return issues;
    }

    public int getSkipped() {
        return skipped;
    }

    public List<ImpLineParser.LineError> getErrors() {
        return errors;
    }
}
