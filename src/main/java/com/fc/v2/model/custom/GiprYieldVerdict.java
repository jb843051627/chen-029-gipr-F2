package com.fc.v2.model.custom;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 一次落线的判定全貌：这一刻采信哪一条线、落进哪一段、该不该起第二张，
 * 全在这一件上；看板、大屏、逐条回算、落库留痕拿的都是同一件，不许各自再数一遍。
 *
 * applicable=false 即「挑不出」——本身是合法回话，此时 level=0，
 * ruleCode/effStart 为空，message 写明为什么给不出；已撤下的不顶、邻类的不借。
 *
 * @author fuce
 * @date 2026-10-07
 */
public class GiprYieldVerdict implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 这一刻采信得上吗：true 采信 / false 挑不出（合法回话） */
    private boolean applicable;

    /** 采信的线代号（落库钉的就是它，与当场返回字字相同）；挑不出为 null */
    private String ruleCode;

    /** 采信的线名；挑不出为 null */
    private String ruleName;

    /** 采信那一版的主键；挑不出为 null */
    private Long ruleId;

    /** 采信那一版启用那一一日（落库钉的就是它）；挑不出为 null */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date effStart;

    /** 采信那一版交班那一一日（不含）；挑不出为 null */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date effEnd;

    /** 这条线管哪一类（种植类/水产类/初加工类） */
    private String objKind;

    /** 线面带的单位口径（每亩公斤／每公顷吨 两式之一） */
    private String unitKind;

    /** 实测值原样（未换齐） */
    private BigDecimal inputRaw;

    /** 实测值带来的单位口径；空表示按线面口径给齐 */
    private String inputUnit;

    /** 换齐到线面口径后的数（同口径即原样；小数取齐位由配置一处定死） */
    private BigDecimal inputConv;

    /** 落进哪一段 1..4；挑不出／数不合为 0 */
    private int level;

    /** 该不该起第二张：落在第4段（超封顶）才起，只此一处定 */
    private boolean needSecond;

    /** 那句明白话：挑不出／数不合时点名是哪个数、按哪条规矩不合 */
    private String message;

    public boolean isApplicable() {
        return applicable;
    }

    public void setApplicable(boolean applicable) {
        this.applicable = applicable;
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

    public Long getRuleId() {
        return ruleId;
    }

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
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

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public boolean isNeedSecond() {
        return needSecond;
    }

    public void setNeedSecond(boolean needSecond) {
        this.needSecond = needSecond;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
