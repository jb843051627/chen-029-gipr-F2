package com.fc.v2.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.common.exception.FlowRuleException;
import com.fc.v2.mapper.auto.TGiprCaseFlowMapper;
import com.fc.v2.mapper.auto.TGiprCaseSignMapper;
import com.fc.v2.mapper.auto.TGiprCaseTraceMapper;
import com.fc.v2.mapper.auto.TGiprHolderBookMapper;
import com.fc.v2.mapper.auto.TGiprProductBookMapper;
import com.fc.v2.model.auto.TGiprCaseFlow;
import com.fc.v2.model.auto.TGiprCaseSign;
import com.fc.v2.model.auto.TGiprCaseTrace;
import com.fc.v2.model.auto.TGiprHolderBook;
import com.fc.v2.model.auto.TGiprProductBook;
import com.fc.v2.service.ITGiprCaseFlowService;
import com.fc.v2.util.SnowflakeIdWorker;
import com.fc.v2.util.StringUtils;

/**
 * 专用标志使用核准办件流转单 Service业务层处理（state-machine 形状：单据流转）
 *
 * 四格钉死、没有近路：
 * 0 申请受理（县里收件）→ 1 材料初审（市里初审）→ 2 标志核准（省局核准）→ 3 备案发标。
 * 件站在第几格一律由后端按 stage 数；报面 view_seq 只给人眼对照，任何动作不读它。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TGiprCaseFlowServiceImpl implements ITGiprCaseFlowService {

    /** 四格关次 */
    private static final int STAGE_ACCEPT = 0;
    private static final int STAGE_REVIEW = 1;
    private static final int STAGE_APPROVE = 2;
    private static final int STAGE_ISSUE = 3;
    private static final int MAX_STAGE = 3;

    /** 单子情形 0未起 1在办 2已封住 */
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_SEALED = 2;

    /** 了结类别 0未完 1走满末格封口 2递件方撤回 3名录产品整段并走 */
    private static final int END_NONE = 0;
    private static final int END_SEAL = 1;
    private static final int END_WITHDRAW = 2;
    private static final int END_MERGE = 3;

    /** 名录情形 0在册 1已移出 */
    private static final int PRODUCT_IN = 0;

    /** 末关两名核签岗 */
    private static final int NEED_SIGNERS = 2;

    /** 同企业并发起件排队等锁秒数（排不上就退回去，不并、不丢） */
    private static final int LOCK_WAIT_SECONDS = 10;

    private static final String[] STAGE_NAMES = {"申请受理（县里收件）", "材料初审（市里初审）", "标志核准（省局核准）", "备案发标"};

    @javax.annotation.Resource
    private TGiprCaseFlowMapper giprCaseFlowMapper;
    @javax.annotation.Resource
    private TGiprCaseTraceMapper traceMapper;
    @javax.annotation.Resource
    private TGiprCaseSignMapper signMapper;
    @javax.annotation.Resource
    private TGiprHolderBookMapper holderMapper;
    @javax.annotation.Resource
    private TGiprProductBookMapper productMapper;

    @Override
    public TGiprCaseFlow selectTGiprCaseFlowById(Long id) {
        return normalize(giprCaseFlowMapper.selectById(id));
    }

    @Override
    public List<TGiprCaseFlow> selectTGiprCaseFlowList(QueryWrapper<TGiprCaseFlow> queryWrapper) {
        queryWrapper.eq("del_flag", 0);
        List<TGiprCaseFlow> list = giprCaseFlowMapper.selectList(queryWrapper);
        for (TGiprCaseFlow r : list) {
            normalize(r);
        }
        return list;
    }

    /**
     * 起一件。四样对上才起得来，缺一样点一样的名，不拿「材料不齐」搪塞。
     * 同一企业手上未闭上的件至多一条；同一件并着进两回（幂等键相同）重取原件，不重建。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TGiprCaseFlow createCase(TGiprCaseFlow form) {
        if (form == null) {
            throw new FlowRuleException("起件四样全空：缺「企业代号」「名录产品」「拟贴货类」「递件时刻」这四栏");
        }

        // 一、四样逐样点名
        List<String> missing = new ArrayList<>();
        if (StringUtils.isEmpty(form.getHolderNo())) {
            missing.add("企业代号（授权册上那一个代号）");
        }
        if (form.getProdId() == null) {
            missing.add("挂的名录产品");
        }
        if (StringUtils.isEmpty(form.getGoodsKind())) {
            missing.add("拟贴在哪一类货上");
        }
        if (form.getApplyTime() == null) {
            missing.add("递件那一刻");
        }
        if (!missing.isEmpty()) {
            throw new FlowRuleException("起件起不来，少了：" + String.join("、", missing));
        }

        String holderNo = form.getHolderNo().trim();
        String lockName = "gipr:case:create:" + holderNo;
        Integer locked = giprCaseFlowMapper.selectGetLock(lockName, LOCK_WAIT_SECONDS);
        if (locked == null || locked != 1) {
            throw new FlowRuleException("同一刻有另一件正以该企业名义递件，排队没排上，请稍后再递，这一笔未并、未丢");
        }
        try {
            return doCreateCase(form, holderNo);
        } finally {
            giprCaseFlowMapper.selectReleaseLock(lockName);
        }
    }

    private TGiprCaseFlow doCreateCase(TGiprCaseFlow form, String holderNo) {
        // 二、代号须真在册子上
        TGiprHolderBook holder = holderMapper.selectOne(new QueryWrapper<TGiprHolderBook>()
                .eq("holder_no", holderNo).eq("del_flag", 0).last("limit 1"));
        if (holder == null) {
            throw new FlowRuleException("起件起不来：授权册上查不到代号「" + holderNo + "」这家企业");
        }
        TGiprProductBook product = productMapper.selectById(form.getProdId());
        if (product == null || (product.getDelFlag() != null && product.getDelFlag() != 0)) {
            throw new FlowRuleException("起件起不来：名录底册里查不到第 " + form.getProdId() + " 号名录产品");
        }

        // 三、幂等先认：同一件并着进两回（幂等键相同）重取原件，不重建、不报「在办」
        String idemKey = StringUtils.isNotEmpty(form.getIdemKey()) ? form.getIdemKey().trim() : buildIdemKey(form, holderNo);
        TGiprCaseFlow existing = giprCaseFlowMapper.selectOne(new QueryWrapper<TGiprCaseFlow>()
                .eq("idem_key", idemKey).eq("del_flag", 0).last("limit 1"));
        if (existing != null) {
            return normalize(existing);
        }

        // 四、同一家企业手上没闭上的件至多一条
        List<TGiprCaseFlow> openList = giprCaseFlowMapper.selectList(new QueryWrapper<TGiprCaseFlow>()
                .eq("holder_no", holderNo)
                .eq("del_flag", 0)
                .eq("status", STATUS_ACTIVE)
                .and(w -> w.eq("end_kind", END_NONE).or().isNull("end_kind"))
                .last("limit 1"));
        if (!openList.isEmpty()) {
            TGiprCaseFlow open = normalize(openList.get(0));
            throw new FlowRuleException("该企业手上已有一条未闭上的件（件次 " + open.getBizNo()
                    + "，停在第" + (open.getStage() + 1) + "格「" + STAGE_NAMES[open.getStage()]
                    + "」），这一条没闭上，下一条起不来");
        }

        Date now = new Date();
        TGiprCaseFlow r = new TGiprCaseFlow();
        r.setBizNo(StringUtils.isNotEmpty(form.getBizNo()) ? form.getBizNo().trim()
                : "GIPR" + SnowflakeIdWorker.getUUID());
        r.setHolderNo(holderNo);
        r.setProdId(form.getProdId());
        r.setGoodsKind(form.getGoodsKind().trim());
        r.setApplyTime(form.getApplyTime());
        r.setIdemKey(idemKey);
        r.setStage(STAGE_ACCEPT);
        r.setViewSeq(form.getViewSeq() == null ? 1 : form.getViewSeq());
        r.setAttemptNo(1);
        r.setCertFlag(0);
        r.setEndKind(END_NONE);
        r.setOpenHolder(holderNo);
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction("START");
        r.setDelFlag(0);
        try {
            giprCaseFlowMapper.insert(r);
        } catch (DuplicateKeyException e) {
            // 同一刻两路并递：先按幂等键取回第一笔；取不到说明撞的是「同企业在办唯一」
            TGiprCaseFlow winner = giprCaseFlowMapper.selectOne(new QueryWrapper<TGiprCaseFlow>()
                    .eq("idem_key", idemKey).last("limit 1"));
            if (winner != null) {
                return normalize(winner);
            }
            throw new FlowRuleException("该企业手上已有一条未闭上的件，下一条起不来（并发那一笔已被在办唯一约束顶回，未并、未丢）");
        }

        appendTrace(r.getId(), STAGE_ACCEPT, 1, "START",
                StringUtils.isNotEmpty(form.getRemark()) ? form.getRemark() : "县里收件起件", now);
        return normalize(r);
    }

    /**
     * 往后挪一格。一次只准一格，跨两格不认；各格门槛按关次逐项验。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TGiprCaseFlow advance(Long id, String remark) {
        TGiprCaseFlow r = lockCase(id);
        int stage = r.getStage();
        if (stage >= MAX_STAGE) {
            throw new FlowRuleException("件已站在末一格「" + STAGE_NAMES[MAX_STAGE] + "」，前头没有格子了");
        }

        int target = stage + 1; // 钉死一次一格，谁递跨两格都按 +1 走，报面序号不参与

        if (target == STAGE_REVIEW) {
            // 门槛①：企业在授权册上还算不算挂着名；名录已移出/并走的，初审这格进不去
            TGiprHolderBook holder = holderMapper.selectOne(new QueryWrapper<TGiprHolderBook>()
                    .eq("holder_no", r.getHolderNo()).eq("del_flag", 0).last("limit 1"));
            if (holder == null) {
                throw new FlowRuleException("材料初审这一格进不去：这家企业在授权册上已经查不到挂名");
            }
            TGiprProductBook product = productMapper.selectById(r.getProdId());
            if (product == null || (product.getDelFlag() != null && product.getDelFlag() != 0)) {
                throw new FlowRuleException("材料初审这一格进不去：挂靠的名录产品在名录底册里整段没了");
            }
            if (product.getStatus() != null && product.getStatus() != PRODUCT_IN) {
                throw new FlowRuleException("材料初审这一格进不去：挂靠的名录产品「" + product.getProdName()
                        + "」已经移出/并走");
            }
        } else if (target == STAGE_APPROVE) {
            // 门槛②：初审那句按语须记成「可」，且只认本趟最早那一句
            TGiprCaseTrace reviewNote = earliestNote(r, STAGE_REVIEW);
            if (reviewNote == null) {
                throw new FlowRuleException("标志核准这道门不开：断在第2格「材料初审」，缺的是「初审按语」栏，须记成「可」");
            }
            if (!"可".equals(reviewNote.getContent() == null ? "" : reviewNote.getContent().trim())) {
                throw new FlowRuleException("标志核准这道门不开：第2格「材料初审」最早那句按语记的是「"
                        + reviewNote.getContent() + "」，不是「可」");
            }
        } else if (target == STAGE_ISSUE) {
            // 门槛③：两名核签岗各落一笔（同人只算一名），同时把核发记号写回本行——两样齐才算走完
            long signerCount = signMapper.selectCount(new QueryWrapper<TGiprCaseSign>()
                    .eq("case_id", r.getId()).eq("del_flag", 0));
            if (signerCount < NEED_SIGNERS) {
                throw new FlowRuleException("备案发标走不出去：两名核签岗只到了 " + signerCount
                        + " 笔，须两名各落一笔（缺 " + (NEED_SIGNERS - signerCount) + " 笔）");
            }
        }

        Date now = new Date();
        int targetAttempt = visitCount(r.getId(), target) + 1;
        r.setStage(target);
        r.setAttemptNo(targetAttempt);
        r.setStatus(STATUS_ACTIVE);
        r.setEndKind(END_NONE);
        r.setLastAction("ADVANCE");
        r.setContent(null);
        updateCase(r.getId(), new UpdateWrapper<TGiprCaseFlow>()
                .set("stage", target)
                .set("attempt_no", targetAttempt)
                .set("status", STATUS_ACTIVE)
                .set("end_kind", END_NONE)
                .set("last_action", "ADVANCE")
                .set("content", null));
        appendTrace(r.getId(), target, targetAttempt, "ADVANCE",
                StringUtils.isNotEmpty(remark) ? remark : ("挪入第" + (target + 1) + "格"), now);

        if (target == STAGE_ISSUE) {
            // 两签已齐，核发记号同笔写回，走满末格即封口
            r.setCertFlag(1);
            r.setStatus(STATUS_SEALED);
            r.setEndKind(END_SEAL);
            r.setLastAction("CLOSE");
            r.setIdemKey(null); // 封了口的件不再占幂等键，往后同企业另起不受顶
            r.setOpenHolder(null); // 封口的件腾出在办占位，但仍在名册里数得着
            updateCase(r.getId(), new UpdateWrapper<TGiprCaseFlow>()
                    .set("cert_flag", 1)
                    .set("status", STATUS_SEALED)
                    .set("end_kind", END_SEAL)
                    .set("last_action", "CLOSE")
                    .set("idem_key", null)
                    .set("open_holder", null));
            appendTrace(r.getId(), STAGE_ISSUE, targetAttempt, "ISSUE", "标志核发记号写回本行，备案发标", now);
            appendTrace(r.getId(), STAGE_ISSUE, targetAttempt, "CLOSE", "走满末格，此件封住", now);
        }
        return normalize(r);
    }

    /**
     * 往回收一格：一次只退一格；头一格没处可退。
     * 退掉那格本趟最早那句按语原样挪进追溯串末尾；再走回该格按新起一趟重新累计。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TGiprCaseFlow rollback(Long id, String remark) {
        TGiprCaseFlow r = lockCase(id);
        int stage = r.getStage();
        if (stage == STAGE_ACCEPT) {
            throw new FlowRuleException("件本就站在头一格「申请受理」，再退没处可退，件仍停在原格");
        }

        Date now = new Date();
        // 退掉那格的按语原样挪到追溯串末尾（ROLLBACK 行挂在离开格名下）
        TGiprCaseTrace leavingNote = earliestNote(r, stage);
        String movedContent = leavingNote == null ? "" : leavingNote.getContent();
        appendTrace(r.getId(), stage, r.getAttemptNo(), "ROLLBACK",
                movedContent, now, "退回第" + stage + "格，该格按语原样挪入追溯串末尾"
                        + (StringUtils.isNotEmpty(remark) ? "；" + remark : ""));

        int back = stage - 1;
        // 落入下一格即开启该格新一轮：轮次=该格历次落格（起件/推进/退回落入）+1
        int backAttempt = visitCount(r.getId(), back) + 1;
        appendTrace(r.getId(), back, backAttempt, "RETURN",
                "退回落入第" + (back + 1) + "格，按新起一次重新累计", now);
        // 从核准关退回：末关那两名核签是上一趟攒的，按「再走回重新累计」清掉计数；
        // 谁签过仍留在追溯串 SIGN 行里，不丢痕。
        if (stage == STAGE_APPROVE) {
            signMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TGiprCaseSign>()
                    .eq("case_id", r.getId()));
        }
        r.setStage(back);
        r.setAttemptNo(backAttempt);
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction("ROLLBACK");
        r.setContent(null);
        updateCase(r.getId(), new UpdateWrapper<TGiprCaseFlow>()
                .set("stage", back)
                .set("attempt_no", backAttempt)
                .set("status", STATUS_ACTIVE)
                .set("last_action", "ROLLBACK")
                .set("content", null));
        return normalize(r);
    }

    /**
     * 当前格落一句按语：同一格同一趟只认最早那一句，后写顶不掉、不另存（幂等 no-op）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TGiprCaseFlow note(Long id, String remark) {
        TGiprCaseFlow r = lockCase(id);
        if (StringUtils.isEmpty(remark)) {
            throw new FlowRuleException("按语落不下去：断在第" + (r.getStage() + 1) + "格「" + STAGE_NAMES[r.getStage()]
                    + "」，缺的是「按语内容」这一栏");
        }
        TGiprCaseTrace first = earliestNote(r, r.getStage());
        if (first != null) {
            // 本行只认最早那句：原样返回，不顶不另存
            return normalize(r);
        }
        Date now = new Date();
        appendTrace(r.getId(), r.getStage(), r.getAttemptNo(), "NOTE", remark.trim(), now);
        r.setContent(remark.trim());
        r.setLastAction("NOTE");
        updateCase(r.getId(), new UpdateWrapper<TGiprCaseFlow>()
                .set("content", remark.trim())
                .set("last_action", "NOTE"));
        return normalize(r);
    }

    /**
     * 备案发标前的核签岗落笔：两名须各落一笔；同一岗重落笔只算一名（幂等 no-op）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TGiprCaseFlow sign(Long id, String signerNo) {
        TGiprCaseFlow r = lockCase(id);
        if (StringUtils.isEmpty(signerNo)) {
            throw new FlowRuleException("核签落不了笔：断在第" + (r.getStage() + 1) + "格，缺的是「核签岗代号」这一栏");
        }
        if (r.getStage() != STAGE_APPROVE) {
            throw new FlowRuleException("核签只在「标志核准」之后、「备案发标」之前收：件此刻停在第"
                    + (r.getStage() + 1) + "格「" + STAGE_NAMES[r.getStage()] + "」，这笔不认");
        }
        String signer = signerNo.trim();
        TGiprCaseSign exist = signMapper.selectOne(new QueryWrapper<TGiprCaseSign>()
                .eq("case_id", r.getId()).eq("signer_no", signer).eq("del_flag", 0).last("limit 1"));
        if (exist != null) {
            return normalize(r); // 同一岗已落过笔，不重计、不另存
        }

        Date now = new Date();
        TGiprCaseSign row = new TGiprCaseSign();
        row.setCaseId(r.getId());
        row.setSignerNo(signer);
        row.setSignTime(now);
        row.setDelFlag(0);
        try {
            signMapper.insert(row);
        } catch (DuplicateKeyException e) {
            return normalize(r); // 同一刻同岗两笔，唯一键顶回一笔
        }
        appendTrace(r.getId(), STAGE_APPROVE, r.getAttemptNo(), "SIGN", "核签岗「" + signer + "」落笔", now);
        r.setLastAction("SIGN");
        updateCase(r.getId(), new UpdateWrapper<TGiprCaseFlow>().set("last_action", "SIGN"));
        return normalize(r);
    }

    /** 递件一方自己反悔把话取了回：就此了结，不必再往前（停在原格、不封口）。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TGiprCaseFlow withdraw(Long id, String remark) {
        return conclude(id, END_WITHDRAW, "CANCEL", "递件方撤回", remark);
    }

    /** 挂靠的名录产品整段并走：就此了结，不必再往前。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TGiprCaseFlow mergeEnd(Long id, String remark) {
        return conclude(id, END_MERGE, "MERGE", "名录产品整段并走", remark);
    }

    /**
     * 倒着看一遍：从末一格往回一格一格演，按落字时刻倒序出结果，全程只读、不回写。
     */
    @Override
    public List<TGiprCaseTrace> replay(Long id) {
        TGiprCaseFlow r = giprCaseFlowMapper.selectById(id);
        if (r == null || (r.getDelFlag() != null && r.getDelFlag() != 0)) {
            throw new FlowRuleException("件不存在，无从回放");
        }
        List<TGiprCaseTrace> traces = traceMapper.selectList(new QueryWrapper<TGiprCaseTrace>()
                .eq("case_id", id).eq("del_flag", 0)
                .orderByDesc("trace_time").orderByDesc("id"));
        // 只出结果、不回写：这里不碰任何 update/insert
        return Collections.unmodifiableList(traces);
    }

    /** 修改内容：已封住/已了结的件一个字都改不得（封口三道闩之二）。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateContent(Long id, String remark) {
        TGiprCaseFlow r = lockCase(id);
        return updateCase(r.getId(), new UpdateWrapper<TGiprCaseFlow>()
                .set("content", remark)
                .set("last_action", "EDIT")) > 0;
    }

    /** 抽走整条：已封住的件抽不走（封口三道闩之三）；未封口按删除标记落痕，不物理抹。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        TGiprCaseFlow r = lockCase(id);
        return updateCase(r.getId(), new UpdateWrapper<TGiprCaseFlow>()
                .set("del_flag", 1)
                .set("open_holder", null)
                .set("last_action", "REMOVE")) > 0;
    }

    /** 显式按主键更新（UpdateWrapper.set 能把列清空，updateById 默认会吞掉 null）。 */
    private int updateCase(Long id, UpdateWrapper<TGiprCaseFlow> uw) {
        uw.eq("id", id);
        return giprCaseFlowMapper.update(null, uw);
    }

    // ------------------------------------------------------------------

    private TGiprCaseFlow conclude(Long id, int endKind, String actionKind, String actionText, String remark) {
        TGiprCaseFlow r = lockCase(id);
        Date now = new Date();
        r.setEndKind(endKind);
        r.setStatus(STATUS_ACTIVE); // 了结但不叫「封住」：封口只给走满末格那一类
        r.setLastAction(actionKind);
        r.setIdemKey(null);
        r.setOpenHolder(null); // 了结的件腾出在办占位，同企业可另起
        updateCase(r.getId(), new UpdateWrapper<TGiprCaseFlow>()
                .set("end_kind", endKind)
                .set("status", STATUS_ACTIVE)
                .set("last_action", actionKind)
                .set("idem_key", null)
                .set("open_holder", null));
        appendTrace(r.getId(), r.getStage(), r.getAttemptNo(), actionKind,
                actionText + (StringUtils.isNotEmpty(remark) ? "：" + remark : ""), now);
        return normalize(r);
    }

    /** 取件并上行锁（同一条件的动作排队，后到的不被挤掉）；顺手把所有封口/了结闩落齐。 */
    private TGiprCaseFlow lockCase(Long id) {
        if (id == null) {
            throw new FlowRuleException("缺件号，这一笔系统不认");
        }
        TGiprCaseFlow r = giprCaseFlowMapper.selectByIdForUpdate(id);
        if (r == null || (r.getDelFlag() != null && r.getDelFlag() != 0)) {
            throw new FlowRuleException("件不存在或已抽走，这一笔系统不认，件仍停在原处");
        }
        normalize(r);
        if (r.getStatus() != null && r.getStatus() == STATUS_SEALED) {
            throw new FlowRuleException("此件已走满末格封住：按语补不得、内容改不得、整条抽不得，这一笔办不了");
        }
        if (r.getEndKind() != null && r.getEndKind() != END_NONE) {
            String why = r.getEndKind() == END_WITHDRAW ? "递件方已撤回" : "挂靠名录已整段并走";
            throw new FlowRuleException("此件已因「" + why + "」了结，停在第" + (r.getStage() + 1)
                    + "格，不再往前挪，这一笔办不了");
        }
        return r;
    }

    /** 本格本趟最早落的那句按语（同格重进后按新趟次另算）。 */
    private TGiprCaseTrace earliestNote(TGiprCaseFlow r, int stage) {
        List<TGiprCaseTrace> list = traceMapper.selectList(new QueryWrapper<TGiprCaseTrace>()
                .eq("case_id", r.getId())
                .eq("stage", stage)
                .eq("attempt_no", r.getAttemptNo() == null ? 1 : r.getAttemptNo())
                .eq("action_kind", "NOTE")
                .eq("del_flag", 0)
                .orderByAsc("trace_time").orderByAsc("id").last("limit 1"));
        if (!list.isEmpty()) {
            return list.get(0);
        }
        // 早年立下的件没有追溯行，按语只在 content 列：读法不另立一套，按同口径回退取它
        if (stage == r.getStage() && StringUtils.isNotEmpty(r.getContent())) {
            TGiprCaseTrace legacy = new TGiprCaseTrace();
            legacy.setCaseId(r.getId());
            legacy.setStage(stage);
            legacy.setAttemptNo(r.getAttemptNo());
            legacy.setActionKind("NOTE");
            legacy.setContent(r.getContent());
            legacy.setTraceTime(r.getUpdateTime() != null ? r.getUpdateTime() : r.getCreateTime());
            return legacy;
        }
        return null;
    }

    /**
     * 该格此前已落过几回：起件(START)、推进落入(ADVANCE)、退回落入(RETURN) 都算一次停留。
     * 再走回这一格时 = 已有次数 + 1，按新起一次重新累计，旧趟次的按语不顶新趟次。
     */
    private int visitCount(Long caseId, int stage) {
        Integer cnt = traceMapper.selectCount(new QueryWrapper<TGiprCaseTrace>()
                .eq("case_id", caseId).eq("stage", stage).eq("del_flag", 0)
                .in("action_kind", "START", "ADVANCE", "RETURN"));
        return cnt == null ? 0 : cnt;
    }

    private void appendTrace(Long caseId, int stage, int attempt, String actionKind, String content, Date when) {
        appendTrace(caseId, stage, attempt, actionKind, content, when, null);
    }

    private void appendTrace(Long caseId, int stage, int attempt, String actionKind, String content, Date when,
                             String remark) {
        TGiprCaseTrace t = new TGiprCaseTrace();
        t.setCaseId(caseId);
        t.setStage(stage);
        t.setAttemptNo(attempt);
        t.setActionKind(actionKind);
        t.setContent(content);
        t.setTraceTime(when);
        t.setRemark(remark);
        t.setDelFlag(0);
        traceMapper.insert(t);
    }

    /** 旧件缺列按同口径读：stage/status 数出来的为准，新列补默认，不因立的迟早分岔。 */
    private TGiprCaseFlow normalize(TGiprCaseFlow r) {
        if (r == null) {
            return null;
        }
        if (r.getStage() == null) {
            r.setStage(STAGE_ACCEPT);
        } else if (r.getStage() < 0) {
            r.setStage(STAGE_ACCEPT);
        } else if (r.getStage() > MAX_STAGE) {
            r.setStage(MAX_STAGE);
        }
        if (r.getStatus() == null) {
            r.setStatus(STATUS_ACTIVE);
        }
        if (r.getAttemptNo() == null) {
            r.setAttemptNo(1);
        }
        if (r.getCertFlag() == null) {
            r.setCertFlag(0);
        }
        if (r.getEndKind() == null) {
            r.setEndKind(END_NONE);
        }
        if (r.getDelFlag() == null) {
            r.setDelFlag(0);
        }
        return r;
    }

    private String buildIdemKey(TGiprCaseFlow form, String holderNo) {
        String t = new SimpleDateFormat("yyyyMMddHHmmss").format(form.getApplyTime());
        return holderNo + "|" + form.getProdId() + "|" + form.getGoodsKind().trim() + "|" + t;
    }
}
