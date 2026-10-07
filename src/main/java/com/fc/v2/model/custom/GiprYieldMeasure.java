package com.fc.v2.model.custom;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 一条待落线的实测：哪一类、所问哪一年度、实测值与其带来的单位口径、实测那一刻。
 * 逐条回算与贴批留痕都按这一件递。
 *
 * @author fuce
 * @date 2026-10-07
 */
public class GiprYieldMeasure implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 哪一类（种植类/水产类/初加工类），只许落本类的线，邻类不借 */
    private String objKind;

    /** 所问年度（回头复算那一年的）；实测那一刻须落在这一年里，隔年度的不借 */
    private Integer askYear;

    /** 实测值 */
    private BigDecimal input;

    /** 实测值带来的单位口径（每亩公斤／每公顷吨 两式之一）；空表示已按线面口径给齐 */
    private String inputUnit;

    /** 实测那一刻 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date measureAt;

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

    public BigDecimal getInput() {
        return input;
    }

    public void setInput(BigDecimal input) {
        this.input = input;
    }

    public String getInputUnit() {
        return inputUnit;
    }

    public void setInputUnit(String inputUnit) {
        this.inputUnit = inputUnit;
    }

    public Date getMeasureAt() {
        return measureAt;
    }

    public void setMeasureAt(Date measureAt) {
        this.measureAt = measureAt;
    }
}
