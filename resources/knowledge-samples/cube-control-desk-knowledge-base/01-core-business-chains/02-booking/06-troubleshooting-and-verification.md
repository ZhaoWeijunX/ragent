---
title: Booking 常见排查与验证
module: booking
doc_type: troubleshooting-and-verification
audience: backend-testing-ops
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---
# Booking 常见排查与验证

## 证据合同与实现核验

排查顺序：确认入口参数与 `BookingParam` → 查 `BizCustomerTask`/`BizTask` → 追状态机 action → 查外部回执 `bookingCallback` → 对照 `BizAdvanceBooking`/Ext → 再查 Release 配置和任务。没有生成任务通常看账号/carrier config/任务创建分支；外部成功页面不变则看回调定位、重复回调和当前态更新。

验证证据：`BizCommandBookingControllerTest`；主链 api-test 在当前 checkout 不稳定，需记录外部执行器响应、数据库前后状态和 taskNo。风险包括回调幂等不足、跨表状态不一致、配置读取错误和把 SUCCESS_RUN 误当 Release 成功。代码/文档差异：历史任务包不替代当前实现；未知项：生产告警与重放窗口无法由代码确认。源码列表为 controller/provider/manager/state-machine/service/entity/SQL；最后验证日期 2026-08-26。

## 没有生成订舱任务

检查入口参数是否进入 `booking`/`bookingV2`，再查 `BizCustomerTask`、任务命令、账号/船司配置和状态机 create action。

## 外部成功但页面未更新

确认外部结果是否进入对应回调；MH8 场景先查 `Mh8BookingCallbackJob` 是否解析并调用 `bookingCallback`。随后检查 Provider 是否找到 `BizCustomerTask` 和 `BizAdvanceBooking`，以及主表、扩展表是否更新。

## 订舱成功但没有放舱任务

检查 `processBookingRecordWhenBookingCallback`、`needCreateReleaseTask`、租户 `releaseConfig.needCreateReleaseTask` 和船司能力配置。成功回执本身不保证创建 Release。

## 验证范围

当前 checkout 没有稳定、独立的 Booking 主链 api-test 套件；修改 Booking 主链时，不能用邻接 BL YAML 代替真实覆盖，应补充或运行覆盖 Booking/Release 的场景。
