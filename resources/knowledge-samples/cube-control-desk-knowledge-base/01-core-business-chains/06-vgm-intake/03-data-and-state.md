---
title: 接单侧 VGM Intake 数据与状态
module: vgm-intake
doc_type: data-and-state
audience: new-backend
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---

# 接单侧 VGM Intake 数据与状态

## 当前态、详情与来源

`vgm_info` 是接单侧当前态真源，实体 `VgmInfo` 保存租户、业务状态、来源类型、来源 BL/工单/记录、账号、taskNo、sourceTaskNo、错误与关闭信息。Mongo `vgm_detail` 保存表单快照，通常包含 `BILL_INFO` 和 `CONTAINER_LIST`；它是 VGM 自己的详情，不是对 BL 详情的实时引用。

来源字段 `source_bl_entrusted_info_id`、`source_bl_work_order_id`、`source_record_id`、`source_task_no` 用于回溯、附件过滤、邮件线程未读数和联合投影幂等。列表附件必须按来源 BL 的 `entrustmentIndex -> idpRecordId/recordId` 过滤，不能展示同一工单其他提单附件。

## 状态与操作边界

状态枚举由 `VgmBizStatusEnum` 定义；人工独立单允许接单、保存、提交、关闭、备注，联合投影记录只表达已提交事实。操作审计写 `sys_operation_log` 与 Mongo snapshot；提交快照中的 `submissionPayload` 保存本次下发的标准数据，一次性截图等证据进入 operation extra data。

`task_no` 关联通道侧稳定 taskNo，用于回调定位；`source_task_no` 用于联合/强制监听投影查重。通道侧 `biz_vgm_record`、`biz_vgm_container`、Mongo `ODS_VGM_RECORD` 不属于本模块，不能用它们直接替代接单列表状态。

## 一致性与风险

- `vgm_info` 与 `vgm_detail` 跨 MySQL/Mongo，源码无法证明原子提交；失败后要能按 VGM id 重建或补偿详情。
- 清空历史错误使用空串而非 null，以避免 MyBatis-Plus 更新策略忽略字段。
- 联合投影先按 sourceTaskNo 查询，再保存当前态和快照；并发唯一性仍应由数据库约束或条件写入加强。
- 保存账号需要同步当前态和详情，但不反写来源 BL。

## 文档差异、未知与来源

旧认知把 VGM 当成 BL 箱明细；当前 `VgmInfo`、Mongo detail 和独立 Controller 证明它是独立当前态。当前代码无法确认生产表唯一索引是否覆盖所有历史数据、Mongo 修复策略和状态保留期限。来源：`VgmInfo`、`VgmInfoServiceImpl`、`VgmDetailServiceImpl`、`VgmInfoManagerImpl`、`VgmCombinedProjectionManager`、VGM DTO/VO/enum、`sql/vgm/`。

面试可追问当前态与快照、稳定业务键与尝试号、跨存储一致性以及事实投影为什么限制人工修改。
