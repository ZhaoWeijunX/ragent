---
title: 校验策略与扩展点排障
module: validation-strategy-and-extension-points
doc_type: troubleshooting-and-verification
audience: backend-test
last_verified: 2026-08-26
---

# 排障与验证

## 五段定位

将失败拆为 formData 到 DTO、配置、策略分派、规则执行、clean/回执五段，记录实际策略类名、cid、carrier、channel、提交阶段和配置快照。

验证要断言错误层级、字段定位和最终任务状态，不应只断言抛异常。动态规则版本化和历史重放能力当前代码无法确认。

校验未生效先记录 carrier、channel、实际策略类名、配置快照和 TEMP/正式模式；再区分转换为空、通用规则失败、船司规则失败、clean 失败和回执失败。若使用 `@GroupSequence`，不要误认为多个 group 是执行顺序；动态租户/船司规则需看 Processor/context。

验证覆盖官网 channel=1、未知 carrier、空 formatData、非法字符、跨字段冲突、TEMP 跳过正式规则、clean 返回空/失败以及策略回退。已有 `COSCOWebVgmInputRuleStrategyTest`、`BillInputRuleStrategyVgmTest` 等只能证明局部规则；代码无法确认生产配置覆盖率和规则变更发布流程。最后核验 2026-08-26。

## 分层排障矩阵

| 层次 | 典型现象 | 需要记录的证据 |
| --- | --- | --- |
| 请求/转换 | 页面有值但 DTO 为空 | 原始 formData、字段路径、转换后 DTO |
| 配置 | 某租户显示或必填规则不同 | cid、carrier、channel、fieldMapping/config 快照 |
| 策略分派 | 规则完全未进入 | `RuleTools` 计算出的策略名、Spring Bean 列表 |
| Bean Validation | 错误顺序与预期不同 | 实际 groups、是否存在 `@GroupSequence`、ConstraintViolation |
| 跨字段/集合 | 单字段合法但组合非法 | 箱列表、相关字段组合、策略返回的业务错误 |
| clean/下发 | 校验通过但任务失败 | clean 请求响应、标准 payload、任务号和回执 |

验证不能只断言“抛异常”，还要核对错误属于哪一层、错误字段能否被前端定位、TEMP 与正式提交是否执行了不同规则。未知 carrier/channel 应明确返回不支持，而不是落入无规则路径；配置缺失、配置为空和配置格式错误需要分别覆盖。对于动态规则变更，建议用旧快照重放一条历史单，确认新版本不会改变已提交事实；当前仓库无法确认线上是否保存完整规则版本，因此该项属于运行与治理缺口。

面试深挖通常会追问：为何不用注解解决全部规则、多个 validation group 是否有顺序、策略模式如何避免 Bean 命名脆弱、动态配置如何版本化。回答应先结合 Processor 拥有运行上下文、策略拥有船司差异这一项目事实，再解释 Bean Validation 元数据、Spring 策略注册和历史重放一致性。
