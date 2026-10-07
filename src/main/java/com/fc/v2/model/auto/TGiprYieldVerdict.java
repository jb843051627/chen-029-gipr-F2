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
 * 核定产量贴线落档留痕对象 t_gipr_yield_verdict
 *
 * 分档与留痕联动：落库时钉进那行的线代号与启用那一一日，
 * 须与求值那一处当场返回的字字相同；只追加、不追溯重算。
 *
 * @author fuce
 * @date 2026-10-07
 */
@TableName("t_gipr_yield_verdict")
@ApiModel(value = "TGiprYieldVerdict", description = "核定产量贴线落档留痕")
public class TGiprYieldVerdict implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 同一批的幂等键（并着贴两回重取不重建） */
    @TableField("batch_no")
    @ApiModelProperty(value = "同一批的幂等键（并着贴两回重取不重建）")
    private String batchNo;

    /** 批内行次（从1起） */
    @TableField("row_no")
    @ApiModelProperty(value = "批内行次（从1起）")
    private Integer rowNo;

    /** 所问品类（种植类/水产类/初加工类） */
    @TableField("obj_kind")
    @ApiModelProperty(value = "所问品类（种植类/水产类/初加工类）")
    private String objKind;

    /** 所问年度（回头复算那一年的） */
    @TableField("ask_year")
    @ApiModelProperty(value = "所问年度（回头复算那一年的）")
    private Integer askYear;

    /** 实测那一刻 */
    @TableField("measure_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "实测那一刻")
    private Date measureAt;

    /** 实测值原样（未换齐；大得没边到放不下的留空，数在 message 里指名） */
    @TableField("input_raw")
    @ApiModelProperty(value = "实测值原样（未换齐）")
    private BigDecimal inputRaw;

    /** 实测值带来的单位口径 */
    @TableField("input_unit")
    @ApiModelProperty(value = "实测值带来的单位口径")
    private String inputUnit;

    /** 换齐到线面口径后的数（同口径即原样） */
    @TableField("input_conv")
    @ApiModelProperty(value = "换齐到线面口径后的数（同口径即原样）")
    private BigDecimal inputConv;

    /** 钉进这行的线代号（与求值当场返回字字相同） */
    @TableField("rule_code")
    @ApiModelProperty(value = "钉进这行的线代号（与求值当场返回字字相同）")
    private String ruleCode;

    /** 钉进这行的启用那一一日（与求值当场返回字字相同） */
    @TableField("eff_start")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "钉进这行的启用那一一日（与求值当场返回字字相同）")
    private Date effStart;

    /** 落进哪一段 1..4（挑不出/数不合为0） */
    @TableField("level")
    @ApiModelProperty(value = "落进哪一段 1..4（挑不出/数不合为0）")
    private Integer level;

    /** 该不该起第二张 0不起 1起（超封顶那一段才起） */
    @TableField("need_second")
    @ApiModelProperty(value = "该不该起第二张 0不起 1起（超封顶那一段才起）")
    private Integer needSecond;

    /** 这一刻采信得上吗 0挑不出 1采信 */
    @TableField("applicable")
    @ApiModelProperty(value = "这一刻采信得上吗 0挑不出 1采信")
    private Integer applicable;

    /** 那句明白话（挑不出/数不合时指名哪个数、按哪条规矩不合） */
    @TableField("message")
    @ApiModelProperty(value = "那句明白话（挑不出/数不合时指名哪个数、按哪条规矩不合）")
    private String message;

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

    public String getObjKind() {
        return objKind;
    }

    public void setObjKind(String objKind) {
        this.objKind = objKind;
    }

    public Integer getAskYear() {
        return askYear;
    }

    public void setAskYear(Integer askYear) {
        this.askYear = askYear;
    }

    public Date getMeasureAt() {
        return measureAt;
    }

    public void setMeasureAt(Date measureAt) {
        this.measureAt = measureAt;
    }

    public BigDecimal getInputRaw() {
        return inputRaw;
    }

    public void setInputRaw(BigDecimal inputRaw) {
        this.inputRaw = inputRaw;
    }

    public String getInputUnit() {
        return inputUnit;
    }

    public void setInputUnit(String inputUnit) {
        this.inputUnit = inputUnit;
    }

    public BigDecimal getInputConv() {
        return inputConv;
    }

    public void setInputConv(BigDecimal inputConv) {
        this.inputConv = inputConv;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public Date getEffStart() {
        return effStart;
    }

    public void setEffStart(Date effStart) {
        this.effStart = effStart;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Integer getNeedSecond() {
        return needSecond;
    }

    public void setNeedSecond(Integer needSecond) {
        this.needSecond = needSecond;
    }

    public Integer getApplicable() {
        return applicable;
    }

    public void setApplicable(Integer applicable) {
        this.applicable = applicable;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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
