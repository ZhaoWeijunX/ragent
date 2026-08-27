# 常见术语与错误

> 最后核验：2026-08-26。

| 误区 | 正确认识 |
| --- | --- |
| Bill 就是一个模块 | BL Intake 与 Bill Input 是不同域；旧 invoice/OCR 语义需复核 |
| VGM 在 BL 详情里，所以属于 BL | Intake 有独立 `vgm_info`；Input 有 `biz_vgm_record` |
| `SUCCESS_RUN` 代表全链完成 | 只代表订舱执行成功；Release 独立 |
| HTTP 200 就成功 | 还要检查业务 code、body、回执与最终状态 |
| 任务表就是业务表 | 任务可重试/重放，业务当前态另有真源 |
| 多个校验 group 有固定顺序 | 未使用 `@GroupSequence` 时是约束集合，不保证业务顺序 |
| `@Transactional` 覆盖所有副作用 | 只覆盖对应事务管理器内资源 |
| Redisson 锁等于历史幂等 | 锁只协调并发；释放后仍需业务唯一键/状态 |
| 通道枚举存在就支持该通道 | 以策略 Registry/Tools 的实际映射为准 |
| UI 隐藏就是后端权限 | 必须检查 Controller/拦截器/租户条件 |
| YAML 存在就是测试通过 | 必须有新鲜执行输出和断言 |
| 历史设计写了就是已上线 | 当前代码优先，差异进台账 |

定位任何问题时按“入口 → 配置/数据 → 下游 → 回调 → 当前态/历史”追踪，并明确事实、推断和当前无法确认。来源：当前业务索引、状态/配置/策略代码、测试与历史差异。面试追问通常围绕这些误区展开，回答要给具体类、表、状态和失败场景。

## 三个项目化判断例子

### “校验通过”是哪一层

Bill Input TEMP 只执行 `TempGroup` 并保存本地，正式流程才按配置启用 VGM 约束、选择 `{carrier}_WEB` 策略并进行二次 clean。因此不能用 TEMP 成功证明官网字段完整，也不能把多个 group 写成有序业务步骤，除非源码使用 `@GroupSequence`。

### “锁住了”还需要幂等吗

Redisson 锁只协调同一 lock key 的并发窗口；锁释放后相同请求仍可再次进入。Manifest Submission 的已有记录判断、VGM `sourceTaskNo`、任务唯一标识和状态条件更新才提供历史幂等/防回退。锁 key 选错或部分调用方不使用同一锁，保护也会失效。

### “回调失败”能否直接重放

先区分外部未执行、外部执行但本地未收敛、通知失败三种事实。若外部已成功，重新下发可能产生重复官网操作；应优先重放幂等回执或补偿本地状态。Business Retry 只适用于注册场景和 Handler，不是任意 Job 的通用重放按钮。

## 面试回答结构

先说项目具体入口、表和状态，再解释技术原理，最后给失败场景和取舍。例如回答“最终一致性”时，使用 `biz_customer_task → external executor → bookingCallback/releaseSpaceCallback → current state/history`，而不是只背消息队列理论。
