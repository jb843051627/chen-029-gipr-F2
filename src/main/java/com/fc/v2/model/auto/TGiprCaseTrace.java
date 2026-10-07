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
import java.util.Date;

/**
 * 办件按语追溯串对象 t_gipr_case_trace
 * 一句按语落下去，紧跟着记下它落在哪一刻，按先后串成一串挂在件身后。
 * 只追加、不改写；回放倒着演，只出结果、不回写。
 *
 * @author fuce
 * @date 2026-10-06
 */
@TableName("t_gipr_case_trace")
@ApiModel(value = "TGiprCaseTrace", description = "办件按语追溯串")
public class TGiprCaseTrace implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 挂在哪一条件身后 */
    @TableField("case_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "办件主键")
    private Long caseId;

    /** 这句话落在哪一格 0..3 */
    @TableField("stage")
    @ApiModelProperty(value = "落字关次 0..3")
    private Integer stage;

    /** 落字时本件的进关趟次（同格退回再进后旧句仍在、新句另计一趟） */
    @TableField("attempt_no")
    @ApiModelProperty(value = "进关趟次")
    private Integer attemptNo;

    /** 动作类别 START起件/NOTE按语/ADVANCE推进/ROLLBACK退回/SIGN核签/ISSUE核发/CLOSE封口/CANCEL撤回/MERGE并走 */
    @TableField("action_kind")
    @ApiModelProperty(value = "动作类别")
    private String actionKind;

    /** 那句按语原样（退回时原样挪入末尾） */
    @TableField("content")
    @ApiModelProperty(value = "那句按语原样")
    private String content;

    /** 这句话落下的那一刻 */
    @TableField("trace_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "落字时刻")
    private Date traceTime;

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

    public Long getCaseId() {
        return caseId;
    }

    public void setCaseId(Long caseId) {
        this.caseId = caseId;
    }

    public Integer getStage() {
        return stage;
    }

    public void setStage(Integer stage) {
        this.stage = stage;
    }

    public Integer getAttemptNo() {
        return attemptNo;
    }

    public void setAttemptNo(Integer attemptNo) {
        this.attemptNo = attemptNo;
    }

    public String getActionKind() {
        return actionKind;
    }

    public void setActionKind(String actionKind) {
        this.actionKind = actionKind;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Date getTraceTime() {
        return traceTime;
    }

    public void setTraceTime(Date traceTime) {
        this.traceTime = traceTime;
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
