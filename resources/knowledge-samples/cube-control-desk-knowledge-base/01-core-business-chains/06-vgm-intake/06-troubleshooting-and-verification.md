---
title: 接单侧 VGM Intake 排障与验证
module: vgm-intake
doc_type: troubleshooting-and-verification
audience: backend-testing-ops
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---

# 接单侧 VGM Intake 排障与验证

- 无法创建：查 `VGM_SUBMISSION` 配置、BL 状态、重复未关闭 VGM 和 precheck。
- 保存后数据不对：查 `vgm_detail` 快照及账号同步，不要只看 `vgm_info`。
- 提交后无结果：查 VGM Input taskNo、`BLCallbackController` dataType、`VgmCallbackManager` 和幂等状态。
- 联合提交未生成 VGM：查是否为 `SUBMITTED_CARRIER`、最新快照是否 `CONTAINER_AND_VGM`；预览件成功不会生成。

现有 VGM 相关 Controller/Manager 测试可验证局部行为；新增主链场景时需覆盖独立创建、保存、提交回调和联合投影。

## 故障矩阵

| 现象 | 首查证据 | 可能断点 |
| --- | --- | --- |
| 页面无创建按钮 | capability、客户/船司、BL 状态 | combined/standalone 配置未命中 |
| createFromBill 重复/失败 | lock key、来源 BL、未关闭记录 | 锁外并发、重复判断、Mongo 保存失败 |
| 保存后账号或详情不一致 | `vgm_info.account`、`vgm_detail` | 双存储部分成功、旧详情覆盖 |
| 独立提交卡住 | VGM id、taskNo、Input record/receipt | 下发失败、回调未到、taskNo 无法定位 |
| 联合提交不投影 | BL 状态、最新操作类型、vgmSubmitMode | 仅预览成功、非 CONTAINER_AND_VGM |
| 重复投影 | sourceTaskNo、projection 日志 | 幂等键为空/变化、并发查询插入 |

## 排查顺序与证据边界

按登录租户 → `vgm_info` 当前态 → `vgm_detail` → 来源 BL 快照 → VGM Input taskNo → `BLCallbackController`/CallbackManager → 操作日志建立时间线。不要用通道表状态直接覆盖接单当前态，也不要因 HTTP 200 忽略业务 code。

本轮仅完成源码、SQL、配置结构和测试入口静态核验；没有执行生产官网、真实回调和 api-test。文档与设计差异及生产配置应继续登记。面试追问：如何区分配置问题、异步延迟和跨存储漂移，如何安全重放已在官网成功的回调。
