package com.fc.v2.model.custom;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 贴线一次算定的结果。
 *
 * 看板那一格、大屏按品类分列的折线、折线上点出来的那个数、后端逐条回算——
 * 几处都取这一份算定结果，不许各自再数一遍。
 *
 * 挑不出在位版本时 level=0、available=false，reason 里写明缘由；
 * 「挑不出」本身是合法回话。实测值越界（负数／大得没边）不产出本对象，
 * 由求值处抛 FlowRuleException 点名是哪一个数、按哪一条规矩不合。
 *
 * 落库留痕时钉进那行的「线代号」与「启用那一日」，就取 {@link #ruleCode}
 * 与 {@link #effStart} 这两个当场返回值，字字相同，不许改取邻类或上一交班期。
 *
 * @author fuce
 * @date 2026-10-07
 */
public class YieldVerdict implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 挑不出的缘由：这一刻没有在位版本（已撤下不顶、邻类不借） */
    public static final String REASON_NO_VERSION = "NO_VERSION";
    /** 挑不出的缘由：入参残缺，无从问线 */
    public static final String REASON_MISSING_ARG = "MISSING_ARG";

    /** 这一刻是否采信到一条在位的线 */
    private boolean available;

    /** 落在哪一段 1..4；挑不出时为 0 */
    private int level;

    /** 挑不出时的缘由码（{@link #REASON_NO_VERSION} 等），采信得到时为 null */
    private String reason;

    /** 给人看的回话（挑不出时写明为何给不出） */
    private String message;

    /** 钉痕用：采信线的主键 */
    private Long ruleId;

    /** 钉痕用：线代号（落库即取此值，字字相同） */
    private String ruleCode;

    /** 线名 */
    private String ruleName;

    /** 管哪一类（种植类／水产类／初加工类） */
    private String objKind;

    /** 线面单位口径（每亩公斤／每公顷吨） */
    private String unitKind;

    /** 交班次序 */
    private Integer priority;

    /** 钉痕用：启用那一刻（落库即取此值，字字相同） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date effStart;

    /** 交班那一刻（不含） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date effEnd;

    /** 报入的实测值，系统收口口径「每亩公斤」 */
    private BigDecimal inputKgPerMu;

    /** 照线面口径换算取齐后、真正拿去落线的那个数 */
    private BigDecimal lineValue;

    public static YieldVerdict none(String reason, String message) {
        YieldVerdict v = new YieldVerdict();
        v.available = false;
        v.level = 0;
        v.reason = reason;
        v.message = message;
        return v;
    }

    public static YieldVerdict of(com.fc.v2.model.auto.TGiprYieldRule rule, int level,
                                  BigDecimal inputKgPerMu, BigDecimal lineValue) {
        YieldVerdict v = new YieldVerdict();
        v.available = true;
        v.level = level;
        v.ruleId = rule.getId();
        v.ruleCode = rule.getRuleCode();
        v.ruleName = rule.getRuleName();
        v.objKind = rule.getObjKind();
        v.unitKind = rule.getUnitKind();
        v.priority = rule.getPriority();
        v.effStart = rule.getEffStart();
        v.effEnd = rule.getEffEnd();
        v.inputKgPerMu = inputKgPerMu;
        v.lineValue = lineValue;
        v.message = "线「" + rule.getRuleCode() + "」落在第 " + level + " 段";
        return v;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getObjKind() {
        return objKind;
    }

    public void setObjKind(String objKind) {
        this.objKind = objKind;
    }

    public String getUnitKind() {
        return unitKind;
    }

    public void setUnitKind(String unitKind) {
        this.unitKind = unitKind;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Date getEffStart() {
        return effStart;
    }

    public void setEffStart(Date effStart) {
        this.effStart = effStart;
    }

    public Date getEffEnd() {
        return effEnd;
    }

    public void setEffEnd(Date effEnd) {
        this.effEnd = effEnd;
    }

    public BigDecimal getInputKgPerMu() {
        return inputKgPerMu;
    }

    public void setInputKgPerMu(BigDecimal inputKgPerMu) {
        this.inputKgPerMu = inputKgPerMu;
    }

    public BigDecimal getLineValue() {
        return lineValue;
    }

    public void setLineValue(BigDecimal lineValue) {
        this.lineValue = lineValue;
    }
}
