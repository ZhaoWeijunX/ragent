---
title: 邮件通知推送消息与公告排障验证
module: mail-notice-push-message-announcement
doc_type: troubleshooting-and-verification
last_verified: 2026-08-26
---

# 排障与验证

通知未出现：查业务回调是否调用 `NoticeManager`、模板 strategy 是否命中、接收人和租户是否正确，再查 `SysNotice`/read-status。邮件未发送：分离通知落库、邮件配置、供应商调用和回执；公告异常则追 `AnnouncementController → AnnouncementPushManager`。模板内容错误优先核对状态枚举与 strategy 分支。

测试为 `StatusNoticeTemplateTest`；应补充多租户、空接收人、重复发送、供应商失败和事务回滚场景。证据合同：源码为 notice/announcement controller、manager、strategy、entity/enum/SQL。代码/文档差异：页面已读不能证明外部送达；未知项：线上告警、重试和投递 SLA 当前代码无法确认。最后验证日期 2026-08-26。

取证时分开记录模板选择、通知落库、渠道调用和供应商回执四个时间点；若只存在前三项，结论只能是“已请求发送”，不能写成“用户已收到”。多租户场景必须核对接收人解析使用的 cid。

## 源码对照与异常边界

先确认业务 manager 是否调用 `NoticeManager`，再检查模板 strategy、接收人和 cid。
公告沿 `AnnouncementPushManager#createAnnouncement/sendAnnouncementNotice` 检查 SysNotice、Job、SysWebsiteMsg 和队列。
通知落库成功而供应商超时属于未知送达态，应先查供应商 messageId 再重试。
Job 创建失败、Job 执行失败、队列生产失败是不同故障点，当前代码无法确认统一死信。
重复事件应按事件 ID、noticeId、任务 ID 和渠道消息 ID 对账，不能按正文去重。
`StatusNoticeTemplateTest` 只能证明模板分支，不证明供应商送达、队列消费或用户已读。
报告要区分“已创建”“已请求发送”“收到回执”“用户已读”。

## 故障矩阵与验证边界

无站内信查业务事件→NoticeManager→模板→SysNotice；内容错误查枚举→strategy→参数；邮件未达查配置→渠道调用→回执；重复推送查事件 ID/重试/接收人集合。单测不能证明供应商送达，运行态必须保留通知 ID、渠道响应和回执时间。当前代码无法确认统一幂等与告警。
