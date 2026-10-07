package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TGiprYieldRule;
import com.fc.v2.model.custom.YieldVerdict;

/**
 * 核定产量与特色指标换算贴线档 Service接口（rule-eval 形状：规则求值，无增删改）
 *
 * 这一串只认一处给出的答案：这一刻采信哪一条线、一条也采信不上时给不给说法、
 * 几条并届听谁的、实测值压在某道上限归哪一段、单位两式在哪儿换齐——
 * 全由本处的同一套算法、同一次算定，屏上与旁处不得各答一问。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITGiprYieldRuleService {

    /** 按主键查询规则 */
    TGiprYieldRule selectTGiprYieldRuleById(Long id);

    /**
     * 单规则求值：按 ruleCode 在 at 时刻生效的版本判档（1..4）。
     * 无生效版本 / 输入越界 / 参数缺失一律返回 0。
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

    // ---------------- 往后的等价去处（不碰上面那套的签名与收发） ----------------

    /**
     * 按线代号问的那一路（唯一决策核心）：给实测值与它发生的那一刻，
     * 先把这一刻在位的版本挑出来，单位照线面口径换齐，再落一次线。
     *
     * 实测值为负数／大得没边／单位口径不认得，抛 FlowRuleException，
     * 消息点名是哪一个数、按哪一条规矩不合，不给像模像样的回话。
     * 这一刻挑不出在位版本（已撤下不顶、邻类不借）时，返回 available=false 的
     * YieldVerdict——「挑不出」本身是合法回话。
     */
    YieldVerdict decide(String ruleCode, BigDecimal input, Date at);

    /**
     * 按品类、按年度取线再落线的等价方法（另起一路承接，不碰按代号那路的签名）。
     * objKind 为空则不限品类（与「通表比优」同口径）；year 为空则不限年度。
     * 同一刻可用版本不止一条时，听序大的；序分不出听代号串排得靠后的——
     * 与按代号那路、通表比优那路必须是同一条被选中。
     */
    YieldVerdict decideByKind(String objKind, Integer year, BigDecimal input, Date at);

    /**
     * 按品类、按年度列版：已撤下、早年到期的版本行面照旧列得出来，
     * 只是不参与求值；按启用那一刻倒序排。
     */
    List<TGiprYieldRule> listVersions(String objKind, Integer year);

    /**
     * 看板对账：看板上挂着线代号、回到 at 这一刻却寻不到在位版本的那些代号，
     * 一条条列出来（撤下未补、窗口断档都算），代号升序去重。
     */
    List<String> listBrokenBoardCodes(Date at);
}
