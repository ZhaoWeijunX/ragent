---
title: SHIPPING 开发定位指南
module: entrusted-shipping
doc_type: development-guide
audience: new-backend
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---
# SHIPPING 开发定位指南

## 证据合同与实现核验

目的为改动 SHIPPING 时保持域边界；非目标是引入新框架。推荐按 `app controller → biz/modular manager/strategy/job → biz/core service/mapper → model entity/enum → SQL` 追踪。共享建单必须通过 `WorkOrderContextResolverRegistry`/`EntrustedOrderCreateFactory` 分派，不能在 `WorkOrderManagerImpl` 增加 BILL 分支或直接写 `entrusted_work_order` 存 BL。

真实证据为 `WorkOrderCreateController`、`WorkOrderManagerImpl`、`AbstractShippingOrderCreate`、resolver/factory、service/mapper/entity；测试为 `WorkOrderContextResolverRegistryTest`、`WorkOrderPageQueryServiceImplTest`、`WorkOrderAllocationManagerImplTest`。配置字段应遵循 `doc/onboarding/entrusted-field-configuration-contract.md`。跨租户 ID、重复建单、非法 recordType、并发转交需覆盖；Maven 环境失败不等于业务通过。

代码/文档差异与未知项：历史任务包不证明当前行为；线上数据量、锁等待阈值和发布流程当前代码无法确认。源码列表为本节类、Mapper/XML、配置契约；最后验证日期 2026-08-26。

## 推荐检索顺序

1. 从 `cube-control-desk-app/.../entrusted/controller` 找路由和动作。
2. 进入 `biz/modular/entrusted/manager` 查编排与工单类型分派。
3. 进入 `biz/modular/entrusted/service` 查主表读写。
4. 进入 `biz/modular/entrusted/strategy/impl/order` 查建单策略。
5. 最后检查 `job/entrusted`、配置 Provider、模型枚举和 api-test。

## 修改前检查

- 新字段是否应通过字段配置契约表达，而不是租户硬编码。
- 查询共享表是否带 `work_order_type`。
- 是否同时覆盖邮件来源、对话来源和人工操作。
- 是否误改了 BILL 入口或主表。
- 是否需要同步更新真实 api-test 场景。

## 代码优先

`doc/wiki/module-entrusted.md` 适合快速建立术语和目录认识；具体行为以当前 Java 调用链、实体/枚举与测试为准。设计文档若只描述目标，不得直接当作已发布行为。
