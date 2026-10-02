package com.fc.v2.service.zw;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Excel 里一行化验读数解析后的结构。字段严格对应原始七列：
 * 采样批次、读数明细、规则代号、点位编号、检测时间、是否有效、是否已复核。
 *
 * <p>读数明细一列内部三段：项目代号 / 样本类别 / 读数，用「|」或全角「｜」分隔。
 * 全部字段只是解析产物，<b>不含任何校验结论</b>——能不能过校验由导入服务层判。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class ImpLine {

    /** 原表行次（数据行从 1 起数，不含表头），异常与报表都按这个行次回指原始行 */
    private final int rowNo;
    private final String batchNo;
    private final String siteNo;
    private final String itemCode;
    private final String sampleType;
    private final BigDecimal qty;
    private final Date testAt;
    /** 申报是否有效 0否 1是 */
    private final int validFlag;
    /** 申报是否已复核 0否 1是 */
    private final int reviewFlag;
    /** 原始行里带的规则代号（可能为空；与裁决线对不上由服务层挑出） */
    private final String ruleCodeDecl;
    /** 原始行原样（七列按读出文本拼接，归档锁住的就是它） */
    private final String rawLine;

    public ImpLine(int rowNo, String batchNo, String siteNo, String itemCode, String sampleType,
                   BigDecimal qty, Date testAt, int validFlag, int reviewFlag,
                   String ruleCodeDecl, String rawLine) {
        this.rowNo = rowNo;
        this.batchNo = batchNo;
        this.siteNo = siteNo;
        this.itemCode = itemCode;
        this.sampleType = sampleType;
        this.qty = qty;
        this.testAt = testAt;
        this.validFlag = validFlag;
        this.reviewFlag = reviewFlag;
        this.ruleCodeDecl = ruleCodeDecl;
        this.rawLine = rawLine;
    }

    public int getRowNo() {
        return rowNo;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public String getItemCode() {
        return itemCode;
    }

    public String getSampleType() {
        return sampleType;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public Date getTestAt() {
        return testAt;
    }

    public int getValidFlag() {
        return validFlag;
    }

    public int getReviewFlag() {
        return reviewFlag;
    }

    public String getRuleCodeDecl() {
        return ruleCodeDecl;
    }

    public String getRawLine() {
        return rawLine;
    }
}
