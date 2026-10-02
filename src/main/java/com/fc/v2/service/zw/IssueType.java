package com.fc.v2.service.zw;

/**
 * 异常台账里四类异常的代号与文面（台账、页面、导出只认这一处）。
 *
 * @author fuce
 * @date 2026-10-02
 */
public final class IssueType {

    /** 读数越过合理量域（负数/超大怪值）：裁决出口 INVALID，退回核实 */
    public static final String INVALID = "INVALID";
    /** 检测当天该样本类别无可用标准：裁决出口 NO_RULE，不硬给结论 */
    public static final String NO_RULE = "NO_RULE";
    /** 库里按检测当天选中的规则代号与原始行申报的代号对不上 */
    public static final String RULE_MISMATCH = "RULE_MISMATCH";
    /** 原始行自报「是否有效=否」：不进正式账，先挂异常让人核实 */
    public static final String INVALID_FLAG = "INVALID_FLAG";

    private IssueType() {
    }

    public static String text(String code) {
        if (INVALID.equals(code)) {
            return "读数越界";
        }
        if (NO_RULE.equals(code)) {
            return "无可用标准";
        }
        if (RULE_MISMATCH.equals(code)) {
            return "规则代号对不上";
        }
        if (INVALID_FLAG.equals(code)) {
            return "申报无效";
        }
        return code == null ? "" : code;
    }
}
