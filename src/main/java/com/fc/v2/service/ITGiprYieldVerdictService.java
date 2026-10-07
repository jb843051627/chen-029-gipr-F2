package com.fc.v2.service;

import java.util.List;

import com.fc.v2.model.auto.TGiprYieldVerdict;
import com.fc.v2.model.custom.GiprYieldMeasure;

/**
 * 核定产量贴线落档留痕 Service接口（分档与留痕联动）
 *
 * 落库时钉进那行的线代号与启用那一一日，取自求值那一处当场返回的判定，
 * 字字相同，不另算一遍；线一旦改动，先前按老线落下的那些笔不追溯重算。
 *
 * @author fuce
 * @date 2026-10-07
 */
public interface ITGiprYieldVerdictService {

    /**
     * 贴一批（分档＋留痕同一笔）：逐条过求值那一处算定，判定原样落库。
     * 同一批并着贴两回（幂等键相同）重取原批，不重建；两回行数对不上点名报不合。
     */
    List<TGiprYieldVerdict> pasteBatch(String batchNo, List<GiprYieldMeasure> items);

    /** 按批次码取回原批（落库啥样回啥样，不当场重算） */
    List<TGiprYieldVerdict> listByBatch(String batchNo);

    /** 按主键查询 */
    TGiprYieldVerdict selectTGiprYieldVerdictById(Long id);
}
