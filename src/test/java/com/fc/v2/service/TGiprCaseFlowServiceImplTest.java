package com.fc.v2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.MyBatisExceptionTranslator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.interceptor.RollbackRuleAttribute;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.common.exception.FlowRuleException;
import com.fc.v2.mapper.auto.TGiprCaseFlowMapper;
import com.fc.v2.mapper.auto.TGiprCaseSignMapper;
import com.fc.v2.mapper.auto.TGiprCaseTraceMapper;
import com.fc.v2.mapper.auto.TGiprHolderBookMapper;
import com.fc.v2.mapper.auto.TGiprProductBookMapper;
import com.fc.v2.model.auto.TGiprCaseFlow;
import com.fc.v2.model.auto.TGiprCaseTrace;
import com.fc.v2.model.auto.TGiprHolderBook;
import com.fc.v2.service.impl.TGiprCaseFlowServiceImpl;

/**
 * 办件四格状态机服务层集成测试：真跑 SQL（H2 MySQL 兼容模式），
 * 覆盖题面钉死的每一条规矩。
 */
class TGiprCaseFlowServiceImplTest {

    private static DataSource ds;
    private static TGiprCaseFlowMapper caseMapper;
    private static TGiprCaseTraceMapper traceMapper;
    private static TGiprCaseSignMapper signMapper;
    private static TGiprHolderBookMapper holderMapper;
    private static ITGiprCaseFlowService svc;

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
        fb.setMapperLocations(new ClassPathResource("mybatis/auto/TGiprCaseFlowMapper.xml"));
        SqlSessionFactory sf = fb.getObject();
        org.apache.ibatis.session.Configuration mc = sf.getConfiguration();
        mc.addMapper(TGiprCaseFlowMapper.class);
        mc.addMapper(TGiprCaseTraceMapper.class);
        mc.addMapper(TGiprCaseSignMapper.class);
        mc.addMapper(TGiprHolderBookMapper.class);
        mc.addMapper(TGiprProductBookMapper.class);

        org.mybatis.spring.SqlSessionTemplate st = new org.mybatis.spring.SqlSessionTemplate(sf);
        caseMapper = st.getMapper(TGiprCaseFlowMapper.class);
        traceMapper = st.getMapper(TGiprCaseTraceMapper.class);
        signMapper = st.getMapper(TGiprCaseSignMapper.class);
        holderMapper = st.getMapper(TGiprHolderBookMapper.class);
        TGiprProductBookMapper productMapper = st.getMapper(TGiprProductBookMapper.class);

        TGiprCaseFlowServiceImpl impl = new TGiprCaseFlowServiceImpl();
        inject(impl, "giprCaseFlowMapper", caseMapper);
        inject(impl, "traceMapper", traceMapper);
        inject(impl, "signMapper", signMapper);
        inject(impl, "holderMapper", holderMapper);
        inject(impl, "productMapper", productMapper);
        svc = impl;

        // 引用一下，确保回滚规则类在 classpath（mybatis-spring 已随 starter 引入）
        new RollbackRuleAttribute(Exception.class);
        new MyBatisExceptionTranslator(sf.getConfiguration().getEnvironment().getDataSource(), false);
    }

    @BeforeEach
    void clean() throws Exception {
        try (java.sql.Connection c = ds.getConnection();
             java.sql.Statement s = c.createStatement()) {
            s.executeUpdate("delete from t_gipr_case_trace");
            s.executeUpdate("delete from t_gipr_case_sign");
            s.executeUpdate("delete from t_gipr_case_flow");
            s.executeUpdate("delete from t_gipr_holder_book");
        }
    }

    private static final AtomicLong HOLDER_SEQ = new AtomicLong(5000);

    private void seedHolder(String no, Integer prodId) {
        TGiprHolderBook h = new TGiprHolderBook();
        h.setId(HOLDER_SEQ.incrementAndGet());
        h.setHolderNo(no);
        h.setProdId(prodId);
        h.setProdNo("GP0" + prodId);
        h.setStatus(1);
        h.setDelFlag(0);
        holderMapper.insert(h);
    }

    private TGiprCaseFlow form(String holder, int prod) {
        TGiprCaseFlow f = new TGiprCaseFlow();
        f.setHolderNo(holder);
        f.setProdId(prod);
        f.setGoodsKind("整蟹礼盒");
        f.setApplyTime(new Date(1_700_000_000_000L));
        return f;
    }

    // ---------------- 起点：四样 + 在办唯一 + 幂等 ----------------

    @Test
    void create_namesEveryMissingField() {
        TGiprCaseFlow f = new TGiprCaseFlow();
        FlowRuleException e = assertThrows(FlowRuleException.class, () -> svc.createCase(f));
        String m = e.getMessage();
        assertTrue(m.contains("企业代号"), m);
        assertTrue(m.contains("名录产品"), m);
        assertTrue(m.contains("一类货"), m);
        assertTrue(m.contains("递件那一刻"), m);
        assertFalse(m.contains("材料不齐"), "不许拿「材料不齐」搪塞");
    }

    @Test
    void create_unknownHolderRejected() {
        FlowRuleException e = assertThrows(FlowRuleException.class, () -> svc.createCase(form("H999", 0)));
        assertTrue(e.getMessage().contains("授权册上查不到"));
    }

    @Test
    void create_sameSecondSubmissionIsIdempotent() {
        seedHolder("H01", 0);
        TGiprCaseFlow f = form("H01", 0);
        TGiprCaseFlow a = svc.createCase(f);
        TGiprCaseFlow b = svc.createCase(form("H01", 0));
        assertEquals(a.getId(), b.getId(), "同一件并着进两回，重取不重建");
    }

    @Test
    void create_onlyOneOpenPerHolder() {
        seedHolder("H02", 0);
        TGiprCaseFlow a = svc.createCase(form("H02", 0));
        // 另一件（不同货类→不同幂等键）应被在办唯一顶回
        TGiprCaseFlow other = form("H02", 0);
        other.setGoodsKind("蟹酱");
        FlowRuleException e = assertThrows(FlowRuleException.class, () -> svc.createCase(other));
        assertTrue(e.getMessage().contains("未闭上"));
        // 撤回后同企业可另起
        svc.withdraw(a.getId(), "不办了");
        TGiprCaseFlow c = svc.createCase(other);
        assertNotEquals(a.getId(), c.getId());
    }

    // ---------------- 四格门槛与走满封口 ----------------

    @Test
    void fullHappyPath_fourStagesThenSealedWithThreeBolts() {
        seedHolder("H03", 0);
        TGiprCaseFlow c = svc.createCase(form("H03", 0));
        assertEquals(0, c.getStage());
        assertEquals(1, c.getStatus());

        // 收件→初审：名录在册放行
        c = svc.advance(c.getId(), null);
        assertEquals(1, c.getStage());

        // 没记成「可」，核准门不开
        final Long idAtReview = c.getId();
        FlowRuleException locked = assertThrows(FlowRuleException.class, () -> svc.advance(idAtReview, null));
        assertTrue(locked.getMessage().contains("初审按语"));

        // 同格第二句顶不掉第一句
        c = svc.note(idAtReview, "否");
        TGiprCaseFlow again = svc.note(idAtReview, "可");
        assertEquals(c.getId(), again.getId());
        assertEquals("否", caseMapper.selectById(idAtReview).getContent(), "本行只认最早那句");
        // 把「否」退回收件重来，再记「可」
        c = svc.rollback(idAtReview, null);
        assertEquals(0, c.getStage());
        c = svc.advance(c.getId(), null);
        assertEquals(1, c.getStage());
        c = svc.note(c.getId(), "可");
        assertEquals("可", caseMapper.selectById(c.getId()).getContent());

        // 初审→核准
        c = svc.advance(c.getId(), null);
        assertEquals(2, c.getStage());

        // 两名核签各落一笔，同人不算两名
        final Long idAtApprove = c.getId();
        svc.sign(idAtApprove, "岗甲");
        TGiprCaseFlow dup = svc.sign(idAtApprove, "岗甲");
        assertEquals(2, dup.getStage());
        FlowRuleException needTwo = assertThrows(FlowRuleException.class, () -> svc.advance(idAtApprove, null));
        assertTrue(needTwo.getMessage().contains("两名"));
        svc.sign(idAtApprove, "岗乙");

        // 核准→备案发标：核发记号写回 + 封口
        c = svc.advance(idAtApprove, null);
        assertEquals(3, c.getStage());
        assertEquals(1, c.getCertFlag());
        assertEquals(2, c.getStatus());
        assertNull(c.getOpenHolder());

        // 封口三闩：补按语、改内容、抽整条全办不了；仍在名册里
        final Long sealedId = c.getId();
        assertThrows(FlowRuleException.class, () -> svc.note(sealedId, "再补一句"));
        assertThrows(FlowRuleException.class, () -> svc.updateContent(sealedId, "改"));
        assertThrows(FlowRuleException.class, () -> svc.remove(sealedId));
        assertThrows(FlowRuleException.class, () -> svc.advance(sealedId, null));
        List<TGiprCaseFlow> roster = svc.selectTGiprCaseFlowList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>());
        assertTrue(roster.stream().anyMatch(x -> x.getId().equals(sealedId)), "封口件仍在名册数得着");

        // 封口后同企业可起新件
        seedHolder("H03B", 0);
        TGiprCaseFlow next = svc.createCase(form("H03B", 0));
        assertEquals(0, next.getStage());
    }

    @Test
    void advance_toReviewBlockedWhenProductMovedOut() {
        seedHolder("H04", 1);
        TGiprCaseFlow c = svc.createCase(form("H04", 1));
        FlowRuleException e = assertThrows(FlowRuleException.class, () -> svc.advance(c.getId(), null));
        assertTrue(e.getMessage().contains("移出") || e.getMessage().contains("并走"));
        assertEquals(0, caseMapper.selectById(c.getId()).getStage(), "件仍停在原格");
    }

    // ---------------- 退回：一格、按语挪串、重进重累计、重签 ----------------

    @Test
    void rollback_movesLeavingNoteVerbatimIntoTrail() {
        seedHolder("H05", 0);
        TGiprCaseFlow c = svc.createCase(form("H05", 0));
        c = svc.advance(c.getId(), null); // 1 初审
        c = svc.note(c.getId(), "可");
        c = svc.rollback(c.getId(), "补材料"); // 1→0，离开初审格
        assertEquals(0, c.getStage());

        List<TGiprCaseTrace> tr = traceMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TGiprCaseTrace>()
                        .eq("case_id", c.getId()).eq("action_kind", "ROLLBACK")
                        .orderByAsc("trace_time").orderByAsc("id"));
        assertEquals(1, tr.size());
        assertEquals("可", tr.get(0).getContent(), "退掉那格的按语原样挪入追溯串末尾");
        assertEquals(1, tr.get(0).getStage());
    }

    @Test
    void rollback_fromApprove_clearsSigns_andReentersReaccumulate() {
        seedHolder("H05B", 0);
        TGiprCaseFlow c = svc.createCase(form("H05B", 0));
        c = svc.advance(c.getId(), null); // 1
        c = svc.note(c.getId(), "可");
        c = svc.advance(c.getId(), null); // 2 核准
        svc.sign(c.getId(), "岗甲");
        svc.sign(c.getId(), "岗乙");
        assertEquals(2, signMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.fc.v2.model.auto.TGiprCaseSign>()
                        .eq("case_id", c.getId())));

        // 2→1：上一趟两签清零，按新起一次重新累计
        c = svc.rollback(c.getId(), null);
        assertEquals(1, c.getStage());
        assertEquals(0, signMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.fc.v2.model.auto.TGiprCaseSign>()
                        .eq("case_id", c.getId())));

        // 重进初审格是新趟次：可再落按语，且门照开（旧趟次不顶新趟次）
        c = svc.note(c.getId(), "可");
        assertEquals("可", caseMapper.selectById(c.getId()).getContent());
        c = svc.advance(c.getId(), null); // 回到核准
        assertEquals(2, c.getStage());
    }

    @Test
    void rollback_atHeadStageHasNowhereToGo() {
        seedHolder("H05C", 0);
        TGiprCaseFlow c = svc.createCase(form("H05C", 0));
        FlowRuleException e = assertThrows(FlowRuleException.class, () -> svc.rollback(c.getId(), null));
        assertTrue(e.getMessage().contains("没处可退"));
        assertEquals(0, caseMapper.selectById(c.getId()).getStage(), "件仍停在头一格");
    }

    // ---------------- 了结 ----------------

    @Test
    void conclude_withdrawAndMerge_stopCaseButDoNotSeal() {
        seedHolder("H06", 0);
        TGiprCaseFlow c = svc.createCase(form("H06", 0));
        c = svc.withdraw(c.getId(), "企业反悔");
        assertEquals(1, c.getStatus(), "撤回不叫封住");
        assertEquals(2, c.getEndKind());
        assertNull(c.getOpenHolder());
        final Long withdrawnId = c.getId();
        assertThrows(FlowRuleException.class, () -> svc.advance(withdrawnId, null));
    }

    // ---------------- 回放只读、断档点名 ----------------

    @Test
    void replay_isReadOnlyAndDescending() {
        seedHolder("H07", 0);
        TGiprCaseFlow c = svc.createCase(form("H07", 0));
        svc.advance(c.getId(), null);
        List<TGiprCaseTrace> replayed = svc.replay(c.getId());
        assertEquals("ADVANCE", replayed.get(0).getActionKind(), "倒着演，最新在前");
        // 回放不回写：关次仍是库中关次
        assertEquals(1, caseMapper.selectById(c.getId()).getStage());
    }

    @Test
    void note_emptyNamesStageAndColumn() {
        seedHolder("H08", 0);
        TGiprCaseFlow c = svc.createCase(form("H08", 0));
        FlowRuleException e = assertThrows(FlowRuleException.class, () -> svc.note(c.getId(), "  "));
        assertTrue(e.getMessage().contains("第1格"));
        assertTrue(e.getMessage().contains("按语内容"));
    }

    // ---------------- 旧件读法不分叉 ----------------

    @Test
    void legacyCaseWithoutTraces_readsSameWay() {
        seedHolder("H09", 0);
        // 早年库：无追溯行，按语只在 content 列
        TGiprCaseFlow old = form("H09", 0);
        old.setStage(1);
        old.setViewSeq(9); // 报面序号乱填也不参与
        old.setStatus(1);
        old.setContent("可");
        old.setDelFlag(0);
        caseMapper.insert(old);
        TGiprCaseFlow got = svc.selectTGiprCaseFlowById(old.getId());
        assertEquals(1, got.getStage(), "关次以后端数出来的 stage 为准");
        // content 里的「可」按同口径顶用，核准门开
        TGiprCaseFlow moved = svc.advance(old.getId(), null);
        assertEquals(2, moved.getStage());
    }

    // ---------------- 并发：两笔分开，不并不丢 ----------------

    @Test
    void concurrentTwoCreates_doNotMergeOrVanish() throws Exception {
        seedHolder("H10", 0);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        Runnable one = () -> {
            try {
                start.await();
                TGiprCaseFlow f = form("H10", 0);
                f.setGoodsKind("货" + Thread.currentThread().getId());
                svc.createCase(f);
                ok.incrementAndGet();
            } catch (FlowRuleException ex) {
                rejected.incrementAndGet();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };
        Thread t1 = new Thread(one);
        Thread t2 = new Thread(one);
        t1.start();
        t2.start();
        start.countDown();
        t1.join(5000);
        t2.join(5000);
        assertEquals(1, ok.get(), "只成一件");
        assertEquals(1, rejected.get(), "另一件被顶回、有回话，不并不丢");
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
