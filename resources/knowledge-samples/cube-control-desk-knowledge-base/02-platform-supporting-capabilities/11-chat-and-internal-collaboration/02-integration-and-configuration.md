---
module: chat-and-internal-collaboration
title: Chat 与内部协同集成配置
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-code-config-and-sql
---

# 集成与配置

调用方先构造 `ChatRecordParam`（sceneCode/sessionType/chatMsg/saveType/version），`ChatManager.checkChatMsg` 查询 `BizApplicationSceneEntity`；业务任务可传 `BizTask` 作为 externalId。saveType=1 保存输入和输出，2 仅输入，3 仅输出。输出响应由 `MsgContentResult.direction/title/code` 序列化为 JSON。

异步输出要求先存在 USER_SEND 记录，`saveOutPutChatMsg` 按 taskId 查找并复用其 chatSessionId；查不到则返回 null。最新会话接口强制使用当前登录 userId，调用方不能用请求体覆盖。场景、会话类型和 record type 的值以枚举/数据库为准。当前代码无法确认 chat 表 DDL、全文检索、消息推送和跨设备同步配置。

源清单：BizChatSessionController、ChatManager、IBizApplicationSceneService/IBizChatSessionService/IBizChatRecordsService、ChatRecordParam/MsgContentResult、Chat enums。

## 调用方集成顺序

同步业务入口通常调用 `saveChatMsg`：它会按 saveType 同时或分别写输入/输出，并将 `BizTask.id` 写入 externalId。异步入口应先调用 `saveInPutChatMsg`，待任务创建或回执结果返回时再调用 `saveOutPutChatMsg`；后者只根据 taskId 和 USER_SEND 记录定位会话，调用前必须保证 task 非空且输入已成功落库。`BizCommandBookingController`、`BizCommandBillInputController`、`BizCommandVgmInputController`、Trace 与 Notice 等 Controller 都是这种能力的调用方，但各自的任务状态、失败补偿仍由所属业务域负责。

`BizChatSessionController#latestChat` 把请求分页对象转换为 `ChatRecordQueryParam` 后强制覆盖 userId，因此前端不应传“其他用户 ID”期待越权查询。调用方应把 sceneCode、sessionType、saveType、version 看作契约字段：sceneCode 必须在应用场景表存在，sessionType 必须命中枚举，version 被写入输入记录但当前代码无法确认是否参与乐观锁或版本筛选。

## 配置和异常边界

当前仓库未看到 Chat 推送服务器或独立 broker 配置；不要把存储成功写成消息已送达。`MsgContentResult.code` 根据 task 是否存在设置默认成功/错误码，不能替代外部任务的真实业务状态。集成测试至少要断言 records 的 type、externalId、chatSessionId 和 content JSON，而不是只验证 Controller 返回 HTTP 200。文档与代码差异、生产重试、DDL 索引、归档与多端同步均需按实际环境补证。
