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
 * 检测数据导入核对行对象 t_zw_imp_row
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_zw_imp_row")
@ApiModel(value = "TZwImpRow", description = "检测数据导入核对行")
public class TZwImpRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 检测数据上传批次号 */
    @TableField("batch_no")
    @ApiModelProperty(value = "检测数据上传批次号")
    private String batchNo;

    /** 原表行次 */
    @TableField("row_no")
    @ApiModelProperty(value = "原表行次")
    private Integer rowNo;

    /** 采样点位代号（随样本入账，水质核实只认代号） */
    @TableField("site_no")
    @ApiModelProperty(value = "采样点位代号")
    private String siteNo;

    /** 被校验出的数据项代号 */
    @TableField("item_code")
    @ApiModelProperty(value = "被校验出的数据项代号")
    private String itemCode;

    /** 本行检测读数 */
    @TableField("qty")
    @ApiModelProperty(value = "本行检测读数")
    private BigDecimal qty;

    /** 样本类别(水源水/出厂水/管网末梢) */
    @TableField("sample_type")
    @ApiModelProperty(value = "样本类别(水源水/出厂水/管网末梢)")
    private String sampleType;

    /** 检测发生那天——用哪条线只看这一天，不看入库的今天 */
    @TableField("test_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "检测发生那天")
    private Date testAt;

    /** 评价等级 1合格 2关注 3超标 4严重超标；空=未定级（无可用标准） */
    @TableField("grade_level")
    @ApiModelProperty(value = "评价等级 1合格 2关注 3超标 4严重超标")
    private Integer gradeLevel;

    /** 定级所依标准线代号（服务层回值钉死，写过不再改） */
    @TableField("rule_code")
    @ApiModelProperty(value = "定级所依标准线代号")
    private String ruleCode;

    /** 所依标准的生效那天（随定级一并钉死，历史复核只认这个编号上下文） */
    @TableField("rule_eff_date")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "所依标准的生效那天")
    private Date ruleEffDate;

    /** 行落地情形 0待核 1已入库 2退回 */
    @TableField("status")
    @ApiModelProperty(value = "行落地情形 0待核 1已入库 2退回")
    private Integer status;

    /** 删除标记 0正常 1删除 */
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

    public Integer getGradeLevel() {
        return gradeLevel;
    }

    public void setGradeLevel(Integer gradeLevel) {
        this.gradeLevel = gradeLevel;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public Date getRuleEffDate() {
        return ruleEffDate;
    }

    public void setRuleEffDate(Date ruleEffDate) {
        this.ruleEffDate = ruleEffDate;
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
