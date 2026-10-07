package com.fc.v2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.common.conf.GiprYieldProperties;
import com.fc.v2.common.exception.FlowRuleException;
import com.fc.v2.mapper.auto.TGiprYieldRuleMapper;
import com.fc.v2.mapper.auto.TGiprYieldVerdictMapper;
import com.fc.v2.model.auto.TGiprYieldRule;
import com.fc.v2.model.auto.TGiprYieldVerdict;
import com.fc.v2.model.custom.GiprYieldMeasure;
import com.fc.v2.model.custom.GiprYieldVerdict;
import com.fc.v2.service.impl.TGiprYieldRuleServiceImpl;
import com.fc.v2.service.impl.TGiprYieldVerdictServiceImpl;

/**
 * 贴线求值服务层集成测试：真跑 SQL（H2 MySQL 兼容模式），
 * 把题面钉死的每一条规矩逐一过堂——挑版本、压界、并立、换算、
 * 两路一口径、挑不出是合法回话、幂等贴批、落库字字相同、不追溯重算。
 */
class TGiprYieldRuleServiceImplTest {

    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static DataSource ds;
    private static TGiprYieldRuleMapper ruleMapper;
    private static TGiprYieldVerdictMapper verdictMapper;
    private static ITGiprYieldRuleService svc;
    private static ITGiprYieldVerdictService verdictSvc;

    @BeforeAll
    static void setUp() throws Exception {
        ds = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("gipr_yield;MODE=MySQL;DB_CLOSE_DELAY=-1")
                .build();
        ScriptUtils.executeSqlScript(ds.getConnection(), new ClassPathResource("schema-h2.sql"));

        MybatisSqlSessionFactoryBean fb = new MybatisSqlSessionFactoryBean();
        fb.setDataSource(ds);
        MybatisConfiguration cfg = new MybatisConfiguration();
        cfg.setMapUnderscoreToCamelCase(true);
        fb.setConfiguration(cfg);
        fb.setGlobalConfig(new GlobalConfig());
        SqlSessionFactory sf = fb.getObject();
        sf.getConfiguration().addMapper(TGiprYieldRuleMapper.class);
        sf.getConfiguration().addMapper(TGiprYieldVerdictMapper.class);

        SqlSessionTemplate st = new SqlSessionTemplate(sf);
        ruleMapper = st.getMapper(TGiprYieldRuleMapper.class);
        verdictMapper = st.getMapper(TGiprYieldVerdictMapper.class);

        TGiprYieldRuleServiceImpl ruleImpl = new TGiprYieldRuleServiceImpl();
        inject(ruleImpl, "giprYieldRuleMapper", ruleMapper);
        svc = ruleImpl;

        TGiprYieldVerdictServiceImpl verdictImpl = new TGiprYieldVerdictServiceImpl();
        inject(verdictImpl, "giprYieldVerdictMapper", verdictMapper);
        inject(verdictImpl, "giprYieldRuleService", svc);
        verdictSvc = verdictImpl;
    }

    @BeforeEach
    void clean() throws Exception {
        try (java.sql.Connection c = ds.getConnection();
             java.sql.Statement s = c.createStatement()) {
            s.executeUpdate("delete from t_gipr_yield_verdict");
            s.executeUpdate("delete from t_gipr_yield_rule");
        }
    }

    private static final AtomicLong SEQ = new AtomicLong(1000);

    private static Date dt(String s) {
        try {
            return FMT.parse(s);
        } catch (java.text.ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    /** 立一条线：代号/品类/三道上限/口径/起止/次序/情形逐项给齐 */
    private TGiprYieldRule seedRule(String code, String kind, String th1, String th2, String th3,
                                    String unit, String start, String end, Integer priority, int status) {
        TGiprYieldRule r = new TGiprYieldRule();
        r.setId(SEQ.incrementAndGet());
        r.setRuleCode(code);
        r.setRuleName(code + "线");
        r.setObjKind(kind);
        r.setTh1Max(th1 == null ? null : bd(th1));
        r.setTh2Max(th2 == null ? null : bd(th2));
        r.setTh3Max(th3 == null ? null : bd(th3));
        r.setUnitKind(unit);
        r.setEffStart(start == null ? null : dt(start));
        r.setEffEnd(end == null ? null : dt(end));
        r.setPriority(priority);
        r.setStatus(status);
        r.setDelFlag(0);
        ruleMapper.insert(r);
        return r;
    }

    private GiprYieldMeasure measure(String kind, Integer year, String input, String unit, String at) {
        GiprYieldMeasure m = new GiprYieldMeasure();
        m.setObjKind(kind);
        m.setAskYear(year);
        m.setInput(input == null ? null : bd(input));
        m.setInputUnit(unit);
        m.setMeasureAt(at == null ? null : dt(at));
        return m;
    }

    // ---------------- 挑版本：那一刻在位的才作数 ----------------

    @Test
    void handoverInstant_belongsToLaterVersion() {
        // 前一版 2024 全年，后一版 2025 元旦接棒：相接的那一瞬归后一版
        seedRule("R1", "种植类", "100", "200", "300", "KG_PER_MU",
                "2024-01-01 00:00:00", "2025-01-01 00:00:00", 1, 0);
        seedRule("R1", "种植类", "200", "400", "600", "KG_PER_MU",
                "2025-01-01 00:00:00", "2026-01-01 00:00:00", 1, 0);

        // 相接那一瞬（2025-01-01 00:00:00）归后一版：150 在后一版 < 关注 200 → 第1段
        assertEquals(1, svc.evaluate("R1", bd("150"), dt("2025-01-01 00:00:00")), "相接一瞬归后一版");
        // 前一瞬仍归前一版：150 在前一版 ∈ [关注100, 告警200) → 第2段
        assertEquals(2, svc.evaluate("R1", bd("150"), dt("2024-12-31 23:59:59")), "前一瞬仍归前一版");
        // 后一版交班那一一日（不含）：2026 元旦那一刻两版都不在位
        assertEquals(0, svc.evaluate("R1", bd("150"), dt("2026-01-01 00:00:00")), "交班日不含，挑不出是合法回话");
    }

    @Test
    void withdrawnVersion_mustNotSubstitute() {
        // 唯一一版已撤下：窗口再合也不许顶上去
        seedRule("R2", "种植类", "100", "200", "300", "KG_PER_MU",
                "2024-01-01 00:00:00", "2026-01-01 00:00:00", 9, 1);
        Date at = dt("2025-06-01 00:00:00");
        assertEquals(0, svc.evaluate("R2", bd("50"), at), "已撤下的不得顶上去");
        GiprYieldVerdict v = svc.verdictByCode("R2", bd("50"), null, at);
        assertFalse(v.isApplicable());
        assertEquals(0, v.getLevel());
        assertNull(v.getRuleCode());
        assertTrue(v.getMessage().contains("挑不出"), v.getMessage());
    }

    @Test
    void neighborKind_mustNotBeBorrowed() {
        // 水产类有在位线，种植类一条都没有：邻类的不借
        seedRule("W1", "水产类", "100", "200", "300", "KG_PER_MU",
                "2024-01-01 00:00:00", "2026-01-01 00:00:00", 1, 0);
        GiprYieldVerdict v = svc.evaluateByKind("种植类", 2025, bd("50"), null, dt("2025-06-01 00:00:00"));
        assertFalse(v.isApplicable(), "邻类品类那一条不得借来凑数");
        assertEquals(0, v.getLevel());
        assertTrue(v.getMessage().contains("挑不出"), v.getMessage());
    }

    @Test
    void noVersion_isLegalAnswer_notAForcedPick() {
        Date at = dt("2025-06-01 00:00:00");
        assertEquals(0, svc.evaluate("NOPE", bd("50"), at), "老路子：挑不出回 0");
        assertEquals(0, svc.evaluateTop(bd("50"), at), "通表也挑不出回 0");
        GiprYieldVerdict v = svc.verdictByCode("NOPE", bd("50"), null, at);
        assertFalse(v.isApplicable(), "「挑不出」本身是合法回话");
        assertEquals(0, v.getLevel());
        assertFalse(v.isNeedSecond(), "挑不出不起第二张");
        assertTrue(v.getMessage().contains("挑不出"), v.getMessage());
    }

    // ---------------- 压界：恰好等于上限归高一档，通篇一个说法 ----------------

    @Test
    void boundaryEquality_goesToHigherSegment() {
        seedRule("R3", "种植类", "100", "200", "300", "KG_PER_MU",
                "2024-01-01 00:00:00", "2026-01-01 00:00:00", 1, 0);
        Date at = dt("2025-06-01 00:00:00");
        assertEquals(1, svc.evaluate("R3", bd("99.999"), at));
        assertEquals(2, svc.evaluate("R3", bd("100"), at), "等于关注那一道归第2段");
        assertEquals(3, svc.evaluate("R3", bd("200"), at), "等于告警那一道归第3段");
        assertEquals(4, svc.evaluate("R3", bd("300"), at), "等于封顶那一道归第4段");
        assertEquals(4, svc.evaluate("R3", bd("300.001"), at));
        assertEquals(1, svc.evaluate("R3", bd("0"), at), "0 是合法实测值");
        // 两路一口径：按代号问与带全貌问，界规是同一处
        GiprYieldVerdict v = svc.verdictByCode("R3", bd("100"), null, at);
        assertEquals(2, v.getLevel(), "恰好压界归哪一头，通篇只许一个说法");
        assertFalse(v.isNeedSecond());
        assertTrue(svc.verdictByCode("R3", bd("300"), null, at).isNeedSecond(), "超封顶那一段才该起第二张");
    }

    // ---------------- 非法实测值：不进落线，指名哪个数、按哪条规矩 ----------------

    @Test
    void invalidInput_namedAndShamed() {
        seedRule("R4", "种植类", "100", "200", "300", "KG_PER_MU",
                "2024-01-01 00:00:00", "2026-01-01 00:00:00", 1, 0);
        Date at = dt("2025-06-01 00:00:00");
        // 老路子：不给一句像模像样的回话（回 0）
        assertEquals(0, svc.evaluate("R4", bd("-1"), at));
        assertEquals(0, svc.evaluate("R4", bd("1000000"), at));
        assertEquals(0, svc.evaluateTop(bd("-1"), at));
        // 新路子：指名是哪一个数不合、按哪一条规矩不合
        FlowRuleException neg = assertThrows(FlowRuleException.class,
                () -> svc.verdictByCode("R4", bd("-1.5"), null, at));
        assertTrue(neg.getMessage().contains("-1.5"), neg.getMessage());
        assertTrue(neg.getMessage().contains("负数"), neg.getMessage());
        FlowRuleException huge = assertThrows(FlowRuleException.class,
                () -> svc.verdictByCode("R4", bd("99999999"), null, at));
        assertTrue(huge.getMessage().contains("99999999"), huge.getMessage());
        assertTrue(huge.getMessage().contains("input-max"), huge.getMessage());
    }

    // ---------------- 并立：先比交班次序，再比代号串；两路入口同一答案 ----------------

    @Test
    void concurrentVersions_priorityThenCodeDesc() {
        // 两条同时在位：次序大的先说话
        seedRule("RA", "种植类", "1000", "2000", "3000", "KG_PER_MU",
                "2024-01-01 00:00:00", "2026-01-01 00:00:00", 1, 0);
        seedRule("RB", "水产类", "10", "20", "30", "KG_PER_MU",
                "2024-01-01 00:00:00", "2026-01-01 00:00:00", 5, 0);
        Date at = dt("2025-06-01 00:00:00");
        // 50 在 RA（次序1）是第1段，在 RB（次序5）是第4段：通表比优听 RB 的
        assertEquals(4, svc.evaluateTop(bd("50"), at), "交班次序数值大的先说话");
        assertEquals(1, svc.evaluate("RA", bd("50"), at), "按代号问只在本代号版本里挑");
        assertEquals(4, svc.evaluate("RB", bd("50"), at));

        // 次序并列：代号串排得靠后的报数
        seedRule("RC", "初加工类", "5", "6", "7", "KG_PER_MU",
                "2024-01-01 00:00:00", "2026-01-01 00:00:00", 5, 0);
        // RB 与 RC 同为次序5：RC 代号串靠后 → 听 RC 的（50 ≥ 封顶7 → 第4段；RB 也是4，换输入分清）
        assertEquals(4, svc.evaluateTop(bd("25"), at), "序并列比代号串：RC 靠后报数（25 在 RB 是第3段，在 RC 是第4段）");
        assertEquals(3, svc.evaluate("RB", bd("25"), at));

        // 两路入口同一答案：按代号问那一路与通表比优那一路必须是同一个
        GiprYieldVerdict byCode = svc.verdictByCode("RC", bd("25"), null, at);
        GiprYieldVerdict top = svc.verdictTop(bd("25"), null, at);
        assertEquals(byCode.getRuleCode(), top.getRuleCode(), "两条入口挑中的须是同一条线");
        assertEquals(byCode.getLevel(), top.getLevel(), "两个地方答出来的必须是同一个");
        assertEquals(svc.evaluate("RC", bd("25"), at), svc.evaluateTop(bd("25"), at));
    }

    // ---------------- 单位两式：换齐再比，定数与取齐位出自配置一处 ----------------

    @Test
    void unitConversion_beforeCompare() {
        // 线面是每公顷吨：三道 6/12/18 吨每公顷（合每亩公斤 400/800/1200）
        seedRule("U1", "种植类", "6", "12", "18", "TON_PER_HECTARE",
                "2024-01-01 00:00:00", "2026-01-01 00:00:00", 1, 0);
        Date at = dt("2025-06-01 00:00:00");

        GiprYieldVerdict onLine = svc.verdictByCode("U1", bd("400"), "KG_PER_MU", at);
        assertEquals(new BigDecimal("6.000"), onLine.getInputConv(), "400 公斤/亩 换齐为 6.000 吨/公顷");
        assertEquals(2, onLine.getLevel(), "换齐后恰压关注道，归第2段");
        assertEquals(1, svc.verdictByCode("U1", bd("399.9"), "KG_PER_MU", at).getLevel());
        assertEquals(3, svc.verdictByCode("U1", bd("800"), "KG_PER_MU", at).getLevel());
        assertEquals(4, svc.verdictByCode("U1", bd("1200"), "KG_PER_MU", at).getLevel());
        assertEquals(2, svc.verdictByCode("U1", bd("400"), "每亩公斤", at).getLevel(), "汉文口径写法一样认");

        // 反向：线面是每亩公斤，实测带每公顷吨
        seedRule("U2", "水产类", "400", "800", "1200", "KG_PER_MU",
                "2024-01-01 00:00:00", "2026-01-01 00:00:00", 1, 0);
        GiprYieldVerdict rev = svc.verdictByCode("U2", bd("6"), "TON_PER_HECTARE", at);
        assertEquals(new BigDecimal("400.000"), rev.getInputConv(), "6 吨/公顷 换齐为 400.000 公斤/亩");
        assertEquals(2, rev.getLevel());
        assertEquals(1, svc.verdictByCode("U2", bd("5.999"), "TON_PER_HECTARE", at).getLevel());

        // 老路子不带口径：默认已按线面口径给齐，不另换算
        assertEquals(2, svc.evaluate("U1", bd("6"), at), "老路子按线面口径直接落");
        assertEquals(1, svc.evaluate("U1", bd("5.999"), at));

        // 口径写法系统不认：指名报错
        FlowRuleException bad = assertThrows(FlowRuleException.class,
                () -> svc.verdictByCode("U1", bd("50"), "每斤公斤", at));
        assertTrue(bad.getMessage().contains("每斤公斤"), bad.getMessage());
        assertTrue(bad.getMessage().contains("不认"), bad.getMessage());
    }

    @Test
    void constants_comeFromConfigOnly() {
        // 另起一只服务实例，定数换掉：一公顷合十亩——换算跟着配置走
        TGiprYieldRuleServiceImpl custom = new TGiprYieldRuleServiceImpl();
        inject(custom, "giprYieldRuleMapper", ruleMapper);
        GiprYieldProperties props = new GiprYieldProperties();
        props.setMuPerHectare(bd("10"));
        inject(custom, "giprYieldProperties", props);

        seedRule("U3", "种植类", "0.7", "1.4", "2.1", "TON_PER_HECTARE",
                "2024-01-01 00:00:00", "2026-01-01 00:00:00", 1, 0);
        Date at = dt("2025-06-01 00:00:00");
        // 60 公斤/亩：一公顷合十亩 → 0.6 吨/公顷（<0.7 第1段）；合十五亩 → 0.9（≥0.7 第2段）
        assertEquals(1, custom.verdictByCode("U3", bd("60"), "KG_PER_MU", at).getLevel(),
                "换算定数跟着配置走（一公顷合十亩）");
        assertEquals(2, svc.verdictByCode("U3", bd("60"), "KG_PER_MU", at).getLevel(),
                "默认配置一公顷合十五亩");
        // 没边线改成 50：60 越线不合，指名配置那一处
        props.setInputMax(bd("50"));
        FlowRuleException e = assertThrows(FlowRuleException.class,
                () -> custom.verdictByCode("U3", bd("60"), null, at));
        assertTrue(e.getMessage().contains("50"), e.getMessage());
        assertEquals(4, svc.evaluate("U3", bd("60"), at), "默认没边线 1000000，60 照常按线面口径落线（越封顶）");
    }

    // ---------------- 按品类、按年度：年度守卫，隔年度的不借 ----------------

    @Test
    void askYear_guardsAgainstCrossYearBorrowing() {
        seedRule("Y1", "种植类", "100", "200", "300", "KG_PER_MU",
                "2024-01-01 00:00:00", "2026-01-01 00:00:00", 1, 0);
        // 实测那一刻不在所问年度里：指名两头对不上
        FlowRuleException e = assertThrows(FlowRuleException.class,
                () -> svc.evaluateByKind("种植类", 2024, bd("50"), null, dt("2025-06-01 00:00:00")));
        assertTrue(e.getMessage().contains("2024"), e.getMessage());
        assertTrue(e.getMessage().contains("2025-06-01"), e.getMessage());
        // 同一年度：照常落线
        assertEquals(1, svc.evaluateByKind("种植类", 2025, bd("50"), null,
                dt("2025-06-01 00:00:00")).getLevel());
        // 不问年度（null）：只按那一刻挑
        assertEquals(1, svc.evaluateByKind("种植类", null, bd("50"), null,
                dt("2025-06-01 00:00:00")).getLevel());
    }

    // ---------------- 口径一致：usable/count/list 与定位同一谓词；行面照旧列得出 ----------------

    @Test
    void usableCountList_shareOnePredicate_andExpiredStillListed() {
        TGiprYieldRule expired = seedRule("L0", "种植类", "1", "2", "3", "KG_PER_MU",
                "2023-01-01 00:00:00", "2024-01-01 00:00:00", 1, 0);
        TGiprYieldRule curHigh = seedRule("L1", "种植类", "1", "2", "3", "KG_PER_MU",
                "2024-01-01 00:00:00", "2026-01-01 00:00:00", 9, 0);
        TGiprYieldRule curLow = seedRule("L2", "水产类", "1", "2", "3", "KG_PER_MU",
                "2024-01-01 00:00:00", "2026-01-01 00:00:00", 2, 0);
        TGiprYieldRule withdrawn = seedRule("L3", "水产类", "1", "2", "3", "KG_PER_MU",
                "2024-01-01 00:00:00", "2026-01-01 00:00:00", 99, 1);
        Date at = dt("2025-06-01 00:00:00");

        List<TGiprYieldRule> avail = svc.listAvailable(at);
        assertEquals(2, avail.size(), "只有在位的两条（撤下/到期的不参与求值）");
        assertEquals("L1", avail.get(0).getRuleCode(), "交班次序大的在前");
        assertEquals("L2", avail.get(1).getRuleCode());
        assertEquals(2, svc.countAvailable(at));
        assertTrue(svc.usable(curHigh.getId(), at));
        assertTrue(svc.usable(curLow.getId(), at));
        assertFalse(svc.usable(expired.getId(), at), "早年到期的不再参与求值");
        assertFalse(svc.usable(withdrawn.getId(), at), "已撤下的不得顶上去");

        // 行面照旧列得出来：到期、撤下的都在册
        List<TGiprYieldRule> all = svc.listVersions(null, null);
        assertEquals(4, all.size(), "行面照旧列得出来，只是不再参与求值");
        List<TGiprYieldRule> plantedOnly = svc.listVersions("种植类", null);
        assertEquals(2, plantedOnly.size());

        // 看板对账：挂着代号却寻不到在位版本的逐条列出
        List<String> unmatched = svc.listUnmatchedCodes(
                Arrays.asList("L0", "L1", "L3", "NOPE", "L1", null), at);
        assertEquals(Arrays.asList("L0", "L3", "NOPE"), unmatched,
                "到期、撤下、查无此号的逐条列出；重复的只列一回");
    }

    @Test
    void openEndedWindows_areInForce() {
        // 起止空端放开：没立启用日自始在、没立交班日一直在
        seedRule("E1", "种植类", "100", "200", "300", "KG_PER_MU", null, null, 1, 0);
        assertEquals(1, svc.evaluate("E1", bd("50"), dt("2015-01-01 00:00:00")), "没立启用日自始在");
        assertEquals(1, svc.evaluate("E1", bd("50"), dt("2035-01-01 00:00:00")), "没立交班日一直在");
    }

    // ---------------- 贴批留痕：幂等、字字相同、不追溯重算 ----------------

    @Test
    void pasteBatch_idempotent_andPinnedVerbatim() {
        TGiprYieldRule rule = seedRule("P1", "种植类", "100", "200", "300", "KG_PER_MU",
                "2024-01-01 00:00:00", "2026-01-01 00:00:00", 1, 0);
        List<GiprYieldMeasure> items = Arrays.asList(
                measure("种植类", 2025, "250", null, "2025-06-01 00:00:00"),
                measure("种植类", 2025, "-3", null, "2025-06-01 00:00:00"),
                measure("种植类", 2025, "50", null, "2025-07-01 00:00:00"));

        List<TGiprYieldVerdict> first = verdictSvc.pasteBatch("B1", items);
        assertEquals(3, first.size());

        // 落库钉的线代号与启用那一一日，与求值当场返回的字字相同
        GiprYieldVerdict v0 = svc.evaluateByKind("种植类", 2025, bd("250"), null, dt("2025-06-01 00:00:00"));
        assertEquals(v0.getRuleCode(), first.get(0).getRuleCode(), "线代号字字相同");
        assertEquals(v0.getEffStart(), first.get(0).getEffStart(), "启用那一一日字字相同");
        assertEquals(rule.getEffStart(), first.get(0).getEffStart());
        assertEquals(3, first.get(0).getLevel().intValue(), "250 ∈ [告警200, 封顶300) → 第3段");
        assertEquals(0, first.get(0).getNeedSecond().intValue());
        assertEquals(1, first.get(0).getApplicable().intValue());

        // 数不合的那一行：不进落线，message 指名哪个数、按哪条规矩
        assertEquals(0, first.get(1).getApplicable().intValue());
        assertEquals(0, first.get(1).getLevel().intValue());
        assertNull(first.get(1).getRuleCode());
        assertTrue(first.get(1).getMessage().contains("-3"), first.get(1).getMessage());
        assertTrue(first.get(1).getMessage().contains("负数"), first.get(1).getMessage());

        // 同一批并着贴两回：重取不重建
        List<TGiprYieldVerdict> again = verdictSvc.pasteBatch("B1", items);
        assertEquals(first.size(), again.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).getId(), again.get(i).getId(), "幂等：同一批同一行同一个");
        }
        // 两回行数对不上：点名报不合
        FlowRuleException conflict = assertThrows(FlowRuleException.class,
                () -> verdictSvc.pasteBatch("B1", items.subList(0, 1)));
        assertTrue(conflict.getMessage().contains("对不上"), conflict.getMessage());
    }

    @Test
    void pasteBatch_neverRecomputesOldRows() {
        seedRule("P2", "种植类", "100", "200", "300", "KG_PER_MU",
                "2024-01-01 00:00:00", "2026-01-01 00:00:00", 1, 0);
        List<GiprYieldMeasure> items = new ArrayList<>(
                Arrays.asList(measure("种植类", 2025, "250", null, "2025-06-01 00:00:00")));
        List<TGiprYieldVerdict> before = verdictSvc.pasteBatch("B2", items);
        assertEquals(3, before.get(0).getLevel().intValue(), "老线：250 落第3段");

        // 线改了：同一代号立一版更靠后的（次序更大，那一刻听它的）
        seedRule("P2", "种植类", "1000", "2000", "3000", "KG_PER_MU",
                "2024-01-01 00:00:00", "2026-01-01 00:00:00", 9, 0);
        // 往后的每一笔照新线走
        assertEquals(1, svc.evaluate("P2", bd("250"), dt("2025-06-01 00:00:00")), "新线：250 落第1段");
        // 先前按老线落下的那些笔不追溯重算
        List<TGiprYieldVerdict> after = verdictSvc.pasteBatch("B2", items);
        assertEquals(before.get(0).getId(), after.get(0).getId());
        assertEquals(3, after.get(0).getLevel().intValue(), "老线落下的笔不动，不追溯重算");
        // 新起一批才照新线落
        List<TGiprYieldVerdict> fresh = verdictSvc.pasteBatch("B3", items);
        assertEquals(1, fresh.get(0).getLevel().intValue(), "往后的每一笔照新线走");
        assertNotEquals(before.get(0).getId(), fresh.get(0).getId());
    }

    @Test
    void pasteBatch_blankBatchOrEmptyItems_named() {
        FlowRuleException noBatch = assertThrows(FlowRuleException.class,
                () -> verdictSvc.pasteBatch("  ", Arrays.asList(measure("种植类", null, "1", null, null))));
        assertTrue(noBatch.getMessage().contains("批次码"), noBatch.getMessage());
        FlowRuleException empty = assertThrows(FlowRuleException.class,
                () -> verdictSvc.pasteBatch("B9", new ArrayList<GiprYieldMeasure>()));
        assertTrue(empty.getMessage().contains("一行"), empty.getMessage());
    }

    // ---------------- 逐条回算与看板格同一次算定 ----------------

    @Test
    void recalcBatch_matchesCellOneByOne() {
        seedRule("C1", "种植类", "100", "200", "300", "KG_PER_MU",
                "2024-01-01 00:00:00", "2026-01-01 00:00:00", 1, 0);
        List<GiprYieldMeasure> items = Arrays.asList(
                measure("种植类", 2025, "50", null, "2025-06-01 00:00:00"),
                measure("种植类", 2025, "150", null, "2025-06-01 00:00:00"),
                measure("种植类", 2025, "250", null, "2025-06-01 00:00:00"),
                measure("种植类", 2025, "350", null, "2025-06-01 00:00:00"),
                measure("种植类", 2025, null, null, "2025-06-01 00:00:00"));
        List<GiprYieldVerdict> batch = svc.recalcBatch(items);
        assertEquals(5, batch.size());
        for (int i = 0; i < 4; i++) {
            GiprYieldVerdict single = svc.evaluateByKind("种植类", 2025, items.get(i).getInput(), null,
                    items.get(i).getMeasureAt());
            assertEquals(single.getLevel(), batch.get(i).getLevel(),
                    "逐条回算与看板那一格同一次算定，不许各自数一遍");
            assertEquals(single.getRuleCode(), batch.get(i).getRuleCode());
        }
        assertEquals(Arrays.asList(1, 2, 3, 4),
                Arrays.asList(batch.get(0).getLevel(), batch.get(1).getLevel(),
                        batch.get(2).getLevel(), batch.get(3).getLevel()));
        assertTrue(batch.get(3).isNeedSecond(), "超封顶那一段该起第二张");
        // 缺栏的那一行：指名，不拖垮整批
        assertFalse(batch.get(4).isApplicable());
        assertTrue(batch.get(4).getMessage().contains("实测值"), batch.get(4).getMessage());
    }

    private static void inject(Object target, String field, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
