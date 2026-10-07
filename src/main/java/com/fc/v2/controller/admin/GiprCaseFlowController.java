package com.fc.v2.controller.admin;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TGiprCaseFlow;
import com.fc.v2.model.auto.TGiprCaseTrace;
import com.fc.v2.service.ITGiprCaseFlowService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 专用标志使用核准办件流转单 Controller（state-machine 形状：流转入口）
 *
 * 四格钉死：县里收件→市里初审→省局核准→备案发标。件在第几格以后端数出来的 stage 为准，
 * 报面 view_seq 只回给人眼对照。规矩被顶住时业务异常的消息原样回，不拿含糊话搪塞。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Api(value = "专用标志使用核准办件流转单")
@Controller
@RequestMapping("/giprCaseFlow")
public class GiprCaseFlowController extends BaseController {

    private final String prefix = "admin/giprCaseFlow";

    @Autowired
    private ITGiprCaseFlowService giprCaseFlowService;

    @ApiOperation(value = "流转台账跳转", notes = "流转台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("giprCaseFlow:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "专用标志使用核准办件流转单流转台账", action = "list")
    @ApiOperation(value = "流转台账", notes = "封口的件照旧在名册里数得着")
    @GetMapping("/list")
    @RequiresPermissions("giprCaseFlow:list")
    @ResponseBody
    public ResultTable list(TGiprCaseFlow record) {
        QueryWrapper<TGiprCaseFlow> queryWrapper = new QueryWrapper<TGiprCaseFlow>();
        queryWrapper.orderByDesc("create_time");
        startPage();
        com.github.pagehelper.PageInfo<TGiprCaseFlow> page =
                new com.github.pagehelper.PageInfo<TGiprCaseFlow>(giprCaseFlowService.selectTGiprCaseFlowList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "单件查询", notes = "关次以后端数出来的为准，报面序号只供对照")
    @GetMapping("/detail")
    @RequiresPermissions("giprCaseFlow:view")
    @ResponseBody
    public AjaxResult detail(Long id) {
        return AjaxResult.successData(200, giprCaseFlowService.selectTGiprCaseFlowById(id));
    }

    @Log(title = "专用标志使用核准办件起件", action = "start")
    @ApiOperation(value = "县里收件起一件", notes = "四样对上才起得来；同一企业在办唯一；同一件并递两回幂等")
    @PostMapping("/start")
    @RequiresPermissions("giprCaseFlow:advance")
    @ResponseBody
    public AjaxResult start(TGiprCaseFlow form) {
        return AjaxResult.successData(200, giprCaseFlowService.createCase(form));
    }

    @Log(title = "专用标志使用核准办件流转单推进", action = "advance")
    @ApiOperation(value = "往后挪一格", notes = "一次只准一格；跨两格不认")
    @PostMapping("/advance")
    @RequiresPermissions("giprCaseFlow:advance")
    @ResponseBody
    public AjaxResult advance(Long id, String remark) {
        return AjaxResult.successData(200, giprCaseFlowService.advance(id, remark));
    }

    @Log(title = "专用标志使用核准办件流转单回退", action = "rollback")
    @ApiOperation(value = "往回收一格", notes = "一次只退一格，退格按语原样挪进追溯串末尾")
    @PostMapping("/rollback")
    @RequiresPermissions("giprCaseFlow:rollback")
    @ResponseBody
    public AjaxResult rollback(Long id, String remark) {
        return AjaxResult.successData(200, giprCaseFlowService.rollback(id, remark));
    }

    @Log(title = "专用标志使用核准办件按语", action = "note")
    @ApiOperation(value = "当前格落一句按语", notes = "同一格同一趟只认最早那句，后写顶不掉、不另存")
    @PostMapping("/note")
    @RequiresPermissions("giprCaseFlow:advance")
    @ResponseBody
    public AjaxResult note(Long id, String remark) {
        return AjaxResult.successData(200, giprCaseFlowService.note(id, remark));
    }

    @Log(title = "备案发标核签岗落笔", action = "sign")
    @ApiOperation(value = "两名核签岗各落一笔", notes = "同人只算一名；两笔齐了才放末关")
    @PostMapping("/sign")
    @RequiresPermissions("giprCaseFlow:advance")
    @ResponseBody
    public AjaxResult sign(Long id, String signerNo) {
        return AjaxResult.successData(200, giprCaseFlowService.sign(id, signerNo));
    }

    @Log(title = "递件方撤回", action = "withdraw")
    @ApiOperation(value = "递件一方自己反悔把话取了回", notes = "就此了结，不必再往前")
    @PostMapping("/withdraw")
    @RequiresPermissions("giprCaseFlow:rollback")
    @ResponseBody
    public AjaxResult withdraw(Long id, String remark) {
        return AjaxResult.successData(200, giprCaseFlowService.withdraw(id, remark));
    }

    @Log(title = "名录产品整段并走", action = "mergeEnd")
    @ApiOperation(value = "挂靠名录产品整段并走", notes = "就此了结，不必再往前")
    @PostMapping("/mergeEnd")
    @RequiresPermissions("giprCaseFlow:rollback")
    @ResponseBody
    public AjaxResult mergeEnd(Long id, String remark) {
        return AjaxResult.successData(200, giprCaseFlowService.mergeEnd(id, remark));
    }

    @ApiOperation(value = "倒着看一遍", notes = "从末一格往回演，只出结果、不回写")
    @GetMapping("/replay")
    @RequiresPermissions("giprCaseFlow:view")
    @ResponseBody
    public AjaxResult replay(Long id) {
        List<TGiprCaseTrace> traces = giprCaseFlowService.replay(id);
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("current", giprCaseFlowService.selectTGiprCaseFlowById(id));
        data.put("traces", traces);
        return AjaxResult.successData(200, data);
    }

    @Log(title = "专用标志使用核准办件内容修改", action = "edit")
    @ApiOperation(value = "修改内容", notes = "已封住不得再改")
    @PostMapping("/edit")
    @RequiresPermissions("giprCaseFlow:advance")
    @ResponseBody
    public AjaxResult edit(Long id, String remark) {
        return toAjax(giprCaseFlowService.updateContent(id, remark) ? 1 : 0);
    }

    @Log(title = "专用标志使用核准办件流转单删除", action = "remove")
    @ApiOperation(value = "删除整条", notes = "已封住抽不走")
    @DeleteMapping("/remove")
    @RequiresPermissions("giprCaseFlow:rollback")
    @ResponseBody
    public AjaxResult remove(Long id) {
        return toAjax(giprCaseFlowService.remove(id) ? 1 : 0);
    }
}
