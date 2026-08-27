# API、Job 与回调索引

> 最后核验：2026-08-26。索引按职责分组，完整 handler 名以当前 `@XxlJob` 检索为准。

## 核心回调/回执

| 领域 | 入口 | 结果 |
| --- | --- | --- |
| Booking | `bookingCallback` | 更新订舱当前态，可能创建 Release 任务 |
| Release | `releaseSpaceCallback` | 更新放舱当前态、历史与下游推送 |
| Bill Input | `billInputReceipt`、`billInputCheckReceipt`、`billFileReceipt`、`billIdentificationReceipt` | 提交、检查、文件和识别状态收敛 |
| VGM Input | VGM receipt/callback | 通道状态、接单侧投影/错误同步 |
| Manifest Input | `ManifestReceiptManager`、operation receipt | 提交状态、监控与通知 |
| BL Intake | `BLCallbackController`、Bill callback manager | 按 dataType 分派 BL/VGM/Manifest 回调并推进对应状态 |

## Job 族

- 调度下发：`taskDispatchJob`、`rpaFairDispatchJob`、API/Bill/VGM/Manifest dispatch job。
- 超时/异常：Booking/Release/RPA callback monitor、Bill/VGM/Manifest submitting timeout。
- Bill 文件：submit-check send/timeout、file pull/timeout/identification。
- Release：API/Website/Email/ASTA、result obtain、push retry。
- 接单邮件：Entrusted Email/Processing/WaitProcess；BL 对应 Bill Desk Job。
- 三方同步：YunBa、Sgcj、Pengbo、Nlscan、MH8、Jfld。
- 运维/统计：BusinessRetry、TaskRecycle、DataArchive、TaskCount/Runtime、BI summary、Announcement。

## 使用方法与风险

先根据 handler 找扫描条件和参数，再追 Service/Strategy、目标状态和外部副作用。手工执行前评估命中数与幂等。回调排障同时用 taskNo 和业务主键，不要只按日志时间匹配。

生产 cron、executor 和回调网关当前代码无法确认。来源：`@XxlJob` 注解、API 契约、Provider/Manager/Receipt、任务与状态实体。
