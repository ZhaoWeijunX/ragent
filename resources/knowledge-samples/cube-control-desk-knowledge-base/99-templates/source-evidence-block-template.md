# 来源证据块模板

## 核验元数据

- 最后核验：使用文章实际完成源码复核的日期；本模板自身核验于 `2026-08-26`
- checkout/分支：记录实际可追溯的分支名或提交号；不得使用模糊的“当前版本”
- 核验类型：静态源码 / 单元测试 / 集成测试 / api-test / 环境观察
- 结论适用范围：`模块、租户/船司/通道、场景`

## 当前事实

- 入口：`Controller/Job/Provider#method`
- 编排：`Manager/Processor/Handler#method`
- 数据：`table/entity/document/config`
- 下游：`Client/Dispatch/API`
- 回调与最终状态：`Callback/Receipt -> state`

## 证据

| 类型 | 路径/命令示例 | 观察示例 | 能证明 | 不能证明 |
| --- | --- | --- | --- | --- |
| 源码 | `CommandBookingOpenApiProvider#bookingCallback` | 回调先定位任务再推进订舱状态 | 当前控制流与守卫存在 | 生产回调一定到达 |
| SQL/配置 | `sql/retry/biz_business_retry_task.sql` | 定义重试任务持久化结构 | schema 设计与字段语义 | 生产已执行该 DDL |
| 测试 | 记录真实命令、退出码与断言 | 目标场景通过或失败 | 指定 checkout/环境的结果 | 未覆盖的船司和生产数据 |

## 事实边界

- 推断：说明依据和可证伪方式。
- 当前代码无法确认：列所需外部/业务证据。
- 文档/代码差异：列双方描述与影响。
- 敏感信息：只写结构和脱敏结论。
