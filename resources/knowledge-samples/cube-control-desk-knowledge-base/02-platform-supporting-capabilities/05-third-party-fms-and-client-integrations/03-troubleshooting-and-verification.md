---
title: 三方 FMS 与客户端集成排障
module: third-party-fms-and-client-integrations
doc_type: troubleshooting-and-verification
audience: backend-ops-test
last_verified: 2026-08-26
---

# 排障与验证

## 最小验证矩阵

| 输入/响应 | 需要确认 |
| --- | --- |
| HTTP 403 HTML | 原始 status、Content-Type，避免误诊 JSON |
| 超时 | taskNo、上游回执、本地重试，不能直接重提 |
| 空 body | client 解析分支和 requestId |
| 业务失败 code | Handler 是否推进失败态 |
| 重复回执 | 条件更新、历史记录和副作用是否幂等 |

先跑 client/provider 单测，再用 mock 外部服务覆盖上述分支；生产网关、密钥、熔断和告警阈值当前代码无法确认。

按调用入口、租户/cid、carrier/channel、taskNo、client 日志、HTTP 状态和业务回执建立时间线。`Unexpected character '<'` 优先判断上游 403/HTML，不要把解析异常当 JSON 合同问题；空 body、超时、连接拒绝和业务 code 失败分别记录。配置问题查 `THIRD_API`、`VGM_SUBMISSION`、`BizBookingCarrierConfig` 缓存及账号归属。

验证优先使用 client/provider 单测和 Bill/VGM API 场景，再做 mock 外部服务的成功、HTTP 4xx/5xx、空响应、超时、重复回执测试。当前代码无法确认生产外部 SLA、重试/熔断、密钥托管和告警阈值；这些是运维确认项。证据：`BillClient`、`SysConfigThirdApiConfigProvider`、FMS Controller、相关 test 与 `sql/third-party/`；最后核验 2026-08-26。
