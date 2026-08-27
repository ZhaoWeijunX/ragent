---
title: 邮件通知推送消息与公告集成配置
module: mail-notice-push-message-announcement
doc_type: integration-and-configuration
last_verified: 2026-08-26
---

# 集成与配置

业务 Manager 在状态变化后调用 `NoticeManager`，模板策略根据业务状态选择内容，随后写通知/发送渠道；公告由 `AnnouncementPushManager` 从 `AnnouncementController` 接收并面向目标用户推送。邮件配置由 `BizNoticeEmailConfig` 与 `NoticeEmailConfigController` 管理，读取和发送边界以对应 service 实现为准。

修改通知类型需同步 `NoticeTypeEnum`、模板 strategy、接收方解析、已读状态、失败处理和测试。不要在通知层更新业务当前态；不要将站内信已写库解释成邮件已送达。

证据合同：调用方为 Booking/Release/委托 manager，受方为 `NoticeManager`、模板 strategy、SysNotice/邮件 service；测试 `StatusNoticeTemplateTest`。代码/文档差异：通知是副作用，不是状态机。未知项：消息队列、事务后发送及供应商回执当前代码无法确认。源码列表为 controllers/managers/strategies/entities/enum/Mapper；最后验证日期 2026-08-26。

## 源码调用细节

`NoticeManager` 依赖租户配置、IM 群组、白名单、钉钉、网站消息、模板和业务日志服务。
`sendNotice` 保存 `BizRpaImChannelMsg`，并创建 SUPPLY_CHAIN/OCEAN_FUSION 任务与业务日志。
节点通知读取 `TenantConfigValue`，分别判断 inner/outer IM 和 website push 开关。
接收人还要经过 staff、群组和业务关系解析，空集合不能解释为已送达。
公告 `createAnnouncement` 先保存 `SysNotice`，未来发布时间生成 `announcement_push_{noticeId}` Job。
到期后 `sendAnnouncementNotice` 保存 `SysWebsiteMsg`，并生产 `cube_control_desk_websocket_push` 消息。
业务写入、通知落库、渠道请求、供应商回执、用户已读是五个不同时间点。
是否有统一 outbox、重试和补偿，当前代码无法确认；新增通知类型需同步 enum、strategy 和测试。

## 变更影响

新增类型要检查 `NoticeTypeEnum → StatusNoticeTemplateStrategy → 接收人解析 → SysNotice/渠道 service`，并补充空接收人、重复事件、供应商超时和模板参数测试。邮件配置保存与业务状态写入可能不在同一事务；当前代码无法确认 outbox，因此只能以实际调用顺序描述。面试追问应区分通知落库、调用供应商和收到回执三个时间点。
