---
module: system-tenant-security-and-operations
title: 系统租户安全与运维能力概览
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-code-config-and-sql
---

# 系统租户安全与运维能力概览

该能力为业务模块提供登录用户、租户配置、系统参数、操作入口和异常边界。当前代码常见入口是 `TenantConfigController`（`/api/v1/tenant/config`）、`UserContext`/`LoginContext`、全局异常拦截器和 `ApiPermission`/`Login` 注解。租户配置由 `TenantConfigService` 查询 `BizTenantConfig`，`getCommonConfig` 从当前上下文 tenantId 返回 commonConfig；`getConfig(tenantId)` 可按参数读取配置，不能仅凭前端路由推断安全隔离。

操作审计由 `@SysOperationLog`、`SysOperationLogAspect`、`SysOperationSnapshotService` 和 `SysOperationLogService` 组成：先执行业务，成功后保存快照差异与日志；审计自身异常记录 WARN 且不阻断主链。认证、授权、租户过滤的完整边界分布在框架、Controller 和各 Service，当前代码无法确认所有接口均有统一后端 RBAC。源清单：TenantConfigController、UserContext/LoginContext、log aspect/service、exception interceptors、租户模型/SQL。

## 已验证的请求与审计链

`TenantConfigController` 在类级同时标记 `@ApiPermission(false)`，其中 `/getCommonConfig` 从 `UserContext.getTenantId()` 读取当前租户并只返回 `value.commonConfig`；而 `/getConfig?tenantId=...` 标记 `@Login(false)`，把请求参数直接作为 `BizTenantConfig.cid` 查询条件。这两个接口不是同一个安全语义：前者依赖当前上下文，后者在当前源码中没有在 Controller 层验证调用方是否拥有该 tenantId。文档不能把“接口可用”或前端菜单隐藏描述成资源授权已经成立。

`SysOperationLogAspect#aroundAdvice` 的顺序是先执行被注解方法，再调用 `writeAudit`。后者以 `targetIdExpr` 的 SpEL 取业务 ID，把入参和结果转为 Map，使用 `SysOperationSnapshotDiffer` 计算差异，先保存 Mongo snapshot，再保存 MySQL `sys_operation_log` 行。切面受 `desk.sys-operation-log.enabled` 控制，`matchIfMissing=true`；审计写入失败只 WARN，不会回滚已成功的业务方法。因此它提供的是尽力而为的审计副作用，不是业务与双存储的强一致事务。

## 设计边界、风险与验证

租户隔离必须落在每一条 Service/Mapper 查询和更新的 cid 条件上；`LoginContext`、`UserContext`、`@Login`、`@ApiPermission` 分别只提供身份或入口元数据，不能单独证明数据隔离。改动配置接口时至少要用两个租户的真实记录交叉测试；改动审计时要验证业务成功且 snapshot 失败、snapshot 成功而日志失败、SpEL 无法解析 targetId 三种分支。当前代码无法确认网关是否对 `/getConfig` 另有拦截、生产脱敏规则、密钥轮换和日志归档策略。

## 文档与代码差异

若历史说明将所有租户配置读取描述为“登录用户只能读取自己的配置”，它与当前 `getConfig(Long tenantId)` 的免登录实现不一致；本篇以当前 Controller 为准，并将外部网关策略标为未知而非假定为补偿控制。
