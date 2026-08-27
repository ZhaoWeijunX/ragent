# 代码入口索引

> 最后核验：2026-08-26。当前 app 中约有 104 个 Controller；本文只列主入口族。

| 业务/能力 | HTTP/OpenAPI 入口 | 编排入口 | 继续追踪 |
| --- | --- | --- | --- |
| SHIPPING | `WorkOrderCreateController`、`WorkOrderController`、Entrusted Mail/Record Controller | `WorkOrderManagerImpl`、OrderCreate/Record Strategy | entrusted Service、Job、Groovy Hook |
| Booking | `BizCommandBookingController`、AdvanceBooking/V2 Controller、`ICommandBookingOpenApi` | `BizCommandBookingManager`、`CommandBookingOpenApiProvider` | Booking stateMachine、任务、回调 |
| Release | `CabinManageController`、Booking OpenAPI 回调 | `CommandBookingOpenApiProvider`、`AbstractReleaseStrategy` | API/Website/Email/ASTA Job/Strategy |
| BL Intake | `BLEntrustedInfoController`、`BLCallbackController`、`BlOpenApiController` | `BLEntrustedInfoManagerImpl`、`BillOfLadingCallbackManager` | BL Service、Mongo、`BillClient` |
| Bill Input | `BizCommandBillInputController`、`ICommandBillInputOpenApi` | `BizCommandBillManager`、`CommandBillInputOpenApiProvider` | Processor、Rule、Handler、File Job |
| VGM Intake | `VgmInfoController`、`VgmConfigController` | `VgmInfoManagerImpl`、`VgmCombinedProjectionManager` | VgmDetail/Config/Callback |
| VGM Input | Command/VGM OpenAPI | VGM Processor/Dispatch/Receipt | `biz_vgm_*`、poll/timeout Job |
| Manifest Intake | `app/modular/manifest` Controller | Manifest Intake Manager | 接单表/Mongo/导入 |
| Manifest Input | Manifest internal OpenAPI/Controller | `ManifestSubmissionService` | Rule/Dispatch/Receipt/Monitor |
| Tracking | Trace/订阅 Controller、Job | trace Service/Strategy | 状态映射、通知、第三方 |
| Plan/Schedule | plan/schedule Controller、Job | Plan Manager/Service | 监控、船期与 Booking 关联 |

## 非 HTTP 入口

- `@XxlJob`：邮件拉取、任务下发、回调监控、文件、重试、统计、同步等；当前检索到约 75 个含注解的类。
- MQ/DB Queue listener：`component/middle/model/queue` 及业务 listener。
- Feign/OpenAPI provider：`cube-control-desk-api` 契约在 biz 中实现。
- Groovy/Native SQL：只作为受控运维/租户扩展入口，不是普通业务 API。

定位时必须继续追主数据、配置、下游与回调；索引只提供起点。生产网关路由和前端调用版本当前代码无法完全确认。

## 来源、边界与未知项

来源为 `cube-control-desk-app` Controller、`cube-control-desk-biz` Manager/Provider/Job/Listener、`cube-control-desk-api` 契约、`cube-control-desk-model` 实体枚举及 `AGENTS.md` 检索索引。本文只给入口族，不代替各模块文章中的方法级调用链；生产网关映射、前端实际调用版本和调度平台配置属于当前代码无法确认项。
