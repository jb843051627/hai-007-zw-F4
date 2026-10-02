package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 水质异常核实台账对象 t_zw_issue
 *
 * <p>一条异常对应导入核对中没能直接落账的一个原始行：异常生成、确认、核实、归档全程留在此表。
 * 核实只准一次：{@code verifyResult/verifyReason/verifyBy/verifyTime} 由核实动作当场钉死，
 * 之后整行只读（{@code archived=1}），重复导入同批同次也不会把已核实条目翻出来重核。
 *
 * @author fuce
 * @date 2026-10-02
 */
@TableName("t_zw_issue")
@ApiModel(value = "TZwIssue", description = "水质异常核实台账")
public class TZwIssue implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 异常事项号（批次号-行次，一次生成） */
    @TableField("issue_no")
    @ApiModelProperty(value = "异常事项号")
    private String issueNo;

    /** 来源上传批次号 */
    @TableField("batch_no")
    @ApiModelProperty(value = "来源上传批次号")
    private String batchNo;

    /** 原表行次 */
    @TableField("row_no")
    @ApiModelProperty(value = "原表行次")
    private Integer rowNo;

    /** 采样点位代号（只认代号不认名字） */
    @TableField("site_no")
    @ApiModelProperty(value = "采样点位代号")
    private String siteNo;

    /** 被校验出的数据项代号 */
    @TableField("item_code")
    @ApiModelProperty(value = "被校验出的数据项代号")
    private String itemCode;

    /** 本行检测读数（照原始行抄入） */
    @TableField("qty")
    @ApiModelProperty(value = "本行检测读数")
    private BigDecimal qty;

    /** 样本类别(水源水/出厂水/管网末梢) */
    @TableField("sample_type")
    @ApiModelProperty(value = "样本类别")
    private String sampleType;

    /** 检测时间（唯一解析口径，页面与接口同一把尺） */
    @TableField("test_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "检测时间")
    private Date testAt;

    /** 申报是否有效 0否 1是 */
    @TableField("valid_flag")
    @ApiModelProperty(value = "申报是否有效 0否 1是")
    private Integer validFlag;

    /** 申报是否已复核 0否 1是 */
    @TableField("review_flag")
    @ApiModelProperty(value = "申报是否已复核 0否 1是")
    private Integer reviewFlag;

    /** 申报规则代号（原始行里带的那条） */
    @TableField("rule_code_decl")
    @ApiModelProperty(value = "申报规则代号")
    private String ruleCodeDecl;

    /** 裁决出口按检测当天选中的规则代号 */
    @TableField("rule_code_hit")
    @ApiModelProperty(value = "裁决出口选中的规则代号")
    private String ruleCodeHit;

    /** 裁决出口给的等级；无可用标准/读数越界为空 */
    @TableField("grade_level")
    @ApiModelProperty(value = "裁决等级")
    private Integer gradeLevel;

    /** 原始行原样留档（归档后锁住的就是这一条） */
    @TableField("raw_line")
    @ApiModelProperty(value = "原始行原样留档")
    private String rawLine;

    /** 校验结论：本行能不能过校验、为什么，逐行写明 */
    @TableField("check_result")
    @ApiModelProperty(value = "校验结论")
    private String checkResult;

    /** 异常类 INVALID读数越界/NO_RULE无可用标准/RULE_MISMATCH规则代号对不上 */
    @TableField("issue_type")
    @ApiModelProperty(value = "异常类")
    private String issueType;

    /** 确认情形 0待确认 1已确认 */
    @TableField("confirm_status")
    @ApiModelProperty(value = "确认情形 0待确认 1已确认")
    private Integer confirmStatus;

    /** 确认人 */
    @TableField("confirm_by")
    @ApiModelProperty(value = "确认人")
    private String confirmBy;

    /** 确认时刻 */
    @TableField("confirm_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "确认时刻")
    private Date confirmTime;

    /** 核实结论 1核实成 2核实不成；空=尚未核实 */
    @TableField("verify_result")
    @ApiModelProperty(value = "核实结论 1核实成 2核实不成")
    private Integer verifyResult;

    /** 核实不成的原因（核实不成必填，核实成留补充说明） */
    @TableField("verify_reason")
    @ApiModelProperty(value = "核实原因/说明")
    private String verifyReason;

    /** 核实人（以这一次写下的为准） */
    @TableField("verify_by")
    @ApiModelProperty(value = "核实人")
    private String verifyBy;

    /** 核实时刻（只写一次，系统日志可翻出哪一次写的） */
    @TableField("verify_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "核实时刻")
    private Date verifyTime;

    /** 归档标识 0未归档 1已归档（归档即只读，原样锁住不能再改） */
    @TableField("archived")
    @ApiModelProperty(value = "归档标识 0未归档 1已归档")
    private Integer archived;

    /** 台账情形 0在办 1已封档 */
    @TableField("status")
    @ApiModelProperty(value = "台账情形 0在办 1已封档")
    private Integer status;

    /** 删除标记 0正常 1删除（归档行永不置删） */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIssueNo() {
        return issueNo;
    }

    public void setIssueNo(String issueNo) {
        this.issueNo = issueNo;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public String getSampleType() {
        return sampleType;
    }

    public void setSampleType(String sampleType) {
        this.sampleType = sampleType;
    }

    public Date getTestAt() {
        return testAt;
    }

    public void setTestAt(Date testAt) {
        this.testAt = testAt;
    }

    public Integer getValidFlag() {
        return validFlag;
    }

    public void setValidFlag(Integer validFlag) {
        this.validFlag = validFlag;
    }

    public Integer getReviewFlag() {
        return reviewFlag;
    }

    public void setReviewFlag(Integer reviewFlag) {
        this.reviewFlag = reviewFlag;
    }

    public String getRuleCodeDecl() {
        return ruleCodeDecl;
    }

    public void setRuleCodeDecl(String ruleCodeDecl) {
        this.ruleCodeDecl = ruleCodeDecl;
    }

    public String getRuleCodeHit() {
        return ruleCodeHit;
    }

    public void setRuleCodeHit(String ruleCodeHit) {
        this.ruleCodeHit = ruleCodeHit;
    }

    public Integer getGradeLevel() {
        return gradeLevel;
    }

    public void setGradeLevel(Integer gradeLevel) {
        this.gradeLevel = gradeLevel;
    }

    public String getRawLine() {
        return rawLine;
    }

    public void setRawLine(String rawLine) {
        this.rawLine = rawLine;
    }

    public String getCheckResult() {
        return checkResult;
    }

    public void setCheckResult(String checkResult) {
        this.checkResult = checkResult;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public Integer getConfirmStatus() {
        return confirmStatus;
    }

    public void setConfirmStatus(Integer confirmStatus) {
        this.confirmStatus = confirmStatus;
    }

    public String getConfirmBy() {
        return confirmBy;
    }

    public void setConfirmBy(String confirmBy) {
        this.confirmBy = confirmBy;
    }

    public Date getConfirmTime() {
        return confirmTime;
    }

    public void setConfirmTime(Date confirmTime) {
        this.confirmTime = confirmTime;
    }

    public Integer getVerifyResult() {
        return verifyResult;
    }

    public void setVerifyResult(Integer verifyResult) {
        this.verifyResult = verifyResult;
    }

    public String getVerifyReason() {
        return verifyReason;
    }

    public void setVerifyReason(String verifyReason) {
        this.verifyReason = verifyReason;
    }

    public String getVerifyBy() {
        return verifyBy;
    }

    public void setVerifyBy(String verifyBy) {
        this.verifyBy = verifyBy;
    }

    public Date getVerifyTime() {
        return verifyTime;
    }

    public void setVerifyTime(Date verifyTime) {
        this.verifyTime = verifyTime;
    }

    public Integer getArchived() {
        return archived;
    }

    public void setArchived(Integer archived) {
        this.archived = archived;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
