package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TGiprYieldRule;
import com.fc.v2.model.custom.GiprYieldMeasure;
import com.fc.v2.model.custom.GiprYieldVerdict;

/**
 * 核定产量与特色指标换算贴线档 Service接口（rule-eval 形状：规则求值，无增删改）
 *
 * 这一串只认此处给出的答案：这一刻采信哪一条线、一条也采信不上时给不给说法、
 * 几条并届时听谁的、实测值恰好压在某一道上限归进哪一段、该不该起第二张、
 * 单位两式在哪儿换齐。屏上与旁处不得各答其中一问。
 *
 * 老五个方法（按代号取值那一路）收什么回什么一概不动；按品类、按年度取线走
 * 后添的等价方法，不碰老路子的收发规制。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITGiprYieldRuleService {

    /** 单位口径：每亩公斤（全系统只此一份代号，另认汉文写法「每亩公斤」） */
    String UNIT_KG_PER_MU = "KG_PER_MU";

    /** 单位口径：每公顷吨（另认汉文写法「每公顷吨」） */
    String UNIT_TON_PER_HECTARE = "TON_PER_HECTARE";

    /** 按主键查询规则 */
    TGiprYieldRule selectTGiprYieldRuleById(Long id);

    /**
     * 单规则求值：按 ruleCode 在 at 时刻生效的版本判档（1..4）。
     * 无生效版本 / 输入越界 / 参数缺失一律返回 0。
     * 实测值默认已按线面单位口径给齐（老路子不带口径，不另做换算）。
     */
    int evaluate(String ruleCode, BigDecimal input, Date at);

    /** 多规则求值：at 时刻可用规则中优先级最高者的档位；无可用规则返回 0 */
    int evaluateTop(BigDecimal input, Date at);

    /** at 时刻可用规则（优先级降序、ruleCode 降序） */
    List<TGiprYieldRule> listAvailable(Date at);

    /** 单条规则在 at 时刻是否可用（批量导入/报表等旁路复用，口径须与定位/列表一致） */
    boolean usable(Long id, Date at);

    /** at 时刻可用规则条数（列表页角标 / 看板） */
    int countAvailable(Date at);

    // ------------------------------------------------------------------
    // 后添的等价路子：与按代号取值同一次挑线、同一条界规、同一处换算，
    // 只是回话带全乎（判定全貌），并按品类、按年度收口。
    // ------------------------------------------------------------------

    /**
     * 按代号取线落档，回判定全貌：与 {@link #evaluate} 同一次算定。
     * 实测值不合（负数／大得没边）抛 FlowRuleException，指名哪个数、按哪条规矩不合；
     * 挑不出在位版本不算错，回 applicable=false 的判定（「挑不出」是合法回话）。
     * inputUnit 为空表示实测值已按线面口径给齐；带上且与线面两式不一，由系统换齐再落。
     */
    GiprYieldVerdict verdictByCode(String ruleCode, BigDecimal input, String inputUnit, Date at);

    /**
     * 通表比优，回判定全貌：与 {@link #evaluateTop} 同一次算定。
     * 从按线代号问那一路、从通表比优问那一路，答出来的必须是同一个。
     */
    GiprYieldVerdict verdictTop(BigDecimal input, String inputUnit, Date at);

    /**
     * 按品类、按年度取线落档（等价方法，承接往后按品类/按年度取线的用处）：
     * 只许落本类的线，邻类不借；实测那一刻须落在所问年度里，隔年度的不借，
     * 对不上抛 FlowRuleException 指名。挑不出在位版本回 applicable=false。
     */
    GiprYieldVerdict evaluateByKind(String objKind, Integer askYear, BigDecimal input, String inputUnit, Date at);

    /**
     * 后端逐条回算：一批实测逐条过同一处算定，回判定全貌列表（不留痕）。
     * 单条实测值不合的，该条回 applicable=false、message 指名，不拖垮整批。
     */
    List<GiprYieldVerdict> recalcBatch(List<GiprYieldMeasure> items);

    /**
     * 看板对账：挂在板上的线代号里，at 这一刻挑不出在位版本的，逐条列出。
     * 「挑不出」是合法回话，列出来让人对着册子核，不硬挑一个顶上。
     */
    List<String> listUnmatchedCodes(Collection<String> ruleCodes, Date at);

    /**
     * 行面列表（只读）：按品类/代号列全部版本，含已到期、已撤下的——
     * 早年到期的那些版本只是不再参与求值，行面照旧列得出来。
     */
    List<TGiprYieldRule> listVersions(String objKind, String ruleCode);
}
