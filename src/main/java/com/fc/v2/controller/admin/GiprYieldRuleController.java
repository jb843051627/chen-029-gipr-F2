package com.fc.v2.controller.admin;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TGiprYieldRule;
import com.fc.v2.model.custom.GiprYieldMeasure;
import com.fc.v2.service.ITGiprYieldRuleService;
import com.fc.v2.service.ITGiprYieldVerdictService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 核定产量与特色指标换算贴线档 Controller（rule-eval 形状：求值与对账入口）
 *
 * 看板那一格、大屏那条按品类分列的折线、折线上点出来的那一个数、后端逐条回算，
 * 都打这里进同一处算定，不许各自数一遍；两个地方各说各话视为缺陷。
 * 原先那条按线代号取值的路子（/evaluate）收什么回什么一概不动。
 *
 * @author fuce
 * @date 2026-10-07
 */
@Api(value = "核定产量与特色指标换算贴线档")
@Controller
@RequestMapping("/giprYieldRule")
public class GiprYieldRuleController extends BaseController {

    private final String prefix = "admin/giprYieldRule";

    @Autowired
    private ITGiprYieldRuleService giprYieldRuleService;

    @Autowired
    private ITGiprYieldVerdictService giprYieldVerdictService;

    @ApiOperation(value = "贴线台账跳转", notes = "贴线台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("giprYieldRule:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "贴线版本行面列表", action = "list")
    @ApiOperation(value = "线版本行面列表", notes = "含已到期、已撤下的版本——只是不再参与求值，行面照旧列得出来")
    @GetMapping("/list")
    @RequiresPermissions("giprYieldRule:list")
    @ResponseBody
    public ResultTable list(String objKind, String ruleCode) {
        startPage();
        List<TGiprYieldRule> list = giprYieldRuleService.listVersions(objKind, ruleCode);
        com.github.pagehelper.PageInfo<TGiprYieldRule> page =
                new com.github.pagehelper.PageInfo<TGiprYieldRule>(list);
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "原先那条按线代号取值的老路子", notes = "收什么回什么一概不动：无生效版本/输入越界/参数缺失一律回 0")
    @GetMapping("/evaluate")
    @RequiresPermissions("giprYieldRule:view")
    @ResponseBody
    public AjaxResult evaluate(String ruleCode, BigDecimal input, Date at) {
        return AjaxResult.successData(200, giprYieldRuleService.evaluate(ruleCode, input, at));
    }

    @ApiOperation(value = "看板那一格", notes = "按品类、按年度取线落档，回判定全貌；挑不出是合法回话")
    @GetMapping("/cell")
    @RequiresPermissions("giprYieldRule:view")
    @ResponseBody
    public AjaxResult cell(String objKind, Integer askYear, BigDecimal input, String inputUnit, Date at) {
        return AjaxResult.successData(200,
                giprYieldRuleService.evaluateByKind(objKind, askYear, input, inputUnit, at));
    }

    @ApiOperation(value = "折线上点出来的那一个数", notes = "按线代号取那一刻在位的一版落档，与看板格同一处算定")
    @GetMapping("/point")
    @RequiresPermissions("giprYieldRule:view")
    @ResponseBody
    public AjaxResult point(String ruleCode, BigDecimal input, String inputUnit, Date at) {
        return AjaxResult.successData(200, giprYieldRuleService.verdictByCode(ruleCode, input, inputUnit, at));
    }

    @ApiOperation(value = "大屏按品类分列的折线", notes = "该品类全部版本按启用日排开（含已到期、已撤下），只读不参与求值")
    @GetMapping("/series")
    @RequiresPermissions("giprYieldRule:view")
    @ResponseBody
    public AjaxResult series(String objKind) {
        return AjaxResult.successData(200, giprYieldRuleService.listVersions(objKind, null));
    }

    @Log(title = "后端逐条回算", action = "recalc")
    @ApiOperation(value = "后端逐条回算", notes = "一批实测逐条过同一处算定，回判定全貌列表，不留痕")
    @PostMapping("/recalc")
    @RequiresPermissions("giprYieldRule:view")
    @ResponseBody
    public AjaxResult recalc(@RequestBody List<GiprYieldMeasure> items) {
        return AjaxResult.successData(200, giprYieldRuleService.recalcBatch(items));
    }

    @Log(title = "贴批落档留痕", action = "pasteBatch")
    @ApiOperation(value = "贴一批（分档＋留痕同一笔）", notes = "同一批并着贴两回幂等，重取不重建；钉进那行的线代号与启用日与求值当场返回字字相同")
    @PostMapping("/pasteBatch")
    @RequiresPermissions("giprYieldRule:paste")
    @ResponseBody
    public AjaxResult pasteBatch(String batchNo, @RequestBody List<GiprYieldMeasure> items) {
        return AjaxResult.successData(200, giprYieldVerdictService.pasteBatch(batchNo, items));
    }

    @ApiOperation(value = "按批次码取回原批", notes = "落库啥样回啥样，不当场重算（不追溯重算）")
    @GetMapping("/batch")
    @RequiresPermissions("giprYieldRule:view")
    @ResponseBody
    public AjaxResult batch(String batchNo) {
        return AjaxResult.successData(200, giprYieldVerdictService.listByBatch(batchNo));
    }

    @ApiOperation(value = "看板对账", notes = "挂在板上的线代号里这一刻挑不出在位版本的，逐条列出")
    @GetMapping("/unmatched")
    @RequiresPermissions("giprYieldRule:view")
    @ResponseBody
    public AjaxResult unmatched(@RequestParam(value = "code", required = false) List<String> codes, Date at) {
        return AjaxResult.successData(200, giprYieldRuleService.listUnmatchedCodes(codes, at));
    }
}
