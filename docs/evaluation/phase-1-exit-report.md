# 阶段 1 退出报告

- 日期：2026-07-30
- 约束：尽量小范围改动原始代码（旁路 / Chat / MetaPayload **未改**；仅扩展配置、审计常量、MapperScan 与新增 `rag.evaluation` 包）

## 退出条件对照

| 退出条件 | 状态 | 证据 |
|----------|------|------|
| 7 张 `t_eval_*` 建表 | 通过 | [`resources/database/evaluation/schema_eval_workbench.sql`](../../resources/database/evaluation/schema_eval_workbench.sql)（`t_eval_manual_override` 已取消，见 `260803_drop_eval_manual_override.sql`） |
| 已有库升级入口 | 通过 | `resources/database/upgrades/v1.1.0/260730_eval_workbench.sql` |
| 全量 schema 同步 | 通过 | `resources/database/schema_pg.sql` 追加评测表段落 |
| 工作台包骨架 | 通过 | `bootstrap/.../rag/evaluation/**` |
| feature flag 关闭无工作台任务 | 通过 | `ragent.eval.workbench-enabled: false`；`EvaluationWorkbenchConfiguration` 条件装配 |
| 专用线程池 | 通过 | `evalRecordExecutor`（仅 workbench 开启时） |
| 审计类型扩展 | 通过 | `BizChangeBizType.EVAL_*` |

## 建表执行

```bash
# 已有环境
psql -U postgres -d ragent -f resources/database/upgrades/v1.1.0/260730_eval_workbench.sql

# 或权威脚本
psql -U postgres -d ragent -f resources/database/evaluation/schema_eval_workbench.sql
```

## 配置要点

```yaml
app:
  eval:
    enabled: true              # 旁路，与工作台独立
    workbench-enabled: false   # 阶段1默认关；阶段2开 API 前再开
```

## 非目标（留给阶段 2+）

- 数据集 CRUD / 导入发布 API
- Run 状态机与双路径 Runner
- Controller 与前端页面
