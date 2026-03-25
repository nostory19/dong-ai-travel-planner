# AI 模型监控指标完整流程

## 1. 监控依赖引入

在 `pom.xml` 文件中引入了以下监控相关依赖：

```xml
<!-- 监控 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

- `spring-boot-starter-actuator`：提供基础的监控端点
- `micrometer-registry-prometheus`：将监控指标暴露为 Prometheus 格式

## 2. 监控端点配置

在 `application.yml` 文件中配置了监控端点：

```yaml
# 监控配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics,threaddump,env,logfile
  endpoint:
    health:
      show-details: always
    prometheus:
      enabled: true
  prometheus:
    metrics:
      export:
        step: 10s
        registry-type: classic
```

- 暴露了多个监控端点，包括健康检查、系统信息、Prometheus 指标等
- 配置了 Prometheus 指标导出的时间步长为 10 秒

## 3. 监控上下文管理

### 3.1 定义监控上下文

创建了 `MonitorContext` 类，用于存储监控相关的上下文信息：

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorContext implements Serializable {
    private String userId;
    private String sessionId;
    @Serial
    private static final long serialVersionUID = 1L;
}
```

### 3.2 上下文持有器

实现了 `MonitorContextHolder` 类，使用 ThreadLocal 来管理监控上下文：

```java
public class MonitorContextHolder {
    private static final ThreadLocal<MonitorContext> CONTEXT_HOLDER = new ThreadLocal<>();
    // 当回调在线程池的其他线程执行时，ThreadLocal 无法传递；提供一次性全局后备以便监听器读取
    private static volatile MonitorContext TEMP_FALLBACK_CONTEXT;

    // 设置监控上下文
    public static void setContext(MonitorContext context) {
        CONTEXT_HOLDER.set(context);
        TEMP_FALLBACK_CONTEXT = context;
    }

    // 获取当前监控上下文
    public static MonitorContext getContext() {
        return CONTEXT_HOLDER.get();
    }

    // 获取一次性全局后备上下文，并清空该后备，避免串扰
    public static MonitorContext pollFallbackContext() {
        MonitorContext ctx = TEMP_FALLBACK_CONTEXT;
        TEMP_FALLBACK_CONTEXT = null;
        return ctx;
    }

    // 清除监控上下文
    public static void clearContext() {
        CONTEXT_HOLDER.remove();
    }
}
```

- 使用 `ThreadLocal` 存储每个线程的监控上下文
- 提供 `TEMP_FALLBACK_CONTEXT` 作为跨线程传递的后备机制

## 4. 指标收集器

实现了 `AiModelMetricsCollector` 类，用于收集各种监控指标：

```java
@Component
@Slf4j
public class AiModelMetricsCollector {
    @Resource
    private MeterRegistry meterRegistry;

    // 缓存已创建的指标，避免重复创建
    private final ConcurrentMap<String, Counter> requestCountersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> errorCountersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> tokenCountersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> responseTimersCache = new ConcurrentHashMap<>();
    
    // 新增：A/B测试和性能对比相关指标缓存
    private final ConcurrentMap<String, Counter> cacheHitCountersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> cacheMissCountersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> toolResponseTimersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> serviceCreationTimersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> truncationCountersCache = new ConcurrentHashMap<>();

    // 记录请求次数
    public void recordRequest(String userId, String sessionId, String modelName, String status) {
        // 实现逻辑...
    }

    // 记录错误
    public void recordError(String userId, String sessionId, String modelName, String errorMessage) {
        // 实现逻辑...
    }

    // 记录Token消耗
    public void recordTokenUsage(String userId, String sessionId, String modelName,
                                 String tokenType, long tokenCount) {
        // 实现逻辑...
    }

    // 记录模型的响应时间
    public void recordResponseTime(String userId, String sessionId, String modelName, Duration duration) {
        // 实现逻辑...
    }

    // 记录工具调用缓存命中
    public void recordToolCacheHit(String userId, String sessionId, String toolName) {
        // 实现逻辑...
    }

    // 记录工具调用缓存未命中
    public void recordToolCacheMiss(String userId, String sessionId, String toolName) {
        // 实现逻辑...
    }

    // 记录工具调用响应时间
    public void recordToolResponseTime(String userId, String sessionId, String toolName, 
                                     Duration duration, boolean fromCache) {
        // 实现逻辑...
    }

    // 记录AI服务实例创建时间
    public void recordServiceCreationTime(String userId, String sessionId, Duration duration, boolean fromCache) {
        // 实现逻辑...
    }

    // 记录MCP结果裁剪统计
    public void recordTruncation(String userId, String sessionId, String toolName, 
                               long beforeTokens, long afterTokens) {
        // 实现逻辑...
    }
}
```

- 使用 `MeterRegistry` 注册和管理指标
- 提供多种指标收集方法，包括请求次数、错误次数、Token消耗、响应时间等
- 支持 A/B 测试相关的指标收集，如缓存命中/未命中、工具调用响应时间等

## 5. 监控监听器

实现了 `AiModelMonitorListener` 类，实现了 `ChatModelListener` 接口，用于监听 AI 模型的请求和响应：

```java
@Component
@Slf4j
public class AiModelMonitorListener implements ChatModelListener {

    // 用于存储请求开始时间的键
    private static final String REQUEST_START_TIME_KEY = "request_start_time";
    // 用于监控上下文传递（因为请求和响应事件的触发不是同一个线程）
    private static final String MONITOR_CONTEXT_KEY = "monitor_context";
    
    @Resource
    private AiModelMetricsCollector aiModelMetricsCollector;

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        // 记录请求开始时间
        requestContext.attributes().put(REQUEST_START_TIME_KEY, Instant.now());
        // 从监控上下文中获取信息
        MonitorContext context = MonitorContextHolder.getContext();
        if (context == null) {
            log.warn("MonitorContext is null in onRequest; skip metrics for this request");
            return;
        }
        String userId = context.getUserId();
        String sessionId = context.getSessionId();
        requestContext.attributes().put(MONITOR_CONTEXT_KEY, context);
        // 获取模型名称
        String modelName = requestContext.chatRequest().modelName();
        // 记录请求指标
        aiModelMetricsCollector.recordRequest(userId, sessionId, modelName, "started");
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        // 从属性中获取监控信息（由 onRequest 方法存储）
        Map<Object, Object> attributes = responseContext.attributes();
        // 从监控上下文中获取信息
        MonitorContext context = (MonitorContext) attributes.get(MONITOR_CONTEXT_KEY);
        if (context == null) {
            context = MonitorContextHolder.getContext();
        }
        if (context == null) {
            context = MonitorContextHolder.pollFallbackContext();
        }
        if (context == null) {
            log.warn("MonitorContext is null in onResponse; skip metrics for this response");
            return;
        }
        String userId = context.getUserId();
        String sessionId = context.getSessionId();
        // 获取模型名称
        String modelName = responseContext.chatResponse().modelName();
        // 记录成功请求
        aiModelMetricsCollector.recordRequest(userId, sessionId, modelName, "success");
        // 记录响应时间
        recordResponseTime(attributes, userId, sessionId, modelName);
        // 记录 Token 使用情况
        recordTokenUsage(responseContext, userId, sessionId, modelName);
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        // 优先从属性或线程上下文中获取信息
        Map<Object, Object> attributes = errorContext.attributes();
        MonitorContext context = attributes != null ? (MonitorContext) attributes.get(MONITOR_CONTEXT_KEY) : null;
        if (context == null) {
            context = MonitorContextHolder.getContext();
        }
        if (context == null) {
            context = MonitorContextHolder.pollFallbackContext();
        }
        if (context == null) {
            log.warn("MonitorContext is null in onError; skip metrics for this error");
            return;
        }
        String userId = context.getUserId();
        String sessionId = context.getSessionId();
        // 获取模型名称和错误类型
        String modelName = errorContext.chatRequest().modelName();
        String errorMessage = errorContext.error().getMessage();
        // 记录失败请求
        aiModelMetricsCollector.recordRequest(userId, sessionId, modelName, "error");
        aiModelMetricsCollector.recordError(userId, sessionId, modelName, errorMessage);
        // 记录响应时间（即使是错误响应）
        recordResponseTime(attributes, userId, sessionId, modelName);
    }

    // 记录响应时间
    private void recordResponseTime(Map<Object, Object> attributes, String userId, String sessionId, String modelName) {
        // 实现逻辑...
    }

    // 记录Token使用情况
    private void recordTokenUsage(ChatModelResponseContext responseContext, String userId, String sessionId, String modelName) {
        // 实现逻辑...
    }
}
```

- 在 `onRequest` 方法中记录请求开始时间和监控上下文
- 在 `onResponse` 方法中记录成功请求、响应时间和 Token 使用情况
- 在 `onError` 方法中记录失败请求和错误信息
- 利用 LangChain4j 框架中 requestContext 和 responseContext 共享属性映射的机制传递监控上下文
- 结合 ThreadLocal 和全局后备上下文，确保监控上下文在不同线程间的传递

## 6. 监控指标传递机制

### 6.1 共享属性映射

LangChain4j 框架在处理 AI 模型请求时，会：
1. 创建一个请求上下文（`ChatModelRequestContext`）
2. 调用 `onRequest` 方法
3. 处理实际的 AI 模型请求
4. 创建响应上下文（`ChatModelResponseContext`），但使用与请求上下文相同的属性映射
5. 调用 `onResponse` 方法

这种设计确保了同一请求的不同阶段（请求和响应）共享相同的属性空间，因此可以在 `onRequest` 中存储信息，然后在 `onResponse` 中使用。

### 6.2 多重保障机制

代码实现了多重保障来确保监控上下文的获取：
1. **优先从共享属性中获取**：`MonitorContext context = (MonitorContext) attributes.get(MONITOR_CONTEXT_KEY);`
2. **如果属性中没有，则尝试从当前线程获取**：`context = MonitorContextHolder.getContext();`
3. **如果线程中也没有，则尝试从全局后备获取**：`context = MonitorContextHolder.pollFallbackContext();`

这种多重保障机制确保了即使在复杂的线程环境中，也能尽可能地获取到监控上下文。

## 7. 监控指标类型

系统收集的监控指标包括：

| 指标名称 | 类型 | 描述 | 标签 |
|---------|------|------|------|
| ai_model_requests_total | Counter | AI模型总请求次数 | user_id, session_id, model_name, status |
| ai_model_errors_total | Counter | AI模型错误次数 | user_id, session_id, model_name, error_message |
| ai_model_tokens_total | Counter | AI模型Token消耗总数 | user_id, session_id, model_name, token_type |
| ai_model_response_duration_seconds | Timer | AI模型响应时间 | user_id, session_id, model_name |
| ai_tool_cache_hits_total | Counter | 工具调用缓存命中次数 | user_id, session_id, tool_name |
| ai_tool_cache_misses_total | Counter | 工具调用缓存未命中次数 | user_id, session_id, tool_name |
| ai_tool_response_duration_seconds | Timer | 工具调用响应时间 | user_id, session_id, tool_name, from_cache |
| ai_service_creation_duration_seconds | Timer | AI服务实例创建时间 | user_id, session_id, from_cache |
| ai_tool_truncation_tokens_saved_total | Counter | 工具结果裁剪节省的Token数量 | user_id, session_id, tool_name |

## 8. 监控端点访问

系统提供了以下监控端点：

| 端点 | URL | 描述 |
|------|-----|------|
| 健康检查 | http://localhost:8290/actuator/health | 系统健康状态 |
| 系统信息 | http://localhost:8290/actuator/info | 应用信息 |
| Prometheus指标 | http://localhost:8290/actuator/prometheus | Prometheus格式的监控指标 |
| 线程转储 | http://localhost:8290/actuator/threaddump | 线程状态 |
| 环境变量 | http://localhost:8290/actuator/env | 环境配置 |
| 日志 | http://localhost:8290/actuator/logfile | 应用日志 |

## 9. 监控流程总结

1. **依赖引入**：添加 Spring Boot Actuator 和 Prometheus 依赖
2. **端点配置**：在 application.yml 中配置监控端点
3. **上下文管理**：创建 MonitorContext 和 MonitorContextHolder 管理监控上下文
4. **指标收集**：实现 AiModelMetricsCollector 收集各种监控指标
5. **监听事件**：实现 AiModelMonitorListener 监听 AI 模型的请求和响应
6. **指标传递**：利用 LangChain4j 共享属性映射和 ThreadLocal 确保监控上下文传递
7. **指标暴露**：通过 Actuator 端点暴露监控指标，供 Prometheus 等监控系统采集

这套监控系统设计全面，不仅覆盖了基本的请求、响应、错误等指标，还包括了 A/B 测试相关的性能对比指标，为系统的运行状态和性能优化提供了详细的监控数据。