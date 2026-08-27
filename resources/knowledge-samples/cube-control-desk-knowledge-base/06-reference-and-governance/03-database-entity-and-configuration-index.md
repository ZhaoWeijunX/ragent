# 数据库、实体与配置索引

> 最后核验：2026-08-26；当前检索到约 177 个 `@TableName` 实体，本文按领域收敛。

| 领域 | 当前态/主数据 | 详情/历史/辅助 | 关键配置 |
| --- | --- | --- | --- |
| SHIPPING | `entrusted_work_order`、`entrusted_info` | mail/chat record、collaborator、Mongo/附件 | customer agent/rule、tenant hook |
| Booking | `biz_advance_booking`、ext/container | `biz_task`、`biz_customer_task`、回调/日志 | `biz_booking_account`、carrier/booking config |
| Release | 复用 Booking 当前态字段 | api/website/mail/asta monitor、`biz_release_result_record` | tenant releaseConfig、RELEASE carrier config |
| BL Intake | `bl_entrusted_info`、`bl_work_order` | BL detail/field source Mongo、exception、VC log | `THIRD_API:BILL_DESK:*`、mail mapping |
| Bill Input | `biz_bill_record` | Mongo record、file、submit-check、schedule | `CarrierConfigTypeEnum.BILL_INPUT`、account |
| VGM Intake | `vgm_info` | Mongo `vgm_detail`、operation snapshot | `VGM_SUBMISSION:*`、Bill fieldConfig |
| VGM Input | `biz_vgm_record`、`biz_vgm_container` | Mongo `ODS_VGM_RECORD` | account、carrier/channel config |
| Manifest | Intake 主表；Input `biz_manifest_record` | detail、task、monitor/operation | MANIFEST_INPUT carrier config |
| Platform | task、retry、operation log、middle_* | Redis/Mongo/OSS/file/queue | Spring profile、sys/tenant config、scripts |

## 数据边界

MySQL 当前态用于列表和状态判定；Mongo 详情/快照保存复杂结构；OSS 由 fileKey 关联；Redis 只作缓存和协调。历史记录不是当前态真源。跨存储修改必须核对 Convert、幂等和补偿。

## 配置定位

从读取方法反查配置，而不是只搜 key：`BizBookingCarrierConfigService`、TenantConfig、SysConfig Provider、BookingAccountService、ScriptService。配置表有记录不保证生产缓存已刷新或外部能力可用。

## 证据与未知项

来源：实体 `@TableName`、Mongo Document、Mapper/XML、`sql/`、配置 Service/Enum。实际生产 schema 版本、数据质量、索引和 TTL 当前代码无法确认。

