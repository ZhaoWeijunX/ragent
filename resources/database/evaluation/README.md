# resources/database/evaluation/

RAG 评测工作台数据库脚本目录（与通用 `schema_pg.sql` / `upgrades/` 配合使用）。

| 文件 | 说明 |
|------|------|
| [`schema_eval_workbench.sql`](schema_eval_workbench.sql) | **权威建表脚本**：7 张 `t_eval_*` 表（IF NOT EXISTS + 部分唯一索引） |

## 表清单

1. `t_eval_dataset` — 评估集
2. `t_eval_dataset_version` — 版本（DRAFT / PUBLISHED / ARCHIVED）
3. `t_eval_case` — 样本
4. `t_eval_run` — 运行（含租约、tags）
5. `t_eval_record` — 录制
6. `t_eval_score_batch` — 评分批次
7. `t_eval_score` — 指标分数

口径见 [`docs/evaluation/phase-0-adr.md`](../../../docs/evaluation/phase-0-adr.md)。

## 执行方式

**新环境（推荐）**：全量初始化后追加执行本目录权威脚本，或直接使用已同步的 `schema_pg.sql`（含评测表段落）。

```bash
psql -U postgres -d ragent -f resources/database/schema_pg.sql
# 若使用未含评测表的旧 schema，可单独执行：
psql -U postgres -d ragent -f resources/database/evaluation/schema_eval_workbench.sql
```

**已有环境升级**：按 `upgrades/v1.1.0/` 顺序执行。若历史库已建过已取消的 `t_eval_manual_override`，执行：

```bash
psql -U postgres -d ragent -f resources/database/upgrades/v1.1.0/260803_drop_eval_manual_override.sql
```

## 维护约定

- 表结构变更：**先改**本目录权威脚本，再同步 `schema_pg.sql` 与对应 `upgrades/v1.1.0/yyMMdd_*.sql`。
- 不要把评测演示数据混进 `init_data_pg.sql`；评估集导入走工作台 API / 后续 import 脚本。
