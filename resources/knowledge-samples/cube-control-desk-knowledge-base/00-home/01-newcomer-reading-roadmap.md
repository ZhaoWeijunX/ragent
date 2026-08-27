---
title: 新人阅读路线
module: home
doc_type: onboarding-roadmap
status: initial-verified
last_verified: 2026-08-26
source_policy: current-repository-docs-and-code-only
---

# 新人阅读路线

本路线面向需要参与后端开发的新同学，也保留前端、测试和运维协作所需的定位入口。它不是业务规则的替代品；每次改动仍应回到目标模块的 Controller、Manager/Provider、Service、Model 和对应验证资产。

## 第一天：建立代码地图

1. 阅读 [知识库首页](00-knowledge-base-home.md)，先区分接单、通道能力和平台支撑能力。
2. 阅读仓库根目录 `AGENTS.md` 的“模块地图”和对应业务域索引；它给出了从 HTTP 入口到持久化模型的检索顺序。
3. 打开根 `pom.xml`，确认五个 Maven 模块：`cube-control-desk-app`、`cube-control-desk-biz`、`cube-control-desk-api`、`cube-control-desk-model`、`cube-control-desk-integration`。
4. 对一个真实需求，依次追踪 `app` Controller、`biz/modular` 编排、`biz/core` Service/Mapper、`model` DTO/Entity/Enum。异步链路还要追 Job、回调和配置。

## 第一周：按所负责业务域深入

| 负责内容 | 首读模块 | 需要特别确认的边界 |
| --- | --- | --- |
| 邮件接单、工单处理 | 接单 / SHIPPING、BILL / BL Intake | SHIPPING 与 BILL 共享部分邮件基础设施，但不共享工单主表。 |
| 订舱、放舱 | Booking、Release | 订舱回执成功不等同于放舱成功；放舱有独立任务、监听和回调。 |
| 提单官网提交 | Bill Input | 它是通道侧基础能力，不等同于 BL 接单工单。 |
| VGM | VGM Intake、VGM Input | 接单侧 `vgm_info` 与通道侧 `biz_vgm_record` 是不同数据域。 |
| 舱单 | Manifest Intake、Manifest Input | 先分别确认接单门面和通道提交实现，不从设计稿推断已上线范围。 |
| 导入、配置、任务、通知 | 平台支撑能力文档 | 先识别调用方业务域，再修改公共能力。 |

## 每次改动前的最小检查

- 找到实际请求/消息入口和至少一个下游写入点。
- 明确当前态真源、历史记录、异步任务和回调幂等键。
- 识别租户、船司、账号、通道等配置边界。
- 查 `api-test/scenarios/` 是否已有相邻场景；没有时不要将静态阅读表述为运行验证。
- 设计文档、任务卡、发布清单只能作为线索；代码与新鲜运行证据才决定当前行为。

## 协作时给出的最小上下文

提交需求、缺陷或排障结论时，至少说明：入口接口/Job、业务标识、租户与船司/通道、现象与期望、涉及状态、下游服务或回调、已执行验证及未覆盖边界。这样前后端、测试和运维可以使用同一条链路核验结论。

## 直接来源

- 仓库根 `AGENTS.md`：检索顺序、模块地图和业务边界。
- 根 `pom.xml`：当前 Maven 模块。
- `doc/README.md` 与 `doc/onboarding/`：现有文档入口和新人材料。
- 当前 `cube-control-desk-app`、`cube-control-desk-biz`、`cube-control-desk-model` 源码目录：实现定位起点。

## 范围、非目标与证据边界

本路线只回答“先读什么、怎样定位、用什么证明”，不替代模块文章中的业务规则。当前仓库能够证明代码、配置结构、SQL 和测试资产；生产环境实际配置、外部系统可用性和 Job 是否启用，当前代码无法确认。

## 文档/代码差异与待确认项

- 历史 Wiki 的模块粒度可能落后于当前 Intake/Input 分域；新人应以当前 Controller、Manager/Provider 和主数据为准。
- `api-test` 场景存在不等于本轮已运行。没有新鲜执行结果时只能标为“可用验证入口”。
- 具体租户业务规则和上线范围需要产品、测试或运维结合环境确认。

## 面试式自检

读完一条链路后，应能回答：入口在哪里、当前态真源是什么、配置从哪里解析、外部调用是否异步、回调如何定位与幂等、失败由谁补偿、什么证据能证明最终状态。
