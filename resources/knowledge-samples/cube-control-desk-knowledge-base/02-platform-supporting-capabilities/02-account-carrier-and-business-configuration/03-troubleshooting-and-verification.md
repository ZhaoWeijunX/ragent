---
title: 账号船司与业务配置排障验证
module: account-carrier-business-configuration
doc_type: troubleshooting-and-verification
last_verified: 2026-08-26
---

# 排障与验证

任务未创建或执行器登录失败时，按 `cid → carrier → channel → account → config type` 逐项查库和日志；确认消费方使用 `BizBookingAccount`，再查配置 JSON、有效时间和渠道值。Bill Input 官网当前 channel 为 1 的事实不能推广到其他通道。

验证优先 `BookingAccountServiceTest`、`BookingCarrierConfigServiceTest`，再用 Controller/API 场景核对保存后读取和运行时消费。证据合同：源码为账号/船司 controller、service、entity、enum、Mapper/XML/SQL。代码/文档差异：UI 权限不等于后端租户隔离；未知项：生产密钥、刷新与告警策略当前代码无法确认。最后验证日期 2026-08-26。

现场记录必须包含 `cid/carrier/channel/configType/accountId`、脱敏后的配置版本和消费方日志；不要输出账号密码或完整 token。保存成功但任务仍使用旧值时，重点检查缓存失效和实例配置刷新，这两者的具体实现需以当前运行环境补证。

## 源码对照与边界

账号侧先查 `BizBookingAccountServiceImpl#pageList/getBookingAccountList/getReleaseAccount` 的 cid、carrier、channel 条件。
配置侧区分 `getConfig` 的 JSON 合并、`getBillInputConfig` 的 BILL_INPUT 反序列化和 VGM/Manifest channel 校验。
“查到账号”不等于“第三方认证成功”；需分开记录数据库、配置解析、HTTP 请求和供应商响应证据。
channel 为空、非数字、carrier 大小写差异、空 JSON 和重复 booking_type 应单独测试。
并发编辑是否乐观锁或最后写覆盖，当前 service 未给出明确证据。
账号禁用后在途任务是否继续执行由消费方决定，不能从账号 service 推断。
现场记录需含脱敏组合键、数据库行数、配置版本、消费方法、响应码和状态前后值。
缓存、密钥管理、刷新、告警和重试窗口当前代码无法确认。

## 故障矩阵与验证边界

找不到账号按 cid→carrier→启用状态→子表关联排查；找不到能力按 channel→config type→carrier 配置排查；旧值按数据库→缓存→实例刷新排查；登录失败按脱敏配置版本→账号状态→第三方响应排查。单测证明 service 分支，集成测试证明 Mapper/XML，运行态才证明真实消费。并发编辑、禁用账号与在途任务的策略当前代码无法确认；面试可追问 fail-closed、缓存一致性和密钥保护。
