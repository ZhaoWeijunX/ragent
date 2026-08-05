# 12306 知识库意图树设计（按文档内容）

> 配套导入脚本：[resources/database/imports/intent-nodes/12306-intent-nodes-import.sql](../../database/imports/intent-nodes/12306-intent-nodes-import.sql)
>
> 本文按 **PDF 抽取正文** 聚类意图，**不以语雀原目录结构为约束**。扫描样本：`resources/knowledge-samples/12306-pdf-doc/` 共 **89** 篇。

---

## 0. 空文档 / 近似空文档（需关注）

以下文档可抽取有效正文极少（几乎只有标题、外链或跳转说明），**不纳入意图叶子映射**；若仍上传入库，检索价值很低，建议剔除或补全文后再挂树。

| 文档 | 抽取有效字符 | 正文情况 |
|------|-------------|----------|
| `4.核心技术文档/用户敏感信息展示时学会脱敏.pdf` | ~27 | 仅标题 + 指向「手摸手之用户敏感信息展示脱敏」的跳转/原文链接，无实质内容 |
| `5.手摸手从零到一实现/5.1…/手摸手之梳理核心业务.pdf` | ~28 | 仅 ProcessOn 外链与访问密码，无业务梳理正文 |

**说明**：脱敏实现请以 `5.4…/手摸手之用户敏感信息展示脱敏.pdf`（有完整正文）为准，归属「用户域」叶子。

其余 FAQ 类短文（Maven / Windows 命令行过长 / 构造器注入等，约 200～500 字）仍有可检索答案，正常入树。

---

## 1. 设计目标

| 目标 | 说明 |
|------|------|
| 内容驱动 | 按用户问题域聚类：入门搭建、技术原语、业务域、热点难题、运维上线 |
| 三层结构 | `DOMAIN → CATEGORY → TOPIC`，仅 TOPIC 参与 LLM 分类 |
| 消歧 | 同主题的「原理文 / 落地文 / 面试文」尽量落到同一内容域，用 description 区分问法 |
| 排除空文 | 空文档不占叶子，避免污染 examples 与评测 |

**有效文档**：89 − 2 空文 = **87** 篇参与映射。

---

## 2. 意图树总览（内容域）

```
train12306（DOMAIN）· 拿个offer-12306实战
├── t12306-bootstrap（入门与工程搭建）
│   ├── t12306-boot-run          # 克隆 / 中间件 / 前后端启动 / 用户体系概要
│   ├── t12306-boot-scaffold     # SpringBoot 模块、目录、表结构（不含空「梳理核心业务」）
│   └── t12306-boot-standards    # 编程规范 / 格式化 / 检查 / MQ 姿势
├── t12306-primitives（通用技术原语）
│   ├── t12306-prim-patterns     # 责任链 / 策略模式
│   ├── t12306-prim-threadpool   # 线程池、并行流、lock、拒绝策略
│   ├── t12306-prim-cache        # Redis 锁、Redisson、缓存一致性原理
│   ├── t12306-prim-sharding     # 分库分表、Proxy、深分页、千万查询
│   ├── t12306-prim-security     # 敏感数据泄露防护、配置泄密（不含空脱敏 stub）
│   └── t12306-prim-components   # 手摸手基础组件库全家桶
├── t12306-domains（业务域落地）
│   ├── t12306-dom-user          # 注册 / 用户分库 / 加密脱敏落地 / 缓存穿透
│   ├── t12306-dom-passenger     # 乘车人模块与分库、本人订单
│   └── t12306-dom-ticket        # 购票流程、检索、座位、支付、令牌限流、订单分库
├── t12306-hotspots（高频难题）
│   ├── t12306-hot-inventory     # 超卖、中间站、Binlog、MQ 顺序、余票一致性
│   └── t12306-hot-concurrency   # 缓存击穿、节假日 Redis、布隆、性能优化、线程池场景
└── t12306-ops（排错与上线）
    ├── t12306-ops-troubleshoot  # Maven / MySQL / Windows / 分布式调用 / 注入 / 幂等场景 / 池参数
    ├── t12306-ops-reliability   # OOM、全局异常、延时关单选型、响应体、雪花 ID、CPU、压测
    └── t12306-ops-deploy        # 云服务器部署
```

**节点统计**：1 DOMAIN + 5 CATEGORY + 17 TOPIC = **23 个节点**

---

## 3. 节点明细与覆盖文档

### 3.1 DOMAIN

| intent_code | name | description |
|-------------|------|-------------|
| `train12306` | 拿个offer-12306实战 | 高铁售票系统工程实战：从本地启动、技术原语、业务落地到库存一致性、高并发与云上部署 |

### 3.2 入门与工程搭建（`t12306-bootstrap`）

| intent_code | name | 覆盖文档（内容归类） | top_k |
|-------------|------|----------------------|-------|
| `t12306-boot-run` | 本地启动跑通 | `2.快速开始/克隆项目`、`安装中间件环境`、`快速启动之*端项目`、`用户体系建设概要` | 8 |
| `t12306-boot-scaffold` | 工程脚手架与建模 | `创建SpringBoot单/多模块`、`工程目录结构`、`初始数据库表信息`、`梳理数据库表关系` | 8 |
| `t12306-boot-standards` | 开发规范 | `掌握架构师的编程规范`、`代码格式化`、`代码检查`、`消息队列正确使用姿势` | 6 |

### 3.3 通用技术原语（`t12306-primitives`）

| intent_code | name | 覆盖文档 | top_k |
|-------------|------|----------|-------|
| `t12306-prim-patterns` | 设计模式 | 责任链重构/抽象、策略模式落地/抽象 | 8 |
| `t12306-prim-threadpool` | 线程池与并发基础 | Hutool Builder、Dubbo 快速消费池、开源线程池框架、Mybatis 拒绝策略、lock 写法、并行流 | 8 |
| `t12306-prim-cache` | Redis 锁与缓存原理 | Redis 分布式锁演进、Redisson 原理、缓存与数据库一致性 | 8 |
| `t12306-prim-sharding` | 分片与大数据查询 | 分库分表平滑上线回滚、ShardingSphere-Proxy、深分页、千万数据防 OOM | 8 |
| `t12306-prim-security` | 数据与配置安全原理 | 防止用户敏感数据泄露、防止配置文件敏感信息泄漏 | 8 |
| `t12306-prim-components` | 基础组件库 | `5.3` 下全部组件库文档（公共/Web/持久层/日志/幂等/ID/用户/规约/设计模式/基础模块/教学） | 8 |

### 3.4 业务域落地（`t12306-domains`）

| intent_code | name | 覆盖文档 | top_k |
|-------------|------|----------|-------|
| `t12306-dom-user` | 用户域 | 注册接口、用户分库、加密存储、**脱敏落地**、注册防缓存穿透 | 8 |
| `t12306-dom-passenger` | 乘车人域 | 乘车人模块、乘车人分库、本人车票订单查看 | 8 |
| `t12306-dom-ticket` | 购票交易域 | 购票流程/v2、责任链验证、列车检索、座位示意图、支付、令牌限流、车票搜索 Redis 非 ES、订单分库 | 8 |

### 3.5 高频难题（`t12306-hotspots`）

| intent_code | name | 覆盖文档 | top_k |
|-------------|------|----------|-------|
| `t12306-hot-inventory` | 余票库存与一致性 | 防超卖、中间站点更新、Binlog 延迟、RocketMQ 顺序、**列车余票缓存库一致性** | 8 |
| `t12306-hot-concurrency` | 高并发与性能 | 缓存击穿双重判定锁、节假日 Redis 扛量、布隆容量碰撞率、核心接口性能优化、项目线程池使用场景 | 8 |

### 3.6 排错与上线（`t12306-ops`）

| intent_code | name | 覆盖文档 | top_k |
|-------------|------|----------|-------|
| `t12306-ops-troubleshoot` | 启动排错与实践 FAQ | Maven/MySQL8/Windows 命令行过长/分布式模式报错、构造器注入、幂等 HTTP 场景、线程池参数配置 | 6 |
| `t12306-ops-reliability` | 可靠性与可观测 | OOM 感知、全局异常拦截、订单延时关闭选型、**延时关单消息面试**、响应实体、雪花 ID、CPU、上线压测 | 8 |
| `t12306-ops-deploy` | 云上部署 | `云服务器部署如何12306项目？` | 8 |

---

## 4. 易混淆消歧（按内容）

| 用户表述 | 应命中 | 判断依据 |
|----------|--------|----------|
| 怎么启动 / 装中间件 | `t12306-boot-run` | 本地跑通，不是云部署 |
| 云服务器 / Nginx / 开放端口 | `t12306-ops-deploy` | 云上部署 |
| 线程池原理 / Hutool / Dubbo 池 | `t12306-prim-threadpool` | 技术原语 |
| 项目里什么场景用线程池 | `t12306-hot-concurrency` | 面试/场景 |
| 线程池参数怎么配 | `t12306-ops-troubleshoot` | FAQ 实践 |
| Redis 锁 / Redisson 原理 | `t12306-prim-cache` | 原理文 |
| 节假日 Redis 能不能扛 | `t12306-hot-concurrency` | 高并发面试 |
| 余票一致性 / 超卖 / Binlog | `t12306-hot-inventory` | 库存一致性专题 |
| 购票流程怎么写 | `t12306-dom-ticket` | 业务落地 |
| 脱敏怎么做 | `t12306-dom-user` | 有正文的脱敏落地；空 stub 不入树 |
| 敏感数据泄露原理 / ShardingSphere 加密上线 | `t12306-prim-security` | 安全原理 |
| 幂等组件怎么实现 | `t12306-prim-components` | 组件库 |
| 幂等 HTTP 用在哪 | `t12306-ops-troubleshoot` | FAQ 场景 |
| Maven/MySQL 报错 | `t12306-ops-troubleshoot` | 排错 |
| 延时关闭选型 / 延时关单消息 | `t12306-ops-reliability` | 可靠性/关单 |

**歧义引导**：

- 「线程池」→ `prim-threadpool` + `hot-concurrency` + `ops-troubleshoot`
- 「缓存」→ `prim-cache` + `hot-concurrency` + `hot-inventory`
- 「分库分表」→ `prim-sharding` + `dom-user` / `dom-passenger` / `dom-ticket`
- 「购票」→ `dom-ticket` + `hot-inventory`

---

## 5. 导入步骤

1. 创建知识库并上传文档；**建议不要上传 2 篇空文档**，或上传后禁用。
2. 查询：

   ```sql
   SELECT id, name, collection_name FROM t_knowledge_base
   WHERE name LIKE '%12306%' OR name LIKE '%拿个offer%';
   ```

3. 替换 `12306-intent-nodes-import.sql` 中 `__KB_ID_12306__` / `__COLLECTION_12306__`
4. 执行 SQL → `redis-cli DEL ragent:intent:tree`
5. 可选执行 `sample-questions-import.sql` 中 12306 段

---

## 6. 字段约定

| 字段 | 取值 |
|------|------|
| `level` | 0=DOMAIN, 1=CATEGORY, 2=TOPIC |
| `kind` | 0=KB |
| `collection_name` | 仅 TOPIC |
| 节点 id 段 | `2059500000000000001` 起 |
| 空文档 | **不映射**任何 `intent_code` |

