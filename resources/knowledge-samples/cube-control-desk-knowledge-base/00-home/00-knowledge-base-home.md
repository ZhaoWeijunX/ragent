---
title: cube-control-desk 知识库首页
module: home
doc_type: knowledge-base-home
audience:
  - 新入职后端开发
  - 前端协作开发
  - 测试
  - 运维
status: initial-verified
last_verified: 2026-08-26
source_policy: current-repository-docs-and-code-only
---

# cube-control-desk 知识库首页

## 1. 这套知识库解决什么问题

本知识库面向刚接手 `cube-control-desk` 的开发同学，帮助读者从“系统做什么”逐步进入“某条业务链路如何从入口推进到数据、下游调用和最终状态”。

主要读者是新入职后端开发；每个业务模块还会保留前端接口协作、测试验证和运维排障所需的信息。

本知识库不替代源码、数据库脚本或运行环境。它的作用是给出可靠的阅读路径和代码地图；具体变更、排障和上线前仍须回到当前代码、配置与测试证据确认。

## 2. 事实来源与可靠性规则

本知识库只使用以下来源：

1. 当前仓库中已存在的文档、设计、验证材料和 SQL/配置资产。
2. 当前仓库中的 Java 代码、Maven 模块、测试与接口契约。

以下规则对后续全部文章生效：

- 当前代码与已存在文档冲突时，以当前代码能够证明的行为作为“现状”。
- 不能被代码或现有文档证明的业务意图，标记为“待确认”，不补写为事实。
- 任务卡、迁移计划、发布 Checklist 和历史验证记录只能用于说明演进或证据边界，不能单独证明现行行为。
- 每篇文章都必须列出来源、关键代码入口、数据/配置边界、验证证据和最后核验日期。
- 文章中的“设计中”“待验证”“历史材料”标签必须保留，不能被改写成已上线或已完成。

## 3. 先怎样理解项目

根 Maven 工程当前包含五个模块：

- `cube-control-desk-app`：HTTP Controller、请求装配和应用层入口。
- `cube-control-desk-biz`：业务编排、基础 CRUD、任务、策略、三方调用和公共组件。
- `cube-control-desk-api`：OpenAPI / Feign 契约。
- `cube-control-desk-model`：实体、DTO、VO、枚举及持久化模型。
- `cube-control-desk-integration`：外部系统集成契约与客户端封装。

常见请求链应按以下顺序定位：

```text
Controller → Manager / Provider → Service → Mapper / Repository
                           ↓
                  Job / Strategy / Third-party client
```

这是一条定位路径，不表示所有场景都严格经过同名类：有些 OpenAPI、Job 回调和策略分发会直接进入 Provider、Handler 或状态机动作。实际文章会给出对应业务的真实调用链。

## 4. 知识库目录和阅读路径

知识库分为七个工作区：

```text
00-home/                         阅读入口、术语和系统地图
01-core-business-chains/         核心业务模块
02-platform-supporting-capabilities/  平台支撑能力
03-cross-domain-flows/           跨模块端到端链路
04-developer-onboarding/         后端新人开发方法
05-role-collaboration/           前端、测试、运维协作
06-reference-and-governance/     来源、差异、未知项和保鲜机制
07-evolution-and-evidence/       历史设计、迁移和验证证据
99-templates/                    文档模板与评审清单
```

建议新同学按此顺序阅读：

1. 本文与“业务地图、系统架构、术语边界”。
2. 自己将要参与的核心业务模块的“模块概览”和“端到端链路”。
3. 该模块的“数据与状态”“接口协作”“开发指南”。
4. 需要联调或排障时，再读“跨域链路”“角色协作”和“排障与验证”。
5. 最后才查看历史迁移、任务包和发布证据，避免把计划误读成现状。

## 5. 核心业务模块

### 5.1 已由项目业务索引明确的模块

| 模块 | 业务边界 | 首要阅读重点 |
| --- | --- | --- |
| 接单 / SHIPPING | 邮件、对话记录进入接单流程，形成 SHIPPING 工单和委托信息 | 工单、邮件记录、Agent 解析、分配、审核与去订舱 |
| Booking | 创建并推进订舱任务，维护订舱当前态 | `BookingParam`、任务状态机、`bookingCallback`、账号和渠道 |
| Release | 从订舱成功派生放舱任务，监听并回写放舱状态 | `createReleaseSpaceTask`、`releaseSpaceCallback`、监听策略与历史记录 |
| BILL / BL Intake | 提单接单门面，复用邮件等基础设施但使用独立 `bl_*` 主数据 | BL 工单、Agent 回调、本地操作、字段来源、异常修复 |
| Bill Input | 提单补料通道能力，负责校验、提交、回执、文件监听、识别与比对 | Processor、船司策略、`BillRecordHandler`、文件 Job |
| VGM Intake | 接单侧 VGM 业务门面，维护独立 `vgm_info` 和详情快照 | 从 BL 创建、保存、提交、联合提交投影 |
| VGM Input | 通道侧 VGM 官网填写能力，使用独立任务和回执模型 | 提交处理、执行任务、回执和状态推进 |

这些模块的详细边界以根目录 `AGENTS.md` 的业务地图为准。特别注意：BL Intake 与 Bill Input、VGM Intake 与 VGM Input 分别是不同的数据和职责边界，不能因为调用关系而混作一个模块。

### 5.2 已有代码与设计材料、需要逐篇核验的模块

| 模块 | 当前可见依据 | 编写时的要求 |
| --- | --- | --- |
| Manifest Intake | `app/modular/manifest`、`biz/modular/manifest` 与舱单接单侧设计 | 必须用当前 Controller、Manager、实体和测试核验设计是否已落地 |
| Manifest Input | `app/modular/manifest`、`biz/modular/manifest` 与通道侧设计/任务包 | 必须区分已实现能力、设计中能力和待确认项 |
| Shipment Tracking | `biz/core/trace`、`model/biz/trace` 与现有 Wiki | 需要从当前入口反查订阅、回调和推送链路 |
| Plan / Schedule | `app/biz modular` 下的 `plan`、`schedule` 目录和现有 Wiki | 需要确认计划、船期与订舱之间的真实耦合范围 |

将它们纳入目录是为了覆盖当前代码与文档已暴露的业务面；这不表示其设计文档中的每项能力已经上线。

## 6. 平台支撑能力如何分组

支撑能力不按每个 Java 包平铺，而按新人理解业务时需要共同掌握的职责收敛：

- 任务与状态机：任务壳、租户任务、状态流转和回调。
- 账号、船司与业务配置：订舱账号、船司能力、租户配置、运行时配置。
- 邮件、通知、推送与消息：邮件记录、模板、通知和消息路由。
- Agent、RPA、Cluster 与任务调度：解析、外部执行、调度和回执。
- 第三方、FMS 与客户端集成：统一客户端、配置解析和外部调用边界。
- 文件、Excel、模板与对象存储：上传、导入、文件识别和文件流转。
- Job、重试、脚本与补偿：定时任务、业务重试、Groovy 脚本和补偿操作。
- 校验、策略与扩展点：渠道/船司差异、规则校验和策略分发。
- 系统、租户、安全与 OPS：系统配置、租户上下文、审计和受控运维工具。
- 日志、统计、BI、Chart 与可观测性：日志、指标、看板和问题定位。
- Chat 与内部协作：内部沟通相关入口和数据边界。
- Route、Port、Country 与地点基础能力：航线、港口、国家等被业务模块复用的数据能力。

支撑模块只生成“能力概览”“接入与配置”“排障与验证”两至三篇文章；它们不会替代核心业务模块自身的端到端说明。

## 7. 跨域链路优先级

单独文档化以下跨域链路，避免读者只掌握局部类名：

1. 接单 / SHIPPING → Booking → Release。
2. BL Intake → Bill Input。
3. Bill Input 提交 → 提交检查 → 文件监听 → 文件识别。
4. BL 与 VGM 的独立和联合提交。
5. VGM Intake → VGM Input。
6. Manifest Intake → Manifest Input。
7. 任务下发 → 外部执行 → 回调 → 状态流转。
8. 邮件拉取 → Agent 解析 → 工单创建。
9. Excel / 文件导入 → 领域数据写入。
10. 货物追踪 → 通知 → 异常补偿。

每一条跨域链路都将明确：入口、输入、主数据、状态变化、外部调用、异步 Job、回调、失败与验证边界。

## 8. 当前已知文档差异

以下差异会进入后续的“文档—代码差异台账”，不直接改写原文档：

- `doc/wiki/` 中部分模块页是历史概览，路径或业务语义需要按当前代码复核。
- Bill 相关旧 Wiki 语义与当前 BL Intake / Bill Input 的职责边界可能不一致；后续文章以当前业务索引、代码和明确标注的边界为准。
- `doc/README.md` 是现有索引入口，但不能替代全量目录或代码核验。
- Manifest 设计和任务包都存在；后续文章会按“代码已证明 / 仅设计存在 / 待确认”分层说明。

## 9. 本文的直接来源

| 来源 | 本文使用方式 |
| --- | --- |
| `pom.xml` | 核对 Maven 根模块列表 |
| `AGENTS.md` | 识别接单、订舱、放舱、BL/Bill、VGM 和公共基建的业务边界、术语和检索入口 |
| `doc/README.md` | 核对现有文档分类及 Manifest、Onboarding、Wiki、Design 的入口 |
| `doc/wiki/SUMMARY.md` | 参考既有模块地图与典型请求分层；不将其历史描述直接当作现状结论 |
| `doc/onboarding/entrusted-quick-start.md` | 参考接单链路的既有上手范围 |
| `doc/onboarding/booking-release-quick-start.md` | 参考订舱—放舱的既有上手范围 |
| `doc/design/manifest/manifest_input_design.md`、`manifest_entrusted_design.md` | 确认 Manifest Input 与 Manifest Intake 是分开的设计主题 |
| `cube-control-desk-app/src/main/java/.../app/modular/` | 核对当前应用入口的业务目录 |
| `cube-control-desk-biz/src/main/java/.../biz/modular/`、`biz/core/` | 核对当前业务编排和基础服务的业务目录 |

## 10. 这篇文章之后

下一篇建议编写 `00-home/01-newcomer-reading-roadmap.md`：把不同角色和不同任务场景映射到明确的阅读顺序、代码入口和验证边界。

> 文档状态：本文是知识库的首篇验证样稿。它只建立阅读入口和真实性规则，不替代任何核心业务模块的详细链路说明。

