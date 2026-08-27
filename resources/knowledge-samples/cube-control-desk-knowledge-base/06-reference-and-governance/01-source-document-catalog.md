# 源文档目录

> 最后核验：2026-08-26。本文编目当前仓库证据，不宣称历史文档全部准确。

## 当前入口

| 来源 | 用途 | 可靠性约束 |
| --- | --- | --- |
| `AGENTS.md` | 业务地图、检索顺序、领域边界、验证入口 | 是维护中的项目索引；具体行为仍回源码 |
| `doc/README.md` | onboarding、design、wiki、task-pack 总索引 | 只证明文档存在 |
| `doc/onboarding/` | 快速理解业务入口与常用链路 | 与当前代码冲突时以代码为准 |
| `doc/design/` | 设计目标、API 契约、状态和迁移意图 | 可能包含未上线或已演进内容 |
| `doc/wiki/` | 历史模块地图和技术概览 | 部分路径/语义可能过时 |
| `doc/task-pack/` | 任务分解、迁移与发布步骤 | 历史证据，不单独证明现状 |
| `docs/solutions/` | 已解决问题、根因和验证片段 | 适用具体 checkout/日期，复用前复核 |
| `sql/` | DDL/DML/回滚/修复脚本 | 执行前核对目标环境 schema 与范围 |
| `src/test`、`../api-test/scenarios` | 局部与端到端验证资产 | 文件存在不等于本轮运行通过 |

当前统计仅用于规模感知：`doc/` 约 261 个文件、`sql/` 约 66 个文件；数量会随 checkout 演进，正式引用应指向具体路径和符号。

## 按业务域找文档

- Entrusted：`doc/onboarding/entrusted-*`、`doc/design/entrust/`、`sql/entrust/`。
- Booking/Release：`doc/onboarding/booking-release-*`、`doc/design/booking|release/`、`sql/booking/`。
- BL/Bill：`doc/design/bill/`、`doc/task-pack/bill-*`、`sql/bill/`、`sql/bill-intake-migration/`。
- VGM：`doc/design/vgm/`、`sql/vgm/`。
- Manifest：`doc/design/manifest/` 与相关任务包/SQL。
- 平台能力：`doc/wiki/module-*`、`doc/excel_import_architecture_design.md`、scheduler/middle/ops 相关 solutions。

## 使用规则

引用设计时写明“设计描述”；引用代码时给类/方法；引用测试时给执行结果和时间；引用历史任务包时不得改写成已上线事实。生产配置、外部返回与组织流程当前仓库无法确认。

面试追问：如何建立 source-of-truth 层级、如何处理代码与设计冲突、如何防止文档陈旧。

