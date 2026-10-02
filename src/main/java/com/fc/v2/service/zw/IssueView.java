package com.fc.v2.service.zw;

import java.util.Date;

import com.fc.v2.model.auto.TZwIssue;
import com.fc.v2.model.auto.TZwLimRule;

/**
 * 异常核实下钻的并排视图：原始行 / 规则条文 / 复核结论 三块摆在一起，页面不自算、不拼装。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class IssueView {

    /** 异常台账行（含原始行原文、校验结论、核实结论、核实人、核实时刻、归档标识） */
    private TZwIssue issue;
    /** 原始行申报规则代号的条文（按代号查；查不到给 null，页面明写「底档无此代号」） */
    private TZwLimRule declaredRule;
    /** 裁决出口按检测当天选中的线条文（INVALID/NO_RULE 时可能为空） */
    private TZwLimRule hitRule;
    /** 同批核实进度：总数、已确认数、已核实（归档）数 */
    private int batchTotal;
    private int batchConfirmed;
    private boolean batchArchived;

    public TZwIssue getIssue() {
        return issue;
    }

    public void setIssue(TZwIssue issue) {
        this.issue = issue;
    }

    public TZwLimRule getDeclaredRule() {
        return declaredRule;
    }

    public void setDeclaredRule(TZwLimRule declaredRule) {
        this.declaredRule = declaredRule;
    }

    public TZwLimRule getHitRule() {
        return hitRule;
    }

    public void setHitRule(TZwLimRule hitRule) {
        this.hitRule = hitRule;
    }

    public int getBatchTotal() {
        return batchTotal;
    }

    public void setBatchTotal(int batchTotal) {
        this.batchTotal = batchTotal;
    }

    public int getBatchConfirmed() {
        return batchConfirmed;
    }

    public void setBatchConfirmed(int batchConfirmed) {
        this.batchConfirmed = batchConfirmed;
    }

    public boolean isBatchArchived() {
        return batchArchived;
    }

    public void setBatchArchived(boolean batchArchived) {
        this.batchArchived = batchArchived;
    }

    /** 核实结论文面（归档后只读展示用） */
    public String getVerifyResultText() {
        if (issue == null || issue.getVerifyResult() == null) {
            return "未核实";
        }
        return issue.getVerifyResult().intValue() == 1 ? "核实成" : "核实不成";
    }

    /** 校验时间文面统一走唯一时间尺 */
    public String getVerifyTimeText() {
        Date t = issue == null ? null : issue.getVerifyTime();
        return ImpLineParser.formatTime(t);
    }
}
