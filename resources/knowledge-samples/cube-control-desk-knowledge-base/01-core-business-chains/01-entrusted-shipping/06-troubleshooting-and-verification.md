---
title: SHIPPING 常见排查与验证
module: entrusted-shipping
doc_type: troubleshooting-and-verification
audience: backend-testing-ops
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---
# SHIPPING 常见排查与验证

## 证据合同与实现核验

目的为按入口、分派、数据和权限逐层取证；非目标是把日志猜测当运行态结论。排障主证据链：`WorkOrderCreateController` → `EntrustedRecordManager`/helper → `EntrustedOrderCreateFactory` → SHIPPING strategy → `EntrustedWorkOrderService`/`EntrustedInfoService`；操作链为 `WorkOrderController → WorkOrderManagerImpl`。应同时查来源记录、`work_order_type`、工单主表和协作表。

测试证据：`WorkOrderContextResolverRegistryTest`、`WorkOrderPageQueryServiceImplTest`、`WorkOrderAllocationManagerImplTest`；运行态证据目录 `../api-test/scenarios/entrusted`。当前风险是共享入口误分派、跨租户查询、通知与事务不一致、重复建单幂等性不足；生产 traceId、告警阈值和人工补偿当前代码无法确认。

代码/文档差异：HTTP 200/页面显示不能单独证明数据库成功；历史说明需回到当前 Java/SQL 核验。源码列表为上述 controller、manager、strategy、service、mapper/entity 及测试；最后验证日期 2026-08-26。

## 邮件未生成工单

按顺序检查：邮件 Job 是否执行、邮件游标和 `entrusted_mail_record` 是否落库、记录 `work_order_type` 是否为 SHIPPING、Agent 状态是否停在 `PENDING/PROCESSING`、建单策略是否产生 `entrusted_work_order` 与 `entrusted_info`。

## 工单动作无效

确认请求是否进入 `WorkOrderController` 对应方法，再追 Manager 的批量结果和 Service 的状态更新；不要只看前端按钮。审核、去订舱、重试等动作可能要求特定当前状态。

## 去订舱后结果异常

先在 SHIPPING 侧确认任务是否创建，再转到 Booking 文档检查 `BizCustomerTask`、状态机和 `bookingCallback`。订舱执行结果回调不是邮件建单结果。

## 验证证据

优先使用 `../api-test/scenarios/entrusted/email_mapping_api_test.yaml` 以及 `../api-test/scenarios/bill-desk/test_entrusted_mail_record.yaml`、`test_entrusted_reply_mail_send_log.yaml` 中与共享入口相关的场景；若场景只覆盖邻接行为，应在报告中明确覆盖范围。
