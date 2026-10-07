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
 * 专用标志使用核准办件流转单对象 t_gipr_case_flow
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_gipr_case_flow")
@ApiModel(value = "TGiprCaseFlow", description = "专用标志使用核准办件流转单")
public class TGiprCaseFlow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 专用标志使用核准办件的件次 */
    @TableField("biz_no")
    @ApiModelProperty(value = "专用标志使用核准办件的件次")
    private String bizNo;

    /** 起件四样之一：哪一家企业（授权册上那一个代号） */
    @TableField("holder_no")
    @ApiModelProperty(value = "哪一家企业（授权册代号）")
    private String holderNo;

    /** 起件四样之二：挂哪一个名录产品 */
    @TableField("prod_id")
    @ApiModelProperty(value = "挂哪一个名录产品")
    private Integer prodId;

    /** 起件四样之三：拟贴在哪一类货上 */
    @TableField("goods_kind")
    @ApiModelProperty(value = "拟贴在哪一类货上")
    private String goodsKind;

    /** 起件四样之四：递件那一刻 */
    @TableField("apply_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "递件那一刻")
    private Date applyTime;

    /** 同一件并着进两回的幂等键（重取不重建） */
    @TableField("idem_key")
    @ApiModelProperty(value = "幂等键")
    private String idemKey;

    /** 当前关次 0..3（申请受理/材料初审/标志核准/备案发标）——以后端数出来的为准 */
    @TableField("stage")
    @ApiModelProperty(value = "当前关次 0..3（申请受理/材料初审/标志核准/备案发标）")
    private Integer stage;

    /** 报面自带序号（只供人眼对着看，库里存取一律按 stage 数出来的） */
    @TableField("view_seq")
    @ApiModelProperty(value = "报面自带序号，仅供人眼对照")
    private Integer viewSeq;

    /** 进关累计趟次：退回再进按新起一次重新累计 */
    @TableField("attempt_no")
    @ApiModelProperty(value = "进关累计趟次")
    private Integer attemptNo;

    /** 标志核发记号 0未写回 1已写回（末关两名核签齐了写回本行） */
    @TableField("cert_flag")
    @ApiModelProperty(value = "标志核发记号 0未写回 1已写回")
    private Integer certFlag;

    /** 了结类别 0未完 1走满末格封口 2递件方撤回 3名录产品整段并走 */
    @TableField("end_kind")
    @ApiModelProperty(value = "了结类别 0未完 1封口 2撤回 3并走")
    private Integer endKind;

    /** 在办占位：未闭上=企业代号（同企业在办至多一条的硬约束），闭上/了结/抽走=NULL */
    @TableField("open_holder")
    @ApiModelProperty(value = "在办占位，闭上/了结置空")
    private String openHolder;

    /** 单子走到哪一关 0未起 1在办 2已封住 */
    @TableField("status")
    @ApiModelProperty(value = "单子走到哪一关 0未起 1在办 2已封住")
    private Integer status;

    /** 一关一记 */
    @TableField("content")
    @ApiModelProperty(value = "一关一记")
    private String content;

    /** 最近一次挪关动作 */
    @TableField("last_action")
    @ApiModelProperty(value = "最近一次挪关动作")
    private String lastAction;

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

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public String getHolderNo() {
        return holderNo;
    }

    public void setHolderNo(String holderNo) {
        this.holderNo = holderNo;
    }

    public Integer getProdId() {
        return prodId;
    }

    public void setProdId(Integer prodId) {
        this.prodId = prodId;
    }

    public String getGoodsKind() {
        return goodsKind;
    }

    public void setGoodsKind(String goodsKind) {
        this.goodsKind = goodsKind;
    }

    public Date getApplyTime() {
        return applyTime;
    }

    public void setApplyTime(Date applyTime) {
        this.applyTime = applyTime;
    }

    public String getIdemKey() {
        return idemKey;
    }

    public void setIdemKey(String idemKey) {
        this.idemKey = idemKey;
    }

    public Integer getViewSeq() {
        return viewSeq;
    }

    public void setViewSeq(Integer viewSeq) {
        this.viewSeq = viewSeq;
    }

    public Integer getAttemptNo() {
        return attemptNo;
    }

    public void setAttemptNo(Integer attemptNo) {
        this.attemptNo = attemptNo;
    }

    public Integer getCertFlag() {
        return certFlag;
    }

    public void setCertFlag(Integer certFlag) {
        this.certFlag = certFlag;
    }

    public Integer getEndKind() {
        return endKind;
    }

    public void setEndKind(Integer endKind) {
        this.endKind = endKind;
    }

    public String getOpenHolder() {
        return openHolder;
    }

    public void setOpenHolder(String openHolder) {
        this.openHolder = openHolder;
    }

    public Integer getStage() {
        return stage;
    }

    public void setStage(Integer stage) {
        this.stage = stage;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLastAction() {
        return lastAction;
    }

    public void setLastAction(String lastAction) {
        this.lastAction = lastAction;
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
