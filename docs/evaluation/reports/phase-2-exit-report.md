# 阶段 2 退出报告

- 日期：2026-07-30
- 范围：评估集资产化（M1）

## 退出条件对照

| 退出条件 | 状态 | 证据 |
|----------|------|------|
| 数据集 CRUD | 通过 | `EvalDatasetController` + `EvalDatasetServiceImpl` |
| 草稿版本创建/复制/归档 | 通过 | createDraft / copy / archive |
| Case 分页查询 | 通过 | `GET .../dataset-versions/{id}/cases` |
| JSONL/JSON 导入与逐行问题 | 通过 | `importCases` + `EvalCaseImportSupport` |
| 版本校验与发布不可变 | 通过 | validate / publish；仅 DRAFT 可改 |
| 导出 JSONL | 通过 | `GET .../export` |
| Admin 页面 | 通过 | `/admin/evaluations/datasets*` |
| ADMIN 隔离 | 通过 | `StpUtil.checkRole("admin")` + RequireAdmin |
| feature flag | 通过 | `@ConditionalOnProperty workbench-enabled` |

## 关键路径

- 后端：`bootstrap/.../rag/evaluation/controller|service|support`
- 前端：`frontend/src/pages/admin/evaluations/`、`frontend/src/services/evaluationService.ts`
- 单测：`EvalCaseImportSupportTest`

## 状态区分（简要）

同名 `ARCHIVED` 在不同表含义不同；**Run 能否引用只看版本状态**。口径见 [phase-0-adr.md](../design/phase-0-adr.md) §3–4。

### 评估集 `t_eval_dataset.status`

| 值 | 含义 |
|----|------|
| `ACTIVE` | 列表默认可见 |
| `ARCHIVED` | 列表默认隐藏；数据与版本保留；**不**参与 Run 引用校验 |

前端列表「归档」改的是这一层。

### 版本 `t_eval_dataset_version.status`（权威）

| 值 | 可改 Case | 可被新 Run 引用 |
|----|-----------|-----------------|
| `DRAFT` | 是（导入 / 增删改） | 否 |
| `PUBLISHED` | 否（不可变快照） | 是 |
| `ARCHIVED` | 否 | 否（历史 Run 仍保留对该版本的引用） |

`DRAFT` → 校验通过后发布 → `PUBLISHED`；不再使用可归档。改已发布内容须**复制为新草稿**后再改。

### Run `t_eval_run.status`（执行态，阶段 3+）

进行中大致跟阶段走：

`PENDING` → `RECORDING` → `DETERMINISTIC_SCORING` →（可选）`RAGAS_SCORING` → `REPORTING`

互斥终态：

| 终态 | 含义 |
|------|------|
| `COMPLETED` | 该跑的都成功，报告完整 |
| `PARTIAL_SUCCESS` | 有样本失败，但至少 1 条成功且有可用自建报告 |
| `FAILED` | 任务级失败，或 0 成功且无可用报告 |
| `CANCELLED` | 用户取消；已录制数据保留 |

另有 `currentPhase`：进行中阶段；终态由 `status` 表达。

### 其它（便于对照）

| 对象 | 作用 |
|------|------|
| Record 状态 | **样本级**录制结果；失败重试只重跑失败样本 |
| Score batch 状态 | 自建 / RAGAS 批次自身成败；RAGAS batch 失败不一定把整个 Run 打成 `FAILED` |
| Override / verdict | 人工改分是否生效（`ACTIVE`/`REVOKED`）；阈值判定（`PASS`/`FAIL`/`WARN`/`NOT_EVALUATED`）——不是生命周期状态 |

一眼区分：评估集归档 = 藏列表；版本归档 = 不能新建 Run；Run 状态 = 这次跑到哪；Record 状态 = 这一条成不成。

---

## 使用指南（以 ragenteval 现有数据为例）

### 1. 前置条件

1. 已执行评测建表（阶段 1），任选其一：

   ```bash
   psql -U postgres -d ragent -f resources/database/evaluation/schema_eval_workbench.sql
   # 或升级入口
   psql -U postgres -d ragent -f resources/database/upgrades/v1.1.0/260730_eval_workbench.sql
   ```

2. 配置开启工作台：`ragent.eval.workbench-enabled: true`（本地 `application.yaml` 默认已开）。
3. 后端与前端已启动，使用 **admin** 账号登录管理台。

### 2. 推荐数据文件

| 用途 | 路径 | 规模 |
|------|------|------|
| 冒烟（先跑通） | `D:\code\ragenteval\eval\rag\dataset\eval_set_v1.jsonl` | 约 20 条 |
| 全量 baseline | `D:\code\ragenteval\eval\rag\dataset\eval_set_v1_all.jsonl` | 约 150 条 |

文件为 **snake_case**（`query_id` / `requires_rag` / `expected_doc_ids` / `expected_doc_ids_nice` / `eval_metrics` 等）。工作台导入层已兼容，可直接上传，无需先转成 camelCase。

样例一行（节选）：

```json
{"query_id":"F1-01","query":"我的扫地机充不进电了","intent_l1":"FEEDBACK","intent_l2":"F1_故障报告","difficulty":"easy","requires_rag":true,"expected_doc_ids":["FAQ_VAC_001","CODE_VAC_001","MANUAL_VAC_001"],"ground_truth":"..."}
```

### 3. UI 操作步骤

1. 打开 **Admin → RAG 评测**（路由：`/admin/evaluations/datasets`）。
2. **新建评估集**，例如：
   - 名称：`比特严选-v1`
   - 业务域：`ragent-test`（可选）
3. 进入该评估集；创建时会自动带草稿版本 `v1`（也可再点「新建草稿版本」）。
4. 进入草稿版本详情页，点 **导入**，选择 `eval_set_v1.jsonl`。
5. 查看导入结果：成功数 / 错误数 / 警告数，以及问题明细表。
6. 点 **校验**，确认是否 `publishable`。
7. 点 **发布** → 状态变为 `PUBLISHED`（样本不可再改）。
8. 需要时点 **导出**，下载 camelCase JSONL。

发布后的版本供后续阶段 3「创建 Run」引用；若要改样本，在版本列表对已发布版本执行 **复制为新草稿**，再导入/编辑后重新发布。

### 4. 等价 API 示例（PowerShell）

Base URL 以本地为例：`http://localhost:9090/api/ragent`。

```powershell
$token = "<你的 token>"
$headers = @{ Authorization = $token }
$base = "http://localhost:9090/api/ragent/admin/evaluations"

# 1) 建评估集（自动创建草稿 v1）
$datasetId = (Invoke-RestMethod -Headers $headers -Method Post `
  -Uri "$base/datasets" -ContentType "application/json" `
  -Body '{"name":"比特严选-v1","domain":"ragent-test"}').data

# 2) 查版本，取 versionId
$versions = (Invoke-RestMethod -Headers $headers `
  -Uri "$base/datasets/$datasetId/versions").data
$versionId = $versions[0].id

# 3) 导入 ragenteval 20 条集
$form = @{ file = Get-Item "D:\code\ragenteval\eval\rag\dataset\eval_set_v1.jsonl" }
Invoke-RestMethod -Headers $headers -Method Post `
  -Uri "$base/dataset-versions/$versionId/import" -Form $form

# 4) 校验 + 发布
Invoke-RestMethod -Headers $headers -Method Post `
  -Uri "$base/dataset-versions/$versionId/validate"
Invoke-RestMethod -Headers $headers -Method Post `
  -Uri "$base/dataset-versions/$versionId/publish"
```

主要接口一览：

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/admin/evaluations/datasets` | 新建评估集 |
| `GET` | `/admin/evaluations/datasets/{id}/versions` | 列出版本 |
| `POST` | `/admin/evaluations/dataset-versions/{versionId}/import` | 上传 JSONL/JSON（`multipart` 字段名 `file`） |
| `POST` | `/admin/evaluations/dataset-versions/{versionId}/validate` | 校验 |
| `POST` | `/admin/evaluations/dataset-versions/{versionId}/publish` | 发布 |
| `GET` | `/admin/evaluations/dataset-versions/{versionId}/export` | 导出 JSONL |
| `GET` | `/admin/evaluations/dataset-versions/{versionId}/cases` | 分页查样本 |

### 5. 预期现象与处理

| 现象 | 含义 | 处理 |
|------|------|------|
| 导入成功约 20 条 | 与 `eval_set_v1.jsonl` 对齐 | 可继续校验、发布 |
| `DOC_UNRESOLVED` 警告 | 当前知识库没有对应 `doc_name`（去后缀业务码） | **不挡导入**；正式评测前用 ragenteval 的 `eval/rag/init` 灌库并对齐映射 |
| `INTENT_UNMAPPED` 警告 | `intent_l2` 不在当前意图树 | **不挡导入**；按 ragenteval 意图树脚本对齐 |
| `QUERY_ID_DUPLICATE` / 缺必填等 ERROR | 该行未入库 | 修 JSONL 后重新导入（草稿导入会替换该版本样本） |
| 发布后不能再导入 | 已发布版本不可变 | 复制为新草稿后再改 |
| 接口 404 | `workbench-enabled=false` 或未登录/非 admin | 打开开关并用 admin 登录 |

### 6. 建议节奏

1. 先用 **20 条** `eval_set_v1.jsonl` 跑通导入 → 校验 → 发布。
2. 通了再导入 **150 条** `eval_set_v1_all.jsonl`（建议新建草稿版本或新评估集，避免覆盖冒烟集）。
3. 环境与文档/意图对齐后，再进入阶段 3 创建评测 Run。
