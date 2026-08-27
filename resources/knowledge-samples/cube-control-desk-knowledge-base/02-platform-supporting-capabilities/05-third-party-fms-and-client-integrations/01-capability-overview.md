---
title: 三方 FMS 与客户端集成能力概览
module: third-party-fms-and-client-integrations
doc_type: capability-overview
audience: backend-ops
last_verified: 2026-08-26
---

# 三方 FMS 与客户端集成

## 真实调用链补充

Controller、Job 或 Processor 先解析租户、carrier、channel 与账号，再由 BillClient 或 ClusterOpenApiService 组装协议请求；SysConfigThirdApiConfigProvider 负责第三方配置，不负责业务状态。响应中的 HTTP status、业务 code 和 taskNo 要分别处理，最终由领域 Handler/Callback 收口。

外部调用不在本地事务内，超时后不能证明对方未执行。当前代码无法确认上游幂等、限流、熔断、连接池和生产 SLA，这些必须以运行态证据补充。

### 关键边界

适配层返回成功不等于 BL/VGM 当前态成功；禁止在 client 内直接更新领域主表或把 HTTP 2xx 当作业务成功。

## 作用与边界

本能力承接 desk 到 FMS、Bill、集群及其他第三方服务的协议适配、租户配置解析和结果转换，不拥有业务主状态。代表实现包括 `BillClient`、`ClusterOpenApiService`、`FmsCleanController`、`SysConfigThirdApiConfigProvider` 和客户同步策略工厂。业务 Manager 负责决定何时调用，client 负责 URL、DTO、鉴权、超时和错误边界。

## 运行位置

典型链路是 Controller/Job → 业务 Manager → client/provider → 外部 HTTP 或集群 → DTO/回调 → Handler/状态机。Bill Input 的清洗由 `ClusterOpenApiService.billInputCleanDataApi`，VGM 清洗由 `vgmInputCleanDataApi`；BL/VGM 的 BillClient 负责配置查询、提交与监听。外部返回成功不等于本地业务成功，最终状态仍由领域 Handler 写入。

## 设计、风险与证据

适配层隔离外部协议变化，并允许按租户读取 `THIRD_API:*` 配置；但仓库无法证明外部服务 SLA、连接池和重试参数的生产值。HTTP 403/HTML 被 JSON 解析会产生误导性异常，日志不得泄露 token/账号。已核验 `BillClient`、`ClusterOpenApiService`、`SysConfigThirdApiConfigProvider`、FMS Controller、调用方 Manager/Processor；未知项：第三方限流和熔断策略。最后核验 2026-08-26。
