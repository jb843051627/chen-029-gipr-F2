package com.fc.v2.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TGiprCaseFlow;
import com.fc.v2.model.auto.TGiprCaseTrace;

/**
 * 专用标志使用核准办件流转单 Service接口（state-machine 形状：单据流转，不提供增删改查全套）
 *
 * 四格钉死、没有近路：0申请受理（县里收件）→1材料初审（市里初审）→2标志核准（省局核准）→3备案发标。
 * 件此刻站在第几格由后端数（stage），报面自带序号 view_seq 只供人眼对着看。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITGiprCaseFlowService {

    /** 按主键查询单据（旧件缺字段的按同口径补齐读） */
    TGiprCaseFlow selectTGiprCaseFlowById(Long id);

    /** 列表查询（流转台账；封口的件照样在名册里数得着） */
    List<TGiprCaseFlow> selectTGiprCaseFlowList(QueryWrapper<TGiprCaseFlow> queryWrapper);

    /**
     * 起一件：四样对上才起得来——企业（授权册代号）、名录产品、拟贴货类、递件时刻；
     * 同一家企业手上未闭上的件至多一条。同一件并着进两回（同一幂等键）重取原件，不重建。
     */
    TGiprCaseFlow createCase(TGiprCaseFlow form);

    /** 往后挪一格（一次只准一格）：门槛不过、件已封住，抛 FlowRuleException 点名原因 */
    TGiprCaseFlow advance(Long id, String remark);

    /** 往回收一格（一次只退一格）：头一格没处可退；退格的按语原样挪进追溯串末尾 */
    TGiprCaseFlow rollback(Long id, String remark);

    /** 在当前格落一句按语：同格同一趟只认最早那一句，后写顶不掉、不另存 */
    TGiprCaseFlow note(Long id, String remark);

    /** 末关（备案发标）核签岗落笔：两名各落一笔（同人只算一名） */
    TGiprCaseFlow sign(Long id, String signerNo);

    /** 递件一方自己反悔把话取了回：当作就此了结 */
    TGiprCaseFlow withdraw(Long id, String remark);

    /** 挂靠的名录产品整段并走：当作就此了结 */
    TGiprCaseFlow mergeEnd(Long id, String remark);

    /** 倒着看一遍：从末一格往回演，只出结果、不回写 */
    List<TGiprCaseTrace> replay(Long id);

    /** 修改内容：已封住不得再改，被拒抛 FlowRuleException */
    boolean updateContent(Long id, String remark);

    /** 删除整条：已封住抽不走，被拒抛 FlowRuleException */
    boolean remove(Long id);
}
