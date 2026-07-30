# PostgreSQL 数据库脚本

## 全量初始化

- `schema_pg.sql`：最新版本的完整表结构
- `init_data_pg.sql`：最新版本的初始化数据

新环境直接按顺序执行这两个文件，不需要再执行历史升级脚本

## 模块脚本

- `evaluation/`：RAG 评测工作台建表权威脚本（`schema_eval_workbench.sql`），与 `schema_pg.sql` 中评测表段落保持同步；说明见 [`evaluation/README.md`](evaluation/README.md)

## 增量升级

`upgrades/` 按正式发布版本划分目录。当前正式版本是 v1.0，因此开发期间产生的 v1.1.0 数据库升级脚本统一放在 `upgrades/v1.1.0/`

目录内的脚本使用首次提交日期和变更含义命名：

- 默认格式：`yyMMdd_变更含义.sql`
- 同一天有多个脚本时：`yyMMdd_两位顺序号_变更含义.sql`

已有环境必须按文件名顺序逐个执行，不合并过程脚本。后续正式版本在 `upgrades/` 下新建对应目录并继续使用相同的命名规则

已经对外提供或被其他开发者执行过的升级脚本保持不变，新的数据库变更继续追加独立脚本
