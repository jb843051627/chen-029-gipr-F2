package com.fc.v2.common.conf;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 核定产量与特色指标贴线求值的一处定数。
 *
 * 全系统只准这里给答案：
 * 一公顷合十五亩、一吨合一千公斤这类换算定数，写死在此处并可由配置覆盖，
 * 各处（看板、折线、后端回算）不许自定；换算后小数取齐到第几位也只在此处定。
 *
 * @author fuce
 * @date 2026-10-07
 */
@Component
@ConfigurationProperties(prefix = "gipr.yield")
public class GiprYieldProperties {

    // ---- 品类口径（与名录底册「甲层种植类·乙层水产类·丙层初加工类」同一套 canonical） ----
    /** 种植类 */
    public static final String KIND_PLANT = "种植类";
    /** 水产类 */
    public static final String KIND_AQUA = "水产类";
    /** 初加工类 */
    public static final String KIND_PROC = "初加工类";

    // ---- 计量单位两式（线面 unit_kind 只认这两式的 canonical 串） ----
    /** 每亩公斤 */
    public static final String UNIT_KG_PER_MU = "每亩公斤";
    /** 每公顷吨 */
    public static final String UNIT_T_PER_HA = "每公顷吨";

    /** 线的情形：在位 */
    public static final int STATUS_ON = 0;
    /** 线的情形：已撤下 */
    public static final int STATUS_OFF = 1;

    /** 一公顷合十五亩——定数，不留各处自定的余地 */
    private BigDecimal muPerHectare = new BigDecimal("15");

    /** 一吨合一千公斤——定数 */
    private BigDecimal kgPerTon = new BigDecimal("1000");

    /** 换算后小数取齐到第几位（ thresholds 落库为 decimal(12,3)，此处随库口径定 3 位） */
    private int valueScale = 3;

    /** 实测值换算成「每亩公斤」后允许的最大数；过了这个数叫「大得没边」，不进落线 */
    private BigDecimal maxValueKgPerMu = new BigDecimal("999999.999");

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

    public int getValueScale() {
        return valueScale;
    }

    public void setValueScale(int valueScale) {
        this.valueScale = valueScale;
    }

    public BigDecimal getMaxValueKgPerMu() {
        return maxValueKgPerMu;
    }

    public void setMaxValueKgPerMu(BigDecimal maxValueKgPerMu) {
        this.maxValueKgPerMu = maxValueKgPerMu;
    }
}
