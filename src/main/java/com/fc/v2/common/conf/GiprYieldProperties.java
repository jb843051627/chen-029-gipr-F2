package com.fc.v2.common.conf;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 贴线定数（gipr.yield 前缀）：全系统只此一处，别处不得自立。
 *
 * 一公顷合几亩、一吨合几公斤、换算后小数取齐到哪一位、实测值大得没边的界线，
 * 都在这一格里；字段自带与 application.yml 一致的默认值，测试不启容器也能直 new。
 *
 * @author fuce
 * @date 2026-10-07
 */
@Component
@ConfigurationProperties(prefix = "gipr.yield")
public class GiprYieldProperties {

    /** 一公顷合十五亩（定数，不留各处自定的余地） */
    private BigDecimal muPerHectare = new BigDecimal("15");

    /** 一吨合一千公斤（定数） */
    private BigDecimal kgPerTon = new BigDecimal("1000");

    /** 单位两式换齐后小数取齐到这一位（四舍五入），全系统只此一处定 */
    private int scale = 3;

    /** 实测值大得没边的界线（达到即不合）：负数或越此线的不进落线 */
    private BigDecimal inputMax = new BigDecimal("1000000");

    public BigDecimal getMuPerHectare() {
        return muPerHectare;
    }

    public void setMuPerHectare(BigDecimal muPerHectare) {
        this.muPerHectare = muPerHectare;
    }

    public BigDecimal getKgPerTon() {
        return kgPerTon;
    }

    public void setKgPerTon(BigDecimal kgPerTon) {
        this.kgPerTon = kgPerTon;
    }

    public int getScale() {
        return scale;
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    public BigDecimal getInputMax() {
        return inputMax;
    }

    public void setInputMax(BigDecimal inputMax) {
        this.inputMax = inputMax;
    }
}
