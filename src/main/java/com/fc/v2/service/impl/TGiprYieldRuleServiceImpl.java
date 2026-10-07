package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.conf.GiprYieldProperties;
import com.fc.v2.common.exception.FlowRuleException;
import com.fc.v2.mapper.auto.TGiprYieldRuleMapper;
import com.fc.v2.model.auto.TGiprYieldRule;
import com.fc.v2.model.custom.YieldVerdict;
import com.fc.v2.service.ITGiprYieldRuleService;
import com.fc.v2.util.StringUtils;

/**
 * 核定产量与特色指标换算贴线档 Service业务层处理（rule-eval 形状）。
 *
 * 这一串只认本类这一处给出的答案，屏上与旁处不得各答其中一问：
 * 1. 先挑在用的那一版——del_flag=0 且 status=在位，且 at 落在 [eff_start, eff_end)；
 *    起始与交回相接那一瞬归后一版；已撤下不顶、邻类不借；「挑不出」是合法回话。
 * 2. 几条并立听谁的——交班次序（priority）大的先说话；序分不出，比代号串自身先后，
 *    靠后的报数。按代号问与通表比优两路走同一选择，答出来必须是同一条。
 * 3. 单位先换齐再比——两式计量照线面 unit_kind 换算，定数（一公顷十五亩、一吨千公斤）
 *    与取齐小数位全在 {@link GiprYieldProperties} 一处定死。
 * 4. 数压在界上归哪一头——只在 {@link #levelOf} 一处定死（三道上限含等号归本段），
 *    别处不得另立一套。
 * 5. 负数／大得没边不进落线，抛 FlowRuleException 点名是哪一个数、按哪一条规矩不合。
 * 6. 线一改，先前按老线落下的笔不追溯重算——求值永远只认传入那一刻在位的版本。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TGiprYieldRuleServiceImpl implements ITGiprYieldRuleService {

    /** 交班次序降序；并列按 ruleCode 降序（数值大的先说话，序分不出听代号靠后的） */
    private static final Comparator<TGiprYieldRule> PRIORITY_THEN_CODE_DESC = new Comparator<TGiprYieldRule>() {
        @Override
        public int compare(TGiprYieldRule a, TGiprYieldRule b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            return cb.compareTo(ca);
        }
    };

    @javax.annotation.Resource
    private TGiprYieldRuleMapper giprYieldRuleMapper;

    @javax.annotation.Resource
    private GiprYieldProperties props;

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）绑定：
     * JDBC 的 serverTimezone 与本机时区不对称，直接把 java.util.Date 作参数会整体偏移，
     * 使边界判定在「切换当日/相接那一瞬」这类用例上随机错档。
     */
    private static String ts(Date d) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    // ============================================================
    // 老路子：模样与收发一概不动，内部改走同一决策核心
    // ============================================================

    @Override
    public TGiprYieldRule selectTGiprYieldRuleById(Long id) {
        return this.giprYieldRuleMapper.selectById(id);
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        // 老形状：无生效版本 / 输入越界 / 参数缺失一律 0；选版与分档口径与 decide 同源
        try {
            YieldVerdict v = decide(ruleCode, input, GiprYieldProperties.UNIT_KG_PER_MU, at);
            return v.isAvailable() ? v.getLevel() : 0;
        } catch (FlowRuleException e) {
            return 0;
        }
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        // 通表比优那一路：与按代号问那一路同一选择、同一次算定
        try {
            YieldVerdict v = decideByKind(null, null, input, GiprYieldProperties.UNIT_KG_PER_MU, at);
            return v.isAvailable() ? v.getLevel() : 0;
        } catch (FlowRuleException e) {
            return 0;
        }
    }

    @Override
    public List<TGiprYieldRule> listAvailable(Date at) {
        if (at == null) {
            return new ArrayList<TGiprYieldRule>();
        }
        List<TGiprYieldRule> list = selectInWindow(null, at);
        list.sort(PRIORITY_THEN_CODE_DESC);
        return list;
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TGiprYieldRule r = this.giprYieldRuleMapper.selectById(id);
        return r != null
                && (r.getDelFlag() == null || r.getDelFlag() == 0)
                && r.getStatus() != null && r.getStatus() == GiprYieldProperties.STATUS_ON
                && inWindow(r, at);
    }

    @Override
    public int countAvailable(Date at) {
        return listAvailable(at).size();
    }

    // ============================================================
    // 往后的等价去处：按代号 / 按品类年度，另起一路承接，签名不碰老的
    // ============================================================

    @Override
    public YieldVerdict decide(String ruleCode, BigDecimal input, Date at) {
        return decide(ruleCode, input, GiprYieldProperties.UNIT_KG_PER_MU, at);
    }

    /** 老签名之外补一个单位口：报入值按 inputUnit 两式之一报，系统收口换算。 */
    public YieldVerdict decide(String ruleCode, BigDecimal input, String inputUnit, Date at) {
        if (StringUtils.isEmpty(ruleCode)) {
            throw new FlowRuleException("贴线问不出去：缺「线代号」这一栏，不知道拿哪一条线落");
        }
        return decideCore(ruleCode.trim(), null, null, input, inputUnit, at);
    }

    @Override
    public YieldVerdict decideByKind(String objKind, Integer year, BigDecimal input, Date at) {
        return decideByKind(objKind, year, input, GiprYieldProperties.UNIT_KG_PER_MU, at);
    }

    /** 按品类、按年度取线那一路（可带报入单位）。 */
    public YieldVerdict decideByKind(String objKind, Integer year, BigDecimal input, String inputUnit, Date at) {
        return decideCore(null, StringUtils.isEmpty(objKind) ? null : objKind.trim(), year, input, inputUnit, at);
    }

    @Override
    public List<TGiprYieldRule> listVersions(String objKind, Integer year) {
        QueryWrapper<TGiprYieldRule> qw = new QueryWrapper<TGiprYieldRule>().eq("del_flag", 0);
        if (StringUtils.isNotEmpty(objKind)) {
            qw.eq("obj_kind", objKind.trim());
        }
        List<TGiprYieldRule> all = this.giprYieldRuleMapper.selectList(qw);
        List<TGiprYieldRule> out = new ArrayList<>();
        for (TGiprYieldRule r : all) {
            // 已撤下、早年到期的版本行面照旧列得出来，只是不参与求值；年度按启用那一日归年
            if (year != null && (r.getEffStart() == null || yearOf(r.getEffStart()) != year)) {
                continue;
            }
            out.add(r);
        }
        out.sort(new Comparator<TGiprYieldRule>() {
            @Override
            public int compare(TGiprYieldRule a, TGiprYieldRule b) {
                Date da = a.getEffStart();
                Date db = b.getEffStart();
                if (da == null && db == null) {
                    return 0;
                }
                if (da == null) {
                    return 1;
                }
                if (db == null) {
                    return -1;
                }
                return db.compareTo(da); // 启用那一刻倒序
            }
        });
        return out;
    }

    @Override
    public List<String> listBrokenBoardCodes(Date at) {
        if (at == null) {
            throw new FlowRuleException("看板对账问不出去：缺「回到询问这一刻」，没法判哪条线寻不到在位版本");
        }
        // 册面上挂过的全部代号（含已撤下那几版所属的代号）
        List<TGiprYieldRule> all = this.giprYieldRuleMapper.selectList(
                new QueryWrapper<TGiprYieldRule>().eq("del_flag", 0));
        TreeSet<String> hung = new TreeSet<>();
        TreeSet<String> alive = new TreeSet<>();
        for (TGiprYieldRule r : all) {
            if (StringUtils.isNotEmpty(r.getRuleCode())) {
                hung.add(r.getRuleCode());
                if (r.getStatus() != null && r.getStatus() == GiprYieldProperties.STATUS_ON && inWindow(r, at)) {
                    alive.add(r.getRuleCode());
                }
            }
        }
        hung.removeAll(alive); // 挂着代号、这一刻却寻不到在位版本的
        return new ArrayList<>(hung);
    }

    // ============================================================
    // 唯一决策核心：挑版 → 换齐 → 落线，一次算定
    // ============================================================

    private YieldVerdict decideCore(String ruleCode, String objKind, Integer year,
                                   BigDecimal input, String inputUnit, Date at) {
        if (input == null) {
            throw new FlowRuleException("贴线落不下去：缺「实测值」这一栏，没有数可比");
        }
        if (at == null) {
            throw new FlowRuleException("贴线落不下去：缺「实测值发生的那一刻」，不知道挑哪一版");
        }
        String unit = StringUtils.isEmpty(inputUnit) ? GiprYieldProperties.UNIT_KG_PER_MU : inputUnit.trim();

        // 一、先把报入值收口到系统口径「每亩公斤」（定数只取自配置，一处定死）
        BigDecimal canonical = toKgPerMu(input, unit);

        // 二、负数／大得没边，不进落线，也不给像模像样的回话——点名是哪一个数、按哪一条规矩
        if (canonical.compareTo(BigDecimal.ZERO) < 0) {
            throw new FlowRuleException("实测值「" + input.stripTrailingZeros().toPlainString()
                    + "」（" + unit + "）是负数，按「实测值不得为负」这一条不进落线，这笔给不出档");
        }
        if (canonical.compareTo(props.getMaxValueKgPerMu()) > 0) {
            throw new FlowRuleException("实测值「" + input.stripTrailingZeros().toPlainString()
                    + "」（" + unit + "）折成每亩公斤为 "
                    + canonical.setScale(props.getValueScale(), RoundingMode.HALF_UP).toPlainString()
                    + "，大得没边（越过量程上限 "
                    + props.getMaxValueKgPerMu().stripTrailingZeros().toPlainString()
                    + " 每亩公斤），按「超量程不进落线」这一笔给不出档");
        }

        // 三、挑这一刻在位的版本（撤下不顶、邻类不借、过期不凑；挑不出是合法回话）
        List<TGiprYieldRule> window = selectInWindow(objKind, at);
        if (year != null) {
            List<TGiprYieldRule> narrowed = new ArrayList<>();
            for (TGiprYieldRule r : window) {
                if (r.getEffStart() != null && yearOf(r.getEffStart()) == year) {
                    narrowed.add(r);
                }
            }
            window = narrowed;
        }
        if (ruleCode != null) {
            List<TGiprYieldRule> narrowed = new ArrayList<>();
            for (TGiprYieldRule r : window) {
                if (ruleCode.equals(r.getRuleCode())) {
                    narrowed.add(r);
                }
            }
            window = narrowed;
        }
        if (window.isEmpty()) {
            String who = ruleCode != null ? ("线代号「" + ruleCode + "」")
                    : (objKind != null ? ("品类「" + objKind + "」") : "通表");
            return YieldVerdict.none(YieldVerdict.REASON_NO_VERSION,
                    "这一刻（" + ts(at) + "）" + who + " 挑不出在位的版本——已撤下的不顶、邻类品类的不借、"
                            + "早年到期的不凑，这一笔只能明说给不出");
        }

        // 四、几条并立听谁的：序大的先说话；序分不出听代号串靠后的
        window.sort(PRIORITY_THEN_CODE_DESC);
        TGiprYieldRule winner = window.get(0);

        // 五、照线面单位口径换齐、取齐到一处定死的小数位，再落线（先前老线落过的笔不追溯）
        BigDecimal lineValue = fromKgPerMu(canonical, winner.getUnitKind());
        int level = levelOf(winner, lineValue);
        return YieldVerdict.of(winner, level,
                canonical.setScale(props.getValueScale(), RoundingMode.HALF_UP), lineValue);
    }

    /**
     * 三道上限把数切成四段，等号归哪一头只此一处定死：
     * 上限含等号归本段——v≤关注→1，关注&lt;v≤告警→2，告警&lt;v≤封顶→3，v&gt;封顶→4。
     */
    private int levelOf(TGiprYieldRule rule, BigDecimal v) {
        if (rule.getTh1Max() != null && v.compareTo(rule.getTh1Max()) <= 0) {
            return 1;
        }
        if (rule.getTh2Max() != null && v.compareTo(rule.getTh2Max()) <= 0) {
            return 2;
        }
        if (rule.getTh3Max() != null && v.compareTo(rule.getTh3Max()) <= 0) {
            return 3;
        }
        return 4;
    }

    /**
     * 挑这一刻在位的版本：未删 + status=在位 + at ∈ [eff_start, eff_end)。
     * 起始与交回相接那一瞬：旧版 eff_end = 新版 eff_start，at 恰在该瞬不满足旧版的
     * 「eff_end &gt; at」（不含），归后一版。时段一律墙钟字符串绑定，防时区整体偏移。
     */
    private List<TGiprYieldRule> selectInWindow(String objKind, Date at) {
        String atStr = ts(at);
        QueryWrapper<TGiprYieldRule> qw = new QueryWrapper<TGiprYieldRule>()
                .eq("del_flag", 0)
                .eq("status", GiprYieldProperties.STATUS_ON)
                .apply("eff_start <= {0}", atStr)
                .and(w -> w.isNull("eff_end").or().apply("eff_end > {0}", atStr));
        if (StringUtils.isNotEmpty(objKind)) {
            qw.eq("obj_kind", objKind);
        }
        return this.giprYieldRuleMapper.selectList(qw);
    }

    /** Java 侧同一口径的窗口判定（看板对账、usable 复用，不另立一套）。 */
    private boolean inWindow(TGiprYieldRule r, Date at) {
        if (r.getEffStart() == null || r.getEffStart().after(at)) {
            return false;
        }
        return r.getEffEnd() == null || r.getEffEnd().after(at); // 交回那一日不含
    }

    /** 两式计量收口到「每亩公斤」。只认配置里那两个 canonical 串，第三式不认得就点名。 */
    private BigDecimal toKgPerMu(BigDecimal value, String unit) {
        if (GiprYieldProperties.UNIT_KG_PER_MU.equals(unit)) {
            return value;
        }
        if (GiprYieldProperties.UNIT_T_PER_HA.equals(unit)) {
            // 每公顷吨 × 一吨多少公斤 ÷ 一公顷多少亩 = 每亩公斤
            return value.multiply(props.getKgPerTon())
                    .divide(props.getMuPerHectare(), props.getValueScale() + 2, RoundingMode.HALF_UP);
        }
        throw new FlowRuleException("报入单位口径「" + unit + "」不认得：贴线只认「"
                + GiprYieldProperties.UNIT_KG_PER_MU + "」「" + GiprYieldProperties.UNIT_T_PER_HA
                + "」两式，实测值「" + value.stripTrailingZeros().toPlainString() + "」这笔没法换齐，不进落线");
    }

    /** 系统口径「每亩公斤」照线面口径换回去，并在一处取齐小数位。 */
    private BigDecimal fromKgPerMu(BigDecimal canonical, String unitKind) {
        if (GiprYieldProperties.UNIT_KG_PER_MU.equals(unitKind)) {
            return canonical.setScale(props.getValueScale(), RoundingMode.HALF_UP);
        }
        if (GiprYieldProperties.UNIT_T_PER_HA.equals(unitKind)) {
            return canonical.multiply(props.getMuPerHectare())
                    .divide(props.getKgPerTon(), props.getValueScale(), RoundingMode.HALF_UP);
        }
        throw new FlowRuleException("线面单位口径「" + unitKind + "」不认得：只认「"
                + GiprYieldProperties.UNIT_KG_PER_MU + "」「" + GiprYieldProperties.UNIT_T_PER_HA
                + "」两式，这笔换不齐，不进落线");
    }

    private int yearOf(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        return c.get(Calendar.YEAR);
    }
}
