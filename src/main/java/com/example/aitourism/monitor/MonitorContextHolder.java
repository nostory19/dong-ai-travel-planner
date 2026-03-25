package com.example.aitourism.monitor;

import lombok.extern.slf4j.Slf4j;

/**
 * 监控上下文持有者（同线程内有效）
 * 用于在请求处理过程中传递监控上下文，确保在不同线程之间能够正确传递。
 * 该类使用 ThreadLocal 来存储每个线程的监控上下文，避免了线程安全问题。
 * 该类提供了一些静态方法，用于设置、获取和清除监控上下文。
 * 具体用法：
 * 1. 在请求处理方法中，使用 setContext 方法设置监控上下文。
 * 2. 在需要使用监控上下文的地方，使用 getContext 方法获取当前线程的监控上下文。
 * 3. 在请求处理方法结束时，使用 clearContext 方法清除监控上下文，避免线程泄漏。
 * 存储示例：存储了用户ID为123456，会话ID为abcdef的监控上下文。
 * 假入是另外一个线程呢？
 * 该线程无法直接访问请求处理方法中的监控上下文，需要通过 pollFallbackContext 方法获取。
 * 该方法会将请求处理方法中的监控上下文存储到临时后备中，供该线程读取。
 * 该方法在使用后需要手动清空临时后备，避免串扰。
 * 意思是只能存储一个线程的监控上下文？是吗？
 * 答案是：是。
 */
@Slf4j
public class MonitorContextHolder {
    private static final ThreadLocal<MonitorContext> CONTEXT_HOLDER = new ThreadLocal<>();
    // 当回调在线程池的其他线程执行时，ThreadLocal 无法传递；提供一次性全局后备以便监听器读取
    private static volatile MonitorContext TEMP_FALLBACK_CONTEXT;

    /**
     * 设置监控上下文
     */
    public static void setContext(MonitorContext context) {
        CONTEXT_HOLDER.set(context);
        TEMP_FALLBACK_CONTEXT = context;
    }

    /**
     * 获取当前监控上下文
     */
    public static MonitorContext getContext() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 获取一次性全局后备上下文，并清空该后备，避免串扰
     */
    public static MonitorContext pollFallbackContext() {
        MonitorContext ctx = TEMP_FALLBACK_CONTEXT;
        TEMP_FALLBACK_CONTEXT = null;
        return ctx;
    }

    /**
     * 清除监控上下文
     */
    public static void clearContext() {
        CONTEXT_HOLDER.remove();
    }
}
