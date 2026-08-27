---
module: system-tenant-security-and-operations
title: 系统租户安全与运维排障验证
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-code-config-and-sql
---

# 排障与验证

先记录 userId、tenantId、接口路径和业务 ID；检查 LoginContext/UserContext，再查 Service 的 cid 条件和数据库结果。配置异常查 `biz_tenant_config.value` JSON；审计缺失查开关、注解、targetId SpEL、snapshot 保存和 WARN 日志。跨租户风险应以实际 SQL/接口响应证明，不能以 `@ApiPermission(false)` 或前端菜单作结论。

静态定位：`rg -n "TenantConfigController|UserContext|getTenantId|SysOperationLogAspect|ApiPermission|Login\("`；SQL 核对 `sys_operation_log` 表。当前未执行生产身份、网关策略和真实数据库查询，无法确认线上授权、密钥轮换或告警阈值。源清单：Controller、认证上下文、切面/服务、异常拦截器及 SQL。

## 安全边界与验证矩阵

前端菜单、路由隐藏和 `@ApiPermission(false)` 只描述入口表现或框架元数据，不能替代 Service/Mapper 的租户条件。验证越权必须使用两个租户的真实业务 ID 交叉请求，并同时观察接口状态、响应体和数据库查询条件。`LoginContext`/`UserContext` 提供当前身份，但只有下游把 cid 写入查询和更新条件时才形成数据隔离。

| 现象 | 优先检查 | 风险判断 |
| --- | --- | --- |
| A 租户能查到 B 数据 | Controller 入参、Service cid 推导、Mapper 条件 | 高风险；不能用 UI 不展示来降级 |
| 配置读取错租户 | `biz_tenant_config` key、cid、缓存键 | 可能造成能力开关和三方凭证串用 |
| 操作成功但无审计 | `@SysOperationLog`、SpEL targetId、切面开关、snapshot | 审计缺失不等于业务事务回滚 |
| 接口 401/403 | 登录态、网关、注解、角色/租户上下文 | 必须保留原 HTTP 状态和响应体 |
| 异常被统一成 500 | 全局异常拦截器和上游响应解析 | HTML 403 被当 JSON 解析会掩盖根因 |

`SysOperationLogAspect` 与业务事务的耦合方式需以具体实现核对：若审计保存失败仅 WARN，则不能宣称审计与业务强一致；若 snapshot 写入 Mongo，还存在 MySQL 与 Mongo 跨存储一致性问题。生产网关规则、账号生命周期、密钥轮换和告警阈值不在当前源码证据范围内，必须由部署配置和运行日志确认。
