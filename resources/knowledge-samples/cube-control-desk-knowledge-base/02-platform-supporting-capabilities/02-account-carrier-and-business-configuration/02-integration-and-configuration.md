---
title: 账号船司与业务配置集成配置
module: account-carrier-business-configuration
doc_type: integration-and-configuration
last_verified: 2026-08-26
---

# 集成与配置解析

业务调用方通过 `BizBookingAccountService` 按租户、船司、账号取账号及邮箱，通过 `BizBookingCarrierConfigService` 取 `CarrierConfigTypeEnum` 对应 JSON/结构化配置；Controller 负责维护，Provider/Manager 负责运行时消费。Bill Input 的通道配置读取 `getBillInputConfig(cid, carrier, channel)`，Release 使用 RELEASE 类型，实际默认值以 service 实现为准。

修改配置需同步 controller DTO、service 校验、entity/Mapper/XML、缓存失效和消费方。账号缺失、carrier/channel 不支持、配置 JSON 非法都必须保留可定位错误；不要把前端隐藏按钮当安全边界。

证据合同：入口 controllers、`BizBookingAccountServiceImpl`、`BizBookingCarrierConfigService`、entity/enum/SQL；测试为 `BookingAccountServiceTest`、`BookingCarrierConfigServiceTest`。代码/文档差异：当前 desk 账号模型替代旧租户账号模型。未知项：加密、缓存 TTL、配置回滚当前代码无法确认。最后验证日期 2026-08-26。

## 源码核验要点

`BizBookingAccountServiceImpl#add/updateAccount/deleteAccount` 带事务并处理用户、标签、邮箱等关联。
`pageList` 使用登录租户构造 cid 条件，运行时查询还要确认 carrier、channel 和启用状态。
`BizBookingCarrierConfigServiceImpl#getConfig` 按 cid、carrier、booking_type 查询，并合并多条 JSON 配置。
`getBillInputConfig` 明确使用 BILL_INPUT 类型；VGM/Manifest 方法额外解析 channel 数组。
配置多行时合并顺序若无显式优先级会造成字段来源不透明，这是当前实现风险。
缓存失效、TTL、配置版本和在途任务是否快照化，当前代码无法确认。
新增配置应覆盖 JSON 非法、channel 不支持、类型隔离、账号禁用和第三方超时。
前端隐藏按钮不是后端租户隔离边界，必须核对 service 查询条件。

## 事务与失败语义

账号保存要检查 cid 归属、启用状态及子表关联；船司配置要保留 carrier、channel、`booking_type` 组合，避免 RELEASE 与 BILL_INPUT 覆盖。消费端应记录使用的配置版本。应区分无账号、无能力、JSON 非法和第三方登录失败。当前代码无法确认 outbox、缓存 TTL 和配置回滚；保存成功而运行时旧值时需分别核对数据库、缓存和实例刷新。面试追问包括配置优先级、缓存击穿、密钥脱敏和外部调用失败后的补偿。
