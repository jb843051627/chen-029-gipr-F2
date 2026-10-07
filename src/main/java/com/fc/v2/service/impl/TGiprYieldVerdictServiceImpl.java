package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.exception.FlowRuleException;
import com.fc.v2.mapper.auto.TGiprYieldVerdictMapper;
import com.fc.v2.model.auto.TGiprYieldVerdict;
import com.fc.v2.model.custom.GiprYieldMeasure;
import com.fc.v2.model.custom.GiprYieldVerdict;
import com.fc.v2.service.ITGiprYieldRuleService;
import com.fc.v2.service.ITGiprYieldVerdictService;
import com.fc.v2.util.StringUtils;

/**
 * 核定产量贴线落档留痕 Service业务层处理（分档与留痕联动）
 *
 * 钉死的三条：
 * 一、落库时钉进那行的线代号与启用那一一日，取自求值那一处（{@link ITGiprYieldRuleService}）
 *     当场返回的判定，字字相同——取自邻类品类或上一交班期的都判为摆不齐，这里不另算一遍；
 * 二、同一批并着贴两回要幂等：幂等键（批次码）相同的重取原批，不重建；
 * 三、线一旦改动，先前按老线落下的那些笔不追溯重算——只追加，没有重算/改写这条路。
 *
 * @author fuce
 * @date 2026-10-07
 */
@Service
public class TGiprYieldVerdictServiceImpl implements ITGiprYieldVerdictService {

    /** input_raw/input_conv 栏位放得下的大小边界，放不下的留空、数在 message 里指名 */
    private static final BigDecimal COLUMN_FIT = new BigDecimal("1E18");

    @javax.annotation.Resource
    private TGiprYieldVerdictMapper giprYieldVerdictMapper;

    @javax.annotation.Resource
    private ITGiprYieldRuleService giprYieldRuleService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<TGiprYieldVerdict> pasteBatch(String batchNo, List<GiprYieldMeasure> items) {
        if (StringUtils.isEmpty(batchNo)) {
            throw new FlowRuleException("贴批贴不下去：缺「批次码」这一栏（同一批并着贴两回靠它认）");
        }
        if (items == null || items.isEmpty()) {
            throw new FlowRuleException("贴批贴不下去：一批里一行实测都没有");
        }
        String key = batchNo.trim();

        // 幂等先认：同一批并着贴两回，重取原批，不重建
        List<TGiprYieldVerdict> existing = listByBatch(key);
        if (!existing.isEmpty()) {
            if (existing.size() != items.size()) {
                throw new FlowRuleException("批次「" + key + "」早先贴过 " + existing.size() + " 行，这回递来 "
                        + items.size() + " 行，两回对不上：同批并着贴须一模一样，这一笔没落");
            }
            return existing;
        }

        // 逐条过求值那一处算定（判定只有这一份，这里不另数一遍）
        List<GiprYieldVerdict> verdicts = giprYieldRuleService.recalcBatch(items);
        List<TGiprYieldVerdict> rows = new ArrayList<>();
        for (int i = 0; i < verdicts.size(); i++) {
            rows.add(pin(key, i + 1, items.get(i), verdicts.get(i)));
        }
        try {
            for (TGiprYieldVerdict row : rows) {
                giprYieldVerdictMapper.insert(row);
            }
        } catch (DuplicateKeyException e) {
            // 同一刻两路并贴同一批：唯一键顶回一路，赢家那批原样取回，不并不丢
            return listByBatch(key);
        }
        return listByBatch(key);
    }

    @Override
    public List<TGiprYieldVerdict> listByBatch(String batchNo) {
        if (StringUtils.isEmpty(batchNo)) {
            return new ArrayList<>();
        }
        return giprYieldVerdictMapper.selectList(new QueryWrapper<TGiprYieldVerdict>()
                .eq("batch_no", batchNo.trim()).eq("del_flag", 0).orderByAsc("row_no"));
    }

    @Override
    public TGiprYieldVerdict selectTGiprYieldVerdictById(Long id) {
        return giprYieldVerdictMapper.selectById(id);
    }

    /**
     * 钉行：线代号与启用那一一日照判定件上原样誊，字字相同；
     * 挑不出/数不合的行 rule_code、eff_start 留空，message 里那句明白话原样落。
     */
    private TGiprYieldVerdict pin(String batchNo, int rowNo, GiprYieldMeasure m, GiprYieldVerdict v) {
        TGiprYieldVerdict row = new TGiprYieldVerdict();
        row.setBatchNo(batchNo);
        row.setRowNo(rowNo);
        row.setObjKind(m == null ? null : (StringUtils.isEmpty(m.getObjKind()) ? null : m.getObjKind().trim()));
        row.setAskYear(m == null ? null : m.getAskYear());
        row.setMeasureAt(m == null ? null : m.getMeasureAt());
        row.setInputRaw(fit(v.getInputRaw()));
        row.setInputUnit(v.getInputUnit());
        row.setInputConv(fit(v.getInputConv()));
        row.setRuleCode(v.getRuleCode());
        row.setEffStart(v.getEffStart());
        row.setLevel(v.getLevel());
        row.setNeedSecond(v.isNeedSecond() ? 1 : 0);
        row.setApplicable(v.isApplicable() ? 1 : 0);
        row.setMessage(v.getMessage());
        row.setDelFlag(0);
        return row;
    }

    /** 栏位放得下的原样落，放不下的（大得没边那种）留空——数本身已在 message 里指名 */
    private BigDecimal fit(BigDecimal v) {
        if (v == null) {
            return null;
        }
        return v.abs().compareTo(COLUMN_FIT) < 0 ? v : null;
    }
}
