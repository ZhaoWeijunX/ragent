---
title: 任务与状态机集成配置
module: task-and-state-machine
doc_type: integration-and-configuration
last_verified: 2026-08-26
---

# 集成与配置

业务模块先由 `BizTaskServiceImpl` 创建任务壳，再由 `BizCustomerTaskServiceImpl` 保存租户、命令、业务类型和扩展；回调通过 taskNo/业务 ID 定位后写日志并触发 `TaskBizStatusEvent`。`TaskOpenApiProvider` 是跨服务契约边界，`TaskController` 是管理端入口。调度任务由 `TaskJobManager`/`TaskRecycleJob` 等 Job 扫描。

配置不是单一文件：命令和状态来自 model enum；业务参数在 `BizCustomerTaskExt`/JSON 扩展；具体 Booking/Release 配置由各模块 service 解析。新增命令必须同步 enum、创建方、回调定位、日志类型和测试，不能只改前端下拉值。

证据合同：调用方为 Booking/Release/Bill 等 manager/provider，被调用方为 task service、mapper、状态机；测试 `TaskJobTest`、`CustomerTaskExtServiceTest`。代码/文档差异：任务完成不等于业务成功，业务当前态仍由业务表维护。未知项：线上调度频率、分布式锁实现及失败补偿由当前代码无法确认。源码列表：`TaskOpenApiProvider`、`TaskManager`、`TaskJobManager`、task service/entity/enum、Mapper/XML；最后验证日期 2026-08-26。

## 变更影响

创建任务必须同时确定 `TaskCommandEnum`、`TaskTypeEnum`、`TaskBusinessTypeEnum`：命令选择执行入口，类型用于分类，业务类型决定回调解释。Ext JSON 的 schema 由业务模块拥有，字段演进要兼容历史任务；OpenAPI 传输必须贯穿 cid、taskNo 和业务 ID。任务插入、扩展写入、日志写入是否同事务须以实现注解确认，外部调用绝不能假设可回滚。新增命令还应检查 Job 扫描条件、状态机事件、回调幂等和测试，避免只改前端枚举。

## 源码调用细节

`BizTaskServiceImpl#createTask` 先调用 `taskNoGenerate(typeEnum.v())`，设置类型、名称和 taskNo 后保存主表；它本身不负责下发 Agent。
`BizCustomerTaskServiceImpl#buildCustomerTask` 接收 tenantId、flowType、BizTask，并保存租户任务壳；`getByTaskNo` 是跨回调定位的重要查询。
`TaskOpenApiProvider#booking`、`outputs`、`outputsV2`、`releaseSpace` 和 `dispatch` 是契约入口，分别连接业务 OpenAPI、任务输出和 RPA 派发。
`TaskJobManager` 被 `TaskRecycleJob` 调用，负责回收/扫描逻辑；新增状态必须检查这些查询条件，否则任务可能永久停留。

```mermaid
flowchart LR
    A[业务Manager/Provider] --> B[BizTask]
    B --> C[BizCustomerTask]
    C --> D[CustomerTaskExt JSON]
    D --> E[Agent/RPA/外部执行]
    E --> F[TaskOpenApiProvider回调]
    F --> G[日志+业务状态机]
```

## 配置演进与一致性

命令和状态是 model enum，扩展 JSON 则由 Booking、Release、Bill 等业务拥有；通用任务层不应解释业务字段。
新字段要考虑历史任务反序列化、默认值、字段版本和未知字段容忍度；当前代码未显示统一 schema version，属于限制。
创建主任务、租户任务、扩展和业务表可能跨 service 分步完成，事务边界必须逐个调用方核对。
外部请求成功但回调丢失时，Job 回收和业务补偿是否能自动恢复，当前源码无法确认。
并发扫描下的 claim/锁语义也无法从这些类确认；验证时要观察同一 taskNo 是否被多个执行器领取。

## 核验与面试

测试使用 `TaskJobTest`、`CustomerTaskExtServiceTest`，并结合对应 Booking/Release/Bill API 验证 taskNo、cid、业务主键和最终业务表。
需覆盖空 taskNo、未知命令、扩展 JSON 解析失败、回调重复、业务记录已关闭和外部超时等边界。
代码事实是任务状态与业务当前态分离；“任务成功必然业务成功”是错误推断。
面试可追问为什么用 taskNo 而非数据库自增 ID 做跨系统关联、如何实现至少一次回调幂等、以及外部调用不可回滚时如何补偿。
