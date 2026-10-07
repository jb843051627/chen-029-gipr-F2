package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.conf.GiprYieldProperties;
import com.fc.v2.common.exception.FlowRuleException;
import com.fc.v2.mapper.auto.TGiprYieldRuleMapper;
import com.fc.v2.model.auto.TGiprYieldRule;
import com.fc.v2.model.custom.GiprYieldMeasure;
import com.fc.v2.model.custom.GiprYieldVerdict;
import com.fc.v2.service.ITGiprYieldRuleService;
import com.fc.v2.util.StringUtils;

/**
 * 核定产量与特色指标换算贴线档 Service业务层处理（rule-eval 形状）
 *
 * 全模块只认这一处给出的答案：
 * 这一刻采信哪一条线（{@link #inForce} + {@link #CORE_ORDER}）、
 * 一条也采信不上时给不给说法（给——「挑不出」是合法回话，硬挑一个才是缺陷）、
 * 几条并届时听谁的（交班次序→代号串→启用日→主键）、
 * 实测值恰好压在某一道上限归进哪一段（{@link #levelOf}，等于上限取高一档）、
 * 该不该起第二张（落在第4段超封顶才起）、
 * 单位两式在哪儿换齐（{@link #convert}，定数与取齐位全出自 gipr.yield 配置那一格）。
 * 屏上与旁处不得各答其中一问，也不许先落一段再回头挑一条线来圆。
 *
 * 时段比较全在 Java 一侧过 {@link #inForce} 这一道谓词，不把 java.util.Date 直接绑进 SQL：
 * JDBC 的 serverTimezone 与本机时区不对称，绑参会整体偏移，边界判定在「切换当日」随机错档。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TGiprYieldRuleServiceImpl implements ITGiprYieldRuleService {

    /** 线的情形：0 在位（已撤下为 1，撤下的不得顶上去） */
    private static final int STATUS_IN = 0;

    /**
     * 几条并立听谁的（只此一处定序）：交班次序数值大的先说话；序分不出高下，
     * 退一步比代号串自身的先后，排得靠后的那条报数；再并比启用那一一日晚的；
     * 末了比主键——排定就不再摇，两路入口答出来的必是同一个。
     */
    private static final Comparator<TGiprYieldRule> CORE_ORDER = new Comparator<TGiprYieldRule>() {
        @Override
        public int compare(TGiprYieldRule a, TGiprYieldRule b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            int byCode = cb.compareTo(ca);
            if (byCode != 0) {
                return byCode;
            }
            long ea = a.getEffStart() == null ? Long.MIN_VALUE : a.getEffStart().getTime();
            long eb = b.getEffStart() == null ? Long.MIN_VALUE : b.getEffStart().getTime();
            if (ea != eb) {
                return Long.compare(eb, ea);
            }
            long ia = a.getId() == null ? Long.MIN_VALUE : a.getId();
            long ib = b.getId() == null ? Long.MIN_VALUE : b.getId();
            return Long.compare(ib, ia);
        }
    };

    @javax.annotation.Resource
    private TGiprYieldRuleMapper giprYieldRuleMapper;

    /** 贴线定数（一公顷合十五亩这类）：字段自带与配置一致的默认值，容器在则以容器为准 */
    @javax.annotation.Resource
    private GiprYieldProperties giprYieldProperties = new GiprYieldProperties();

    // ------------------------------------------------------------------
    // 老路子：收什么回什么一概不动，行为归到同一处算定
    // ------------------------------------------------------------------

    @Override
    public TGiprYieldRule selectTGiprYieldRuleById(Long id) {
        return this.giprYieldRuleMapper.selectById(id);
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (StringUtils.isEmpty(ruleCode) || input == null || at == null) {
            return 0;
        }
        if (!isValidInput(input)) {
            return 0; // 不进落线，也不给一句像模像样的回话
        }
        GiprYieldVerdict v = decide(loadByCode(ruleCode.trim()), input, null, at,
                "代号「" + ruleCode.trim() + "」");
        return v.isApplicable() ? v.getLevel() : 0;
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return 0;
        }
        if (!isValidInput(input)) {
            return 0;
        }
        GiprYieldVerdict v = decide(loadAll(), input, null, at, "通表");
        return v.isApplicable() ? v.getLevel() : 0;
    }

    @Override
    public List<TGiprYieldRule> listAvailable(Date at) {
        List<TGiprYieldRule> out = new ArrayList<>();
        if (at == null) {
            return out;
        }
        for (TGiprYieldRule r : loadAll()) {
            if (inForce(r, at)) {
                out.add(r);
            }
        }
        out.sort(CORE_ORDER);
        return out;
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        return inForce(this.giprYieldRuleMapper.selectById(id), at);
    }

    @Override
    public int countAvailable(Date at) {
        return listAvailable(at).size();
    }

    // ------------------------------------------------------------------
    // 后添的等价路子：同一次挑线、同一条界规、同一处换算，回话带全乎
    // ------------------------------------------------------------------

    @Override
    public GiprYieldVerdict verdictByCode(String ruleCode, BigDecimal input, String inputUnit, Date at) {
        require(!StringUtils.isEmpty(ruleCode), "缺「线代号」这一栏，这一笔不进落线");
        require(input != null, "缺「实测值」这一栏，这一笔不进落线");
        require(at != null, "缺「那一刻」这一栏，这一笔不进落线");
        checkInputOrThrow(input);
        return decide(loadByCode(ruleCode.trim()), input, inputUnit, at, "代号「" + ruleCode.trim() + "」");
    }

    @Override
    public GiprYieldVerdict verdictTop(BigDecimal input, String inputUnit, Date at) {
        require(input != null, "缺「实测值」这一栏，这一笔不进落线");
        require(at != null, "缺「那一刻」这一栏，这一笔不进落线");
        checkInputOrThrow(input);
        return decide(loadAll(), input, inputUnit, at, "通表");
    }

    @Override
    public GiprYieldVerdict evaluateByKind(String objKind, Integer askYear, BigDecimal input, String inputUnit,
                                           Date at) {
        require(!StringUtils.isEmpty(objKind), "缺「品类」这一栏（种植类/水产类/初加工类），这一笔不进落线");
        require(input != null, "缺「实测值」这一栏，这一笔不进落线");
        require(at != null, "缺「那一刻」这一栏，这一笔不进落线");
        checkInputOrThrow(input);
        if (askYear != null) {
            checkAtInYear(at, askYear);
        }
        return decide(loadByKind(objKind.trim()), input, inputUnit, at, "品类「" + objKind.trim() + "」");
    }

    @Override
    public List<GiprYieldVerdict> recalcBatch(List<GiprYieldMeasure> items) {
        List<GiprYieldVerdict> out = new ArrayList<>();
        if (items == null) {
            return out;
        }
        for (GiprYieldMeasure m : items) {
            out.add(recalcOne(m));
        }
        return out;
    }

    /** 单条回算：哪一栏不合、哪个数越界，落进判定全貌的 message 里指名，不拖垮整批 */
    private GiprYieldVerdict recalcOne(GiprYieldMeasure m) {
        try {
            if (m == null) {
                throw new FlowRuleException("这一行是空的，缺「品类」「实测值」「那一刻」几栏，不进落线");
            }
            return evaluateByKind(m.getObjKind(), m.getAskYear(), m.getInput(), m.getInputUnit(), m.getMeasureAt());
        } catch (FlowRuleException e) {
            GiprYieldVerdict v = new GiprYieldVerdict();
            v.setApplicable(false);
            v.setLevel(0);
            if (m != null) {
                v.setObjKind(m.getObjKind());
                v.setInputRaw(m.getInput());
                v.setInputUnit(StringUtils.isEmpty(m.getInputUnit()) ? null : m.getInputUnit().trim());
            }
            v.setMessage(e.getMessage());
            return v;
        }
    }

    @Override
    public List<String> listUnmatchedCodes(Collection<String> ruleCodes, Date at) {
        List<String> out = new ArrayList<>();
        if (ruleCodes == null) {
            return out;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String code : ruleCodes) {
            if (StringUtils.isEmpty(code)) {
                continue;
            }
            String c = code.trim();
            if (!seen.add(c)) {
                continue;
            }
            boolean hit = false;
            for (TGiprYieldRule r : loadByCode(c)) {
                if (at != null && inForce(r, at)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                out.add(c); // 这一刻挑不出在位版本的，逐条列出——「挑不出」是合法回话
            }
        }
        return out;
    }

    @Override
    public List<TGiprYieldRule> listVersions(String objKind, String ruleCode) {
        QueryWrapper<TGiprYieldRule> qw = new QueryWrapper<TGiprYieldRule>().eq("del_flag", 0);
        if (!StringUtils.isEmpty(objKind)) {
            qw.eq("obj_kind", objKind.trim());
        }
        if (!StringUtils.isEmpty(ruleCode)) {
            qw.eq("rule_code", ruleCode.trim());
        }
        // SQL 先按启用日、主键排（分页插件在库侧截段，页与页之间次序才不摇）；
        // Java 再按同一次序过一遍，把空启用日的摆法定死（空端视作最前）
        qw.orderByAsc("eff_start").orderByAsc("id");
        List<TGiprYieldRule> list = this.giprYieldRuleMapper.selectList(qw);
        // 行面照旧列得出来（含已到期、已撤下）：按启用那一一日升序、主键升序，只读不参与求值
        list.sort(new Comparator<TGiprYieldRule>() {
            @Override
            public int compare(TGiprYieldRule a, TGiprYieldRule b) {
                long ea = a.getEffStart() == null ? Long.MIN_VALUE : a.getEffStart().getTime();
                long eb = b.getEffStart() == null ? Long.MIN_VALUE : b.getEffStart().getTime();
                if (ea != eb) {
                    return Long.compare(ea, eb);
                }
                long ia = a.getId() == null ? Long.MIN_VALUE : a.getId();
                long ib = b.getId() == null ? Long.MIN_VALUE : b.getId();
                return Long.compare(ia, ib);
            }
        });
        return list;
    }

    // ------------------------------------------------------------------
    // 一处算定：挑线 → 换齐 → 落段 → 该不该起第二张
    // ------------------------------------------------------------------

    /**
     * 一次算定：候选里那一刻在位的挑出来，按 CORE_ORDER 排定取头一条；
     * 一条也挑不出就明说给不出（applicable=false），已撤下的不顶、邻类的不借。
     * 实测值先按线面口径换齐再落段；落库要钉的线代号与启用那一一日就在返回件上。
     */
    private GiprYieldVerdict decide(List<TGiprYieldRule> candidates, BigDecimal input, String inputUnit, Date at,
                                    String scopeDesc) {
        List<TGiprYieldRule> usable = new ArrayList<>();
        for (TGiprYieldRule r : candidates) {
            if (inForce(r, at)) {
                usable.add(r);
            }
        }
        GiprYieldVerdict v = new GiprYieldVerdict();
        v.setInputRaw(input);
        v.setInputUnit(StringUtils.isEmpty(inputUnit) ? null : inputUnit.trim());
        if (usable.isEmpty()) {
            v.setApplicable(false);
            v.setLevel(0);
            v.setMessage("这一刻（" + fmt(at) + "）" + scopeDesc + "在位的一版都挑不出：已撤下的不顶、邻类的不借，"
                    + "这一笔给不出档位");
            return v;
        }
        usable.sort(CORE_ORDER);
        TGiprYieldRule rule = usable.get(0);
        BigDecimal conv = convert(input, inputUnit, rule);
        int level = levelOf(rule, conv);
        v.setApplicable(true);
        v.setRuleId(rule.getId());
        v.setRuleCode(rule.getRuleCode());
        v.setRuleName(rule.getRuleName());
        v.setObjKind(rule.getObjKind());
        v.setUnitKind(rule.getUnitKind());
        v.setEffStart(rule.getEffStart());
        v.setEffEnd(rule.getEffEnd());
        v.setInputConv(conv);
        v.setLevel(level);
        // 该不该起第二张只此一处定：落在第4段（超封顶）才起，其余不起
        v.setNeedSecond(level == 4);
        v.setMessage("采信「" + rule.getRuleCode() + "」（启用 " + fmt(rule.getEffStart()) + "），落在第" + level + "段"
                + (level == 4 ? "，超封顶，该起第二张" : ""));
        return v;
    }

    /**
     * 这一刻在不在位（全系统只此一道谓词）：未删、情形在位（0）、
     * 启用那一一日 ≤ at ＜ 交班那一一日（不含）——起始与交回两日相接的那一瞬归后一版。
     * 起/止空端放开：没立启用日即自始在、没立交班日即一直在。
     */
    private boolean inForce(TGiprYieldRule r, Date at) {
        if (r == null || at == null) {
            return false;
        }
        if (r.getDelFlag() != null && r.getDelFlag() != 0) {
            return false;
        }
        if (r.getStatus() == null || r.getStatus() != STATUS_IN) {
            return false; // 已撤下的那一条不得顶上去
        }
        if (r.getEffStart() != null && at.before(r.getEffStart())) {
            return false;
        }
        if (r.getEffEnd() != null && !at.before(r.getEffEnd())) {
            return false; // 交班那一一日（不含），相接的那一瞬归后一版
        }
        return true;
    }

    /**
     * 落线（全系统只此一处定界规，别处不得另立一套含不含等号的说法）：
     * 三道上限把数切成四段，恰好压在某一道上限归高一档——
     * 等于关注归第2段、等于告警归第3段、等于封顶归第4段。
     * 即：input ＜ 关注 → 1；关注 ≤ input ＜ 告警 → 2；告警 ≤ input ＜ 封顶 → 3；input ≥ 封顶 → 4。
     * 某一道没立（null）就跳过那一道。
     */
    private int levelOf(TGiprYieldRule rule, BigDecimal input) {
        if (rule.getTh1Max() != null && input.compareTo(rule.getTh1Max()) < 0) {
            return 1;
        }
        if (rule.getTh2Max() != null && input.compareTo(rule.getTh2Max()) < 0) {
            return 2;
        }
        if (rule.getTh3Max() != null && input.compareTo(rule.getTh3Max()) < 0) {
            return 3;
        }
        return 4;
    }

    /**
     * 单位两式换齐再比（全系统只此一处换算）：
     * 实测值没带口径（空）即默认已按线面口径给齐，原样落（老路子即此）；
     * 带了口径且与线面不一，先归到每亩公斤、再折到线面口径，
     * 一公顷合十五亩这类定数与小数取齐位全出自 gipr.yield 配置那一格。
     * 口径写法系统不认的，指名是哪一处带来的、哪一格立得不合。
     */
    private BigDecimal convert(BigDecimal input, String inputUnit, TGiprYieldRule rule) {
        String from = normalizeUnit(inputUnit, "实测值带来");
        if (from == null) {
            return input;
        }
        String to = normalizeUnit(rule.getUnitKind(), "线「" + rule.getRuleCode() + "」面上");
        if (to == null || from.equals(to)) {
            return input;
        }
        BigDecimal kgPerMu = UNIT_TON_PER_HECTARE.equals(from)
                ? input.multiply(giprYieldProperties.getKgPerTon())
                        .divide(giprYieldProperties.getMuPerHectare(), MathContext.DECIMAL128)
                : input;
        BigDecimal out = UNIT_TON_PER_HECTARE.equals(to)
                ? kgPerMu.multiply(giprYieldProperties.getMuPerHectare())
                        .divide(giprYieldProperties.getKgPerTon(), MathContext.DECIMAL128)
                : kgPerMu;
        return out.setScale(giprYieldProperties.getScale(), RoundingMode.HALF_UP);
    }

    /** 口径写法归一：空为没带（null）；两式各认代号与汉文；此外指名报错 */
    private String normalizeUnit(String unit, String who) {
        if (unit == null || unit.trim().isEmpty()) {
            return null;
        }
        String u = unit.trim();
        if (UNIT_KG_PER_MU.equalsIgnoreCase(u) || "每亩公斤".equals(u)) {
            return UNIT_KG_PER_MU;
        }
        if (UNIT_TON_PER_HECTARE.equalsIgnoreCase(u) || "每公顷吨".equals(u)) {
            return UNIT_TON_PER_HECTARE;
        }
        throw new FlowRuleException(who + "的单位口径「" + u + "」系统不认：只认每亩公斤（KG_PER_MU）／"
                + "每公顷吨（TON_PER_HECTARE）两式");
    }

    /** 实测值合不合（只此一条规矩）：负数不合；达到/越过大得没边线（gipr.yield.input-max 一处定）不合 */
    private boolean isValidInput(BigDecimal input) {
        return input != null
                && input.compareTo(BigDecimal.ZERO) >= 0
                && input.compareTo(giprYieldProperties.getInputMax()) < 0;
    }

    /** 新路子不合即抛：指名是哪一个数不合、按哪一条规矩不合 */
    private void checkInputOrThrow(BigDecimal input) {
        if (input == null) {
            throw new FlowRuleException("缺「实测值」这一栏，这一笔不进落线");
        }
        if (input.compareTo(BigDecimal.ZERO) < 0) {
            throw new FlowRuleException("实测值「" + input.toPlainString() + "」不合：负数不进落线"
                    + "（规矩：实测值须 ≥ 0）");
        }
        if (input.compareTo(giprYieldProperties.getInputMax()) >= 0) {
            throw new FlowRuleException("实测值「" + input.toPlainString() + "」不合：达到/越过大得没边线「"
                    + giprYieldProperties.getInputMax().toPlainString()
                    + "」（规矩：gipr.yield.input-max 一处定的那道），不进落线");
        }
    }

    /** 年度守卫：回头复算只认同一年度在岗的那一版，隔年度的不借 */
    private void checkAtInYear(Date at, int askYear) {
        Calendar c = Calendar.getInstance();
        c.setTime(at);
        int y = c.get(Calendar.YEAR);
        if (y != askYear) {
            throw new FlowRuleException("实测那一刻「" + fmt(at) + "」不在所问年度「" + askYear
                    + "」里：回头复算只认同一年度在岗的那一版，隔年度的不借");
        }
    }

    private void require(boolean ok, String message) {
        if (!ok) {
            throw new FlowRuleException(message);
        }
    }

    private List<TGiprYieldRule> loadByCode(String ruleCode) {
        return this.giprYieldRuleMapper.selectList(new QueryWrapper<TGiprYieldRule>()
                .eq("del_flag", 0).eq("rule_code", ruleCode));
    }

    private List<TGiprYieldRule> loadByKind(String objKind) {
        return this.giprYieldRuleMapper.selectList(new QueryWrapper<TGiprYieldRule>()
                .eq("del_flag", 0).eq("obj_kind", objKind));
    }

    private List<TGiprYieldRule> loadAll() {
        return this.giprYieldRuleMapper.selectList(new QueryWrapper<TGiprYieldRule>().eq("del_flag", 0));
    }

    private static String fmt(Date d) {
        return d == null ? "（没立）" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }
}
