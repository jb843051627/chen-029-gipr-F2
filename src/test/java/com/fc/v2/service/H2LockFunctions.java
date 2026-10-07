package com.fc.v2.service;

/**
 * 仅供 H2 测试：顶替 MySQL 的 get_lock/release_lock。
 * 状态机里锁只负责并发排队，真正的在办唯一/幂等由唯一约束兜底，故测试里恒放。
 */
public final class H2LockFunctions {

    private H2LockFunctions() {
    }

    public static Integer getLock(String name, Integer timeoutSeconds) {
        return 1;
    }

    public static Integer releaseLock(String name) {
        return 1;
    }
}
