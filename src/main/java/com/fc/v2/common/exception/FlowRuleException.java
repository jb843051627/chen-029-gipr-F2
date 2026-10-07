package com.fc.v2.common.exception;

/**
 * 办件流转规矩被顶住时抛出。
 * 消息必须点名道姓（缺哪一样、断在哪一格、哪道门关着），不许拿「材料不齐」一类含糊话搪塞。
 * 由 {@link com.fc.v2.common.exception.GlobalExceptionResolver} 兜成 AjaxResult.json。
 *
 * @author fuce
 * @date 2026-10-06
 */
public class FlowRuleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FlowRuleException(String message) {
        super(message);
    }
}
