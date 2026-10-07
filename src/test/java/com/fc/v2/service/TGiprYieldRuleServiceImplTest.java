package com.fc.v2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import com.fc.v2.model.auto.TGiprYieldRule;
import com.fc.v2.model.custom.YieldVerdict;
import com.fc.v2.service.impl.TGiprYieldRuleServiceImpl;

/**
 * 核定产量贴线求值服务层集成测试：真跑 SQL（H2 MySQL 兼容模式），
 * 逐条钉死题面规矩——选版、相接瞬归后版、撤下不顶、邻类不借、
 * 并立次序、两路同答、压线归段、越界点名、单位换齐、幂等、看板断线、按品类年度。
 */
class TGiprYieldRuleServiceImplTest {

    private static DataSource ds;
    private static TGiprYieldRuleMapper mapper;
    private static ITGiprYieldRuleService svc;
    private static TGiprYieldRuleServiceImpl impl;
    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static final String KG = GiprYieldProperties.UNIT_KG_PER_MU;
    private static final String THA = GiprYieldProperties.UNIT_T_PER_HA;
    private static final String PLANT = GiprYieldProperties.KIND_PLANT;
    private static final String AQUA = GiprYieldProperties.KIND_AQUA;
    private static final String PROC = GiprYieldProperties.KIND_PROC;

    @BeforeAll
    static void setUp() throws Exception {
        ds = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("gipr;MODE=MySQL;DB_CLOSE_DELAY=-1")
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

        org.mybatis.spring.SqlSessionTemplate st = new org.mybatis.spring.SqlSessionTemplate(sf);
        mapper = st.getMapper(TGiprYieldRuleMapper.class);

        impl = new TGiprYieldRuleServiceImpl();
        inject(impl, "giprYieldRuleMapper", mapper);
        inject(impl, "props", new GiprYieldProperties()); // 15 亩/公顷、1000 公斤/吨、3 位、量程 999999.999
        svc = impl;
    }

    @BeforeEach
    void clean() throws Exception {
        try (java.sql.Connection c = ds.getConnection();
             java.sql.Statement s = c.createStatement()) {
            s.executeUpdate("delete from t_gipr_yield_rule");
        }
    }

    private static long seq = 1000;

    private TGiprYieldRule rule(String code, String kind, String unit,
                                int th1, int th2, int th3, int priority, int status,
                                String start, String end) {
        TGiprYieldRule r = new TGiprYieldRule();
        r.setId(++seq);
        r.setRuleCode(code);
        r.setRuleName(code + "号线");
        r.setObjKind(kind);
        r.setUnitKind(unit);
        r.setTh1Max(new BigDecimal(th1));
        r.setTh2Max(new BigDecimal(th2));
        r.setTh3Max(new BigDecimal(th3));
        r.setPriority(priority);
        r.setStatus(status);
        r.setDelFlag(0);
        try {
            r.setEffStart(start == null ? null : FMT.parse(start));
            r.setEffEnd(end == null ? null : FMT.parse(end));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        mapper.insert(r);
        return r;
    }

    private static Date at(String s) {
        try {
            return FMT.parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---------------- 先挑在用的那一版：相接瞬归后版 ----------------

    @Test
    void versionSwitch_handoverInstantBelongsToLaterVersion() {
        // 同代号两版相接：旧版 [2024-01-01, 2025-01-01)，新版 [2025-01-01, 无)
        TGiprYieldRule oldV = rule("YL-A", PLANT, KG, 10, 20, 30, 1, 0,
                "2024-01-01 00:00:00", "2025-01-01 00:00:00");
        TGiprYieldRule newV = rule("YL-A", PLANT, KG, 50, 60, 70, 1, 0,
                "2025-01-01 00:00:00", null);

        // 相接前一瞬归旧版：60 在旧版是第 4 段
        YieldVerdict before = svc.decide("YL-A", new BigDecimal("60"), at("2024-12-31 23:59:59"));
        assertTrue(before.isAvailable());
        assertEquals(4, before.getLevel());
        assertEquals(oldV.getId(), before.getRuleId());

        // 相接那一瞬归后一版：60 在新版恰压告警，第 2 段
        YieldVerdict on = svc.decide("YL-A", new BigDecimal("60"), at("2025-01-01 00:00:00"));
        assertTrue(on.isAvailable());
        assertEquals(2, on.getLevel());
        assertEquals(newV.getId(), on.getRuleId());
        assertEquals("YL-A", on.getRuleCode());
        assertEquals(newV.getEffStart(), on.getEffStart(), "钉痕的启用那一日须与当场返回字字相同");
    }

    @Test
    void retiredVersion_doesNotFillIn_andExpiredDoesNotEither() {
        // 只有一条已撤下的线在位窗口里；另一条早年到期（在位但窗口已过）
        rule("YL-OFF", PLANT, KG, 10, 20, 30, 1, 1, "2024-01-01 00:00:00", null);
        rule("YL-OLD", PLANT, KG, 10, 20, 30, 1, 0, "2020-01-01 00:00:00", "2021-01-01 00:00:00");

        YieldVerdict v = svc.decide("YL-OFF", new BigDecimal("15"), at("2025-06-01 00:00:00"));
        assertFalse(v.isAvailable(), "已撤下的不得顶上去");
        assertEquals(0, v.getLevel());
        assertEquals(YieldVerdict.REASON_NO_VERSION, v.getReason());
        assertTrue(v.getMessage().contains("给不出"));

        YieldVerdict old = svc.decide("YL-OLD", new BigDecimal("15"), at("2025-06-01 00:00:00"));
        assertFalse(old.isAvailable(), "早年到期的版本不参与求值");
        // 老形状同一口径：给不出即 0，不硬挑
        assertEquals(0, svc.evaluate("YL-OFF", new BigDecimal("15"), at("2025-06-01 00:00:00")));
    }

    @Test
    void neighborKind_isNotBorrowed() {
        // 只有水产类在位，却问种植类
        rule("YL-Q", AQUA, KG, 10, 20, 30, 1, 0, "2025-01-01 00:00:00", null);
        YieldVerdict v = svc.decideByKind(PLANT, null, new BigDecimal("15"), at("2025-06-01 00:00:00"));
        assertFalse(v.isAvailable(), "邻类品类那一条不得借来凑答复");
        assertTrue(v.getMessage().contains("种植类"));

        YieldVerdict byCode = svc.decide("YL-NOPE", new BigDecimal("15"), at("2025-06-01 00:00:00"));
        assertFalse(byCode.isAvailable(), "查无此代号也是挑不出，不硬凑一条");
    }

    @Test
    void noVersionAtAll_isLegalAnswer_notAnException() {
        YieldVerdict v = svc.decideByKind(null, null, new BigDecimal("15"), at("2025-06-01 00:00:00"));
        assertFalse(v.isAvailable());
        assertEquals(0, svc.evaluateTop(new BigDecimal("15"), at("2025-06-01 00:00:00")));
    }

    // ---------------- 几条并立听谁的：序大的先说话，序平听代号靠后的 ----------------

    @Test
    void concurrentRules_priorityFirst_codeSecond_andTwoPathsAgree() {
        // 同刻两条不同代号并立，序相同：B 阈值低、A 阈值高
        rule("YL-A", PLANT, KG, 100, 200, 300, 5, 0, "2025-01-01 00:00:00", null);
        rule("YL-B", PLANT, KG, 10, 20, 30, 5, 0, "2025-01-01 00:00:00", null);
        Date now = at("2025-06-01 00:00:00");

        // 序分不出，代号串排得靠后的 B 报数：50 在 B 已过封顶落第 4 段，在 A 落第 1 段
        YieldVerdict top = svc.decideByKind(null, null, new BigDecimal("50"), now);
        assertTrue(top.isAvailable());
        assertEquals("YL-B", top.getRuleCode());
        assertEquals(4, top.getLevel());
        // 从按线代号问那一路再问一遍：必须是同一条、同一个档
        YieldVerdict byCode = svc.decide("YL-B", new BigDecimal("50"), now);
        assertEquals(top.getRuleCode(), byCode.getRuleCode());
        assertEquals(top.getRuleId(), byCode.getRuleId());
        assertEquals(top.getLevel(), byCode.getLevel());
        // 老的通表比优入口也同答
        assertEquals(4, svc.evaluateTop(new BigDecimal("50"), now));
        // 列表次序：B 在 A 前
        List<TGiprYieldRule> avail = svc.listAvailable(now);
        assertEquals("YL-B", avail.get(0).getRuleCode());
        assertEquals(2, avail.size());
        assertEquals(2, svc.countAvailable(now));

        // 序分出高下：A 提到序 9，A 先说话
        TGiprYieldRule a = mapper.selectList(null).stream().filter(x -> "YL-A".equals(x.getRuleCode())).findFirst().get();
        a.setPriority(9);
        mapper.updateById(a);
        YieldVerdict top2 = svc.decideByKind(null, null, new BigDecimal("50"), now);
        assertEquals("YL-A", top2.getRuleCode());
        assertEquals(1, top2.getLevel());
    }

    // ---------------- 数压在界上归哪一头：等号归本段，通篇一个说法 ----------------

    @Test
    void valueExactlyOnThreshold_goesToLowerSegment() {
        rule("YL-EQ", PLANT, KG, 10, 20, 30, 1, 0, "2025-01-01 00:00:00", null);
        Date now = at("2025-06-01 00:00:00");
        assertEquals(1, svc.decide("YL-EQ", new BigDecimal("10"), now).getLevel(), "恰等于关注→第1段");
        assertEquals(2, svc.decide("YL-EQ", new BigDecimal("20"), now).getLevel(), "恰等于告警→第2段");
        assertEquals(3, svc.decide("YL-EQ", new BigDecimal("30"), now).getLevel(), "恰等于封顶→第3段");
        assertEquals(2, svc.decide("YL-EQ", new BigDecimal("10.001"), now).getLevel(), "刚过关注→第2段");
        assertEquals(4, svc.decide("YL-EQ", new BigDecimal("30.001"), now).getLevel(), "刚过封顶→第4段");
    }

    // ---------------- 负数／大得没边：不进落线，点名 ----------------

    @Test
    void negativeValue_namedByValueAndRule() {
        rule("YL-N", PLANT, KG, 10, 20, 30, 1, 0, "2025-01-01 00:00:00", null);
        FlowRuleException e = assertThrows(FlowRuleException.class,
                () -> svc.decide("YL-N", new BigDecimal("-5"), at("2025-06-01 00:00:00")));
        assertTrue(e.getMessage().contains("-5"), "要指名是哪一个数");
        assertTrue(e.getMessage().contains("负数"), "要按哪一条规矩不合");
        // 老形状对越界维持「一律 0」的收发
        assertEquals(0, svc.evaluate("YL-N", new BigDecimal("-5"), at("2025-06-01 00:00:00")));
        assertEquals(0, svc.evaluateTop(new BigDecimal("-5"), at("2025-06-01 00:00:00")));
    }

    @Test
    void absurdlyLargeValue_namedByValueAndRule() {
        rule("YL-BIG", PLANT, KG, 10, 20, 30, 1, 0, "2025-01-01 00:00:00", null);
        FlowRuleException e = assertThrows(FlowRuleException.class,
                () -> svc.decide("YL-BIG", new BigDecimal("1000000"), at("2025-06-01 00:00:00")));
        assertTrue(e.getMessage().contains("1000000"));
        assertTrue(e.getMessage().contains("大得没边") || e.getMessage().contains("量程"));
    }

    @Test
    void missingArguments_areNamedNotSwallowed() {
        FlowRuleException e1 = assertThrows(FlowRuleException.class,
                () -> svc.decide("YL-X", null, at("2025-06-01 00:00:00")));
        assertTrue(e1.getMessage().contains("实测值"));
        FlowRuleException e2 = assertThrows(FlowRuleException.class,
                () -> svc.decide("YL-X", new BigDecimal("5"), null));
        assertTrue(e2.getMessage().contains("那一刻"));
        FlowRuleException e3 = assertThrows(FlowRuleException.class,
                () -> svc.decide("  ", new BigDecimal("5"), at("2025-06-01 00:00:00")));
        assertTrue(e3.getMessage().contains("线代号"));
    }

    // ---------------- 单位先换齐再比：定数一处定死 ----------------

    @Test
    void unitsUnifiedBeforeComparing_bothFormsSameVerdict() {
        // 线面口径「每公顷吨」，关注 15 吨/公顷 == 1000 公斤/亩（15×1000÷15）
        rule("YL-U", PLANT, THA, 15, 30, 45, 1, 0, "2025-01-01 00:00:00", null);
        Date now = at("2025-06-01 00:00:00");

        // 报每亩公斤 1000 → 照线面换成 15.000 吨/公顷，恰压关注，第 1 段
        YieldVerdict inKg = impl.decide("YL-U", new BigDecimal("1000"), KG, now);
        assertEquals(1, inKg.getLevel());
        assertEquals(new BigDecimal("15.000"), inKg.getLineValue());
        assertEquals(new BigDecimal("1000.000"), inKg.getInputKgPerMu());

        // 同一物理量改按每公顷吨报 15：结论与拿去落线的数必须一致
        YieldVerdict inHa = impl.decide("YL-U", new BigDecimal("15"), THA, now);
        assertEquals(inKg.getLevel(), inHa.getLevel());
        assertEquals(inKg.getLineValue(), inHa.getLineValue());
        assertEquals(inKg.getRuleId(), inHa.getRuleId());

        // 2000 公斤/亩 = 30 吨/公顷，恰压告警
        assertEquals(2, impl.decide("YL-U", new BigDecimal("2000"), KG, now).getLevel());
        assertEquals(2, impl.decide("YL-U", new BigDecimal("30"), THA, now).getLevel());
    }

    @Test
    void unknownUnit_namedAndRejected() {
        rule("YL-U2", PLANT, KG, 10, 20, 30, 1, 0, "2025-01-01 00:00:00", null);
        FlowRuleException e = assertThrows(FlowRuleException.class,
                () -> impl.decide("YL-U2", new BigDecimal("5"), "每蒲式耳蒲", at("2025-06-01 00:00:00")));
        assertTrue(e.getMessage().contains("每蒲式耳蒲"));
        assertTrue(e.getMessage().contains("两式"));
    }

    // ---------------- 同一批并着贴两回要幂等 ----------------

    @Test
    void repeatedEvaluation_isIdempent() {
        rule("YL-ID", AQUA, KG, 10, 20, 30, 1, 0, "2025-01-01 00:00:00", null);
        Date now = at("2025-06-01 00:00:00");
        YieldVerdict a = svc.decide("YL-ID", new BigDecimal("25"), now);
        YieldVerdict b = svc.decide("YL-ID", new BigDecimal("25"), now);
        assertEquals(a.getLevel(), b.getLevel());
        assertEquals(a.getRuleId(), b.getRuleId());
        assertEquals(a.getRuleCode(), b.getRuleCode());
        assertEquals(a.getEffStart(), b.getEffStart());
        assertEquals(a.getLineValue(), b.getLineValue());
        // 纯读：库里还是只那一条，没生出第二张
        assertEquals(1, mapper.selectCount(null));
    }

    // ---------------- 老线不追溯：新时刻照新线，旧时刻照旧线 ----------------

    @Test
    void ruleChange_doesNotRetroactivelyRecompute() {
        rule("YL-R", PROC, KG, 10, 20, 30, 1, 0, "2024-01-01 00:00:00", "2025-01-01 00:00:00");
        rule("YL-R", PROC, KG, 50, 60, 70, 1, 0, "2025-01-01 00:00:00", null);
        BigDecimal sample = new BigDecimal("40");
        // 同一笔实测值，发生在旧线期归第 4 段，发生在新线期归第 1 段——各按发生那一刻的线，不回翻
        assertEquals(4, svc.decide("YL-R", sample, at("2024-06-01 00:00:00")).getLevel());
        assertEquals(1, svc.decide("YL-R", sample, at("2025-06-01 00:00:00")).getLevel());
    }

    // ---------------- 看板断线清单 + 行面照旧列得出 ----------------

    @Test
    void boardLists_codesWithNoInPositionVersion() {
        rule("YL-OK", PLANT, KG, 10, 20, 30, 1, 0, "2025-01-01 00:00:00", null);
        rule("YL-RET", AQUA, KG, 10, 20, 30, 1, 1, "2025-01-01 00:00:00", null); // 已撤下
        rule("YL-GAP", PROC, KG, 10, 20, 30, 1, 0, "2020-01-01 00:00:00", "2021-01-01 00:00:00"); // 断档
        Date now = at("2025-06-01 00:00:00");

        List<String> broken = svc.listBrokenBoardCodes(now);
        assertTrue(broken.contains("YL-RET"));
        assertTrue(broken.contains("YL-GAP"));
        assertFalse(broken.contains("YL-OK"));
        // 去重 + 代号升序
        assertEquals(java.util.Arrays.asList("YL-GAP", "YL-RET"), broken);

        // 已撤下、早年到期的版本行面照旧列得出来（只是不参与求值）
        List<TGiprYieldRule> all = svc.listVersions(null, null);
        assertEquals(3, all.size());
    }

    // ---------------- 按品类、按年度取线 ----------------

    @Test
    void decideByKindAndYear_picksThatYearsVersion() {
        rule("YL-Y", PLANT, KG, 10, 20, 30, 1, 0, "2024-05-01 00:00:00", "2025-05-01 00:00:00");
        TGiprYieldRule v2025 = rule("YL-Y", PLANT, KG, 50, 60, 70, 1, 0, "2025-05-01 00:00:00", null);

        // 按品类、按年度：实测值发生在 2024 那一版在位期，且年度指 2024，落旧线
        // 15 在旧线（10/20/30）过了关注、未过告警，第 2 段
        YieldVerdict v24 = svc.decideByKind(PLANT, 2024, new BigDecimal("15"), at("2024-06-01 00:00:00"));
        assertTrue(v24.isAvailable());
        assertEquals(2, v24.getLevel());
        // 同一刻在新版在位期：年度指 2025，落新线
        YieldVerdict v25 = svc.decideByKind(PLANT, 2025, new BigDecimal("15"), at("2025-06-01 00:00:00"));
        assertTrue(v25.isAvailable());
        assertEquals(v2025.getId(), v25.getRuleId());
        assertEquals(1, v25.getLevel());

        // 年度指 2024、却拿 2025 的时刻问：2024 版已到期，按「到期不凑、挑不出合法」，不得借旧版
        assertFalse(svc.decideByKind(PLANT, 2024, new BigDecimal("15"), at("2025-06-01 00:00:00")).isAvailable());

        // 年度列版：2025 只出新版，倒序
        List<TGiprYieldRule> rows2025 = svc.listVersions(PLANT, 2025);
        assertEquals(1, rows2025.size());
        assertEquals(v2025.getId(), rows2025.get(0).getId());
        // 没有任何版本启用的年度：挑不出，不硬凑
        assertFalse(svc.decideByKind(PLANT, 2026, new BigDecimal("15"), at("2026-06-01 00:00:00")).isAvailable());
    }

    @Test
    void usable_matchesWindowAndStatusExactly() {
        TGiprYieldRule on = rule("YL-USE", PLANT, KG, 10, 20, 30, 1, 0,
                "2025-01-01 00:00:00", "2026-01-01 00:00:00");
        TGiprYieldRule off = rule("YL-USE2", PLANT, KG, 10, 20, 30, 1, 1,
                "2025-01-01 00:00:00", null);
        Date now = at("2025-06-01 00:00:00");
        assertTrue(svc.usable(on.getId(), now));
        assertFalse(svc.usable(on.getId(), at("2026-01-01 00:00:00")), "交回那一日（不含）起不可用");
        assertFalse(svc.usable(off.getId(), now), "已撤下不可用");
        assertFalse(svc.usable(999999L, now), "库里没有不可用");
        assertFalse(svc.usable(null, now));
        assertFalse(svc.usable(on.getId(), null));
        assertNotNull(svc.selectTGiprYieldRuleById(on.getId()));
        assertNull(svc.selectTGiprYieldRuleById(999999L));
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
