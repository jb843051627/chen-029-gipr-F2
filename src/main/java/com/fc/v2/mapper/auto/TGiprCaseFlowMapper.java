package com.fc.v2.mapper.auto;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fc.v2.model.auto.TGiprCaseFlow;

/**
 * 专用标志使用核准办件流转单数据层
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface TGiprCaseFlowMapper extends BaseMapper<TGiprCaseFlow> {

    /** 取件并上行锁：同一条件的动作排队走，后到的不被挤掉（须在事务内调用） */
    TGiprCaseFlow selectByIdForUpdate(Long id);

    /** 按企业取命名锁：同企业并发起件排队，第二笔进来时第一笔已落库，按「在办唯一」判 */
    Integer selectGetLock(@Param("name") String name, @Param("timeout") Integer timeout);

    /** 释放命名锁 */
    Integer selectReleaseLock(@Param("name") String name);
}
