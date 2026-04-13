# 智能行程规划助手（后端）- README Plus

> 面向作品集展示的增强版项目说明，聚焦「业务价值 + 技术实现 + 可观测性 + 可扩展性」。
## 开发计划


## 1. 项目概述

`dong-ai-travel-planner` 是一个基于大模型的智能旅游规划后端系统。  
用户通过自然语言输入出行需求后，系统可进行多轮理解、调用工具补充实时信息、输出结构化路线，并支持前端地图渲染。

项目定位：
- 面向真实业务场景的 AI Agent 应用后端
- 强调工程可落地（鉴权、监控、缓存、会话隔离、异常处理）
- 支持后续扩展为多 Agent 协同和 RAG 体系

---

## 2. 核心亮点

### 2.1 AI 对话与路线规划一体化
- 支持多轮对话，持续理解上下文意图
- 输出文本攻略 + 结构化路线（`dailyRoutes`）双通道结果
- 前端可基于结构化数据进行地图轨迹渲染

### 2.2 流式交互体验（Flux + SSE）
- 后端通过 `POST /ai_assistant/chat-stream` 返回 `text/event-stream`
- 使用 Reactor `Flux` 按 token/chunk 推送，降低首字等待时间
- 支持流式异常兜底与结束事件，提升用户体验稳定性

### 2.3 工具能力可插拔（MCP + Function Call）
- MCP 工具通过配置动态启停，支持热插拔
- Function Call 工具统一接入 `ToolManager`，便于扩展和治理
- 工具调用链路可纳入统一监控指标

### 2.4 会话隔离与记忆管理
- 以会话维度隔离 AI Service 实例，避免跨用户上下文污染
- Redis 承载短期记忆，MySQL 持久化历史消息与会话数据
- 会话与消息完整留痕，支持追溯与运维排障

### 2.5 全链路可观测性
- 基于 Micrometer 暴露 Prometheus 指标
- Grafana 面板覆盖请求量、耗时、Token、错误率、缓存命中等维度
- 支持 A/B 试验指标采集，便于持续性能优化

---

## 3. 技术架构

系统采用典型分层架构：
- **Controller 层**：REST API、鉴权注解、入参校验
- **Service 层**：会话管理、聊天编排、流式输出、后处理落库
- **AI 能力层**：Agent 服务工厂、模型接入、记忆、护轨、工具调用
- **数据层**：MyBatis + MySQL（会话/消息/权限等核心数据）
- **缓存层**：Redis（记忆）+ Caffeine（实例/工具缓存）
- **监控层**：Actuator + Micrometer + Prometheus + Grafana

推荐阅读：
- 主文档：`README.md`
- 接口文档：`doc/API.md`
- 监控面板：`doc/Prometheus-Grafana.json`

---

## 4. 关键业务流程（简版）

1. 用户发起聊天请求（携带会话 ID、用户 ID、消息）
2. 系统完成登录态和权限校验（Sa-Token）
3. 创建或复用会话与 AI Service 实例
4. 调用模型与工具，流式返回文本结果（SSE）
5. 流结束后写入消息历史，并提取结构化路线数据
6. 前端按结构化数据渲染路线与节点

---

## 5. 技术栈

| 分类 | 方案 |
|---|---|
| 语言与框架 | Java 21、Spring Boot 3 |
| AI 能力 | LangChain4j、OpenAI Compatible API |
| 数据库与 ORM | MySQL、MyBatis |
| 缓存 | Redis、Caffeine |
| 鉴权 | Sa-Token（JWT） |
| 监控 | Micrometer、Prometheus、Grafana |
| 其他 | Lombok、OkHttp、Hutool |

---

## 6. 目录结构（后端）

```text
dong-ai-travel-planner/
├── src/main/java/com/example/aitourism/
│   ├── ai/               # Agent、工具、记忆、护轨、工厂
│   ├── controller/       # 接口入口
│   ├── service/          # 业务编排与流式处理
│   ├── mapper/           # MyBatis 映射接口
│   ├── entity/           # 数据实体
│   ├── dto/              # 入参与出参对象
│   ├── config/           # 配置类（鉴权、跨域等）
│   └── monitor/          # 指标采集与监听
├── src/main/resources/
│   ├── application.yml   # 主配置
│   └── mapper/           # MyBatis XML
├── sql/                  # 建表与初始化脚本
├── doc/                  # 接口、监控面板等文档
├── pom.xml
└── README.md
```

---

## 7. 快速启动

### 7.1 环境准备
- JDK 21
- Maven 3.9+
- MySQL
- Redis

### 7.2 初始化数据库
```bash
mysql -u root -p < sql/create_table.sql
```

### 7.3 配置应用
修改 `src/main/resources/application.yml`（或本地覆盖配置）：
- `spring.datasource.*`
- `openai.*` / `openai-small.*`
- `spring.data.redis.*`
- `sa-token.*`
- `mcp.clients.*`（如启用 MCP 工具）

### 7.4 运行项目
```bash
mvn clean package
java -jar target/ai-tourism-0.0.1-SNAPSHOT.jar
```

默认端口：`8290`

---

## 8. 代表性接口

### 8.1 认证与用户
- `POST /auth/login`
- `POST /auth/register`
- `POST /auth/refresh`
- `POST /auth/logout`
- `GET /auth/me`

### 8.2 AI 助手
- `POST /ai_assistant/chat-stream`：流式对话（SSE）
- `POST /ai_assistant/get_history`：会话历史
- `POST /ai_assistant/session_list`：会话列表
- `POST /ai_assistant/session_modify`：会话管理（删除/改标题）

---

## 9. 监控与性能优化

当前已具备：
- 模型请求量、成功率、错误数、响应耗时、Token 消耗监控
- 工具缓存命中率与响应时间对比（缓存 vs 非缓存）
- A/B 测试指标（缓存策略、裁剪策略）
- Grafana 面板可直接导入：`doc/Prometheus-Grafana.json`

建议运维实践：
- Prometheus 抓取周期设置为 `10s`
- Grafana 看板区分“实时值（Stat）”与“趋势（Timeseries）”
- 关键阈值接入告警（错误率、耗时、缓存命中率）

---

## 10. 项目价值与可扩展方向

### 10.1 业务价值
- 降低用户制定行程门槛，提高规划效率
- 提供“对话 + 地图”的直观体验，增强可用性
- 支持多场景复用（本地生活、门店推荐、活动路线设计）

### 10.2 技术演进方向
- 引入 RAG：城市知识库检索增强
- 引入 LangGraph4j：流程可控、步骤可观测
- 增加地理编码与路径优化能力（减少绕路）
- 增强安全治理：限流、风控与审计日志

---

## 11. 相关资源

- 项目主说明：`README.md`
- 接口文档：`doc/API.md`
- 监控看板：`doc/Prometheus-Grafana.json`
- 前端仓库：

---

## 12. 许可说明

当前仓库说明为学习用途，商用请先获得授权。

## 项目可优化方向
多Agent架构
* 规划Agent
* 工具Agent
* 评估Agent

RAG增强
* 接入ES
* 做知识库

结果评估机制
* 自动打分
* 用户反馈学习
