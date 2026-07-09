# biz-security 知识库意图树设计

> 配套导入脚本：[docs/examples/biz-security-intent-nodes-import.sql](../../../docs/examples/biz-security-intent-nodes-import.sql)

本文档描述 `resources/docs/knowledge/biz/biz-security/` 目录下 **8 篇多格式文档** 的意图树划分方案，用于 Ragent 意图分类与定向检索。

---

## 1. 设计目标

| 目标 | 说明 |
|------|------|
| 对齐项目机制 | 遵循 `DOMAIN → CATEGORY → TOPIC` 三层结构，仅 **TOPIC（叶子）** 参与 LLM 分类 |
| 系列边界清晰 | 制度、合规、运营、访问控制四大 CATEGORY 独立，避免跨域误路由 |
| 消歧同名主题 | 「权限」「分级」「等保」「应急」等词汇通过 `description` 区分语境 |
| 单库聚合 | 8 篇文档（含 md/txt/csv/docx/xlsx/pdf/png）同属一个知识库，叶子节点共享 `kbId` 与 `collectionName` |
| 格式覆盖 | 覆盖 Markdown、Tika(txt)、CSV、Excel、MinerU(docx/pdf)、Image(png) 全解析链路 |

---

## 2. 知识库文档清单

| 文档 | 格式 | 解析器 | 说明 |
|------|------|--------|------|
| `信息安全管理制度.md` | Markdown | Markdown | 集团信息安全总则，13 章 + FAQ |
| `数据分类分级规范.docx` | Word | MinerU | 数据 L1～L4 分级标准与标签规则 |
| `等保合规自查报告.pdf` | PDF | MinerU | 2025 年度等保自查与整改计划 |
| `应急响应流程图.png` | PNG | Image | P0/P1 事件处置流程可视化 |
| `安全事件通报模板.txt` | Plain Text | Tika | 初报/续报/终报/对外口径模板 |
| `系统资产清单.csv` | CSV | Csv | 20+ 系统资产编码与等保信息 |
| `权限申请对照表.xlsx` | Excel | ExcelPoi | 角色-权限-审批矩阵 |

---

## 3. 意图树总览

```
biz-security（DOMAIN）
├── sec-policy（安全制度与规范）
│   ├── sec-policy-mgmt              # 信息安全管理制度.md
│   └── sec-policy-classification    # 数据分类分级规范.docx
├── sec-compliance（合规与审计）
│   └── sec-compliance-djcp          # 等保合规自查报告.pdf
├── sec-ops（安全运营）
│   ├── sec-ops-incident             # 安全事件通报模板.txt + 应急响应流程图.png
│   └── sec-ops-assets               # 系统资产清单.csv
└── sec-access（访问控制）
    └── sec-access-rbac              # 权限申请对照表.xlsx
```

**节点统计**：1 DOMAIN + 4 CATEGORY + 6 TOPIC = **11 个节点**

系统交互节点（`sys` / `sys-welcome` / `sys-about-bot`）为全局共用，见 `docs/examples/mcp-intent-nodes-import.sql`，不在本知识库脚本中重复导入。

---

## 4. 节点明细

### 4.1 DOMAIN

| intent_code | name | description |
|-------------|------|-------------|
| `biz-security` | 企业信息安全与合规 | 星云科技集团信息安全制度、数据分级、等保合规、应急响应、系统资产与权限管理知识库 |

### 4.2 CATEGORY：安全制度与规范（`sec-policy`）

| intent_code | name | 覆盖文档 | top_k |
|-------------|------|----------|-------|
| `sec-policy-mgmt` | 信息安全管理制度 | `信息安全管理制度.md` | 8 |
| `sec-policy-classification` | 数据分类分级 | `数据分类分级规范.docx` | 8 |

**典型问法示例**

- `sec-policy-mgmt`：集团信息安全管理的基本原则是什么？
- `sec-policy-mgmt`：安全事件 P0 级别响应时限是多少？
- `sec-policy-mgmt`：外包人员能否接触 L4 数据？
- `sec-policy-classification`：L3 和 L4 数据在存储加密上有什么区别？
- `sec-policy-classification`：个人身份证号应该定什么数据等级？
- `sec-policy-classification`：数据标签命名格式是什么？

### 4.3 CATEGORY：合规与审计（`sec-compliance`）

| intent_code | name | 覆盖文档 | top_k |
|-------------|------|----------|-------|
| `sec-compliance-djcp` | 等保合规自查 | `等保合规自查报告.pdf` | 8 |

**典型问法示例**

- `sec-compliance-djcp`：2025 年等保自查整体符合率是多少？
- `sec-compliance-djcp`：OA 系统的等保备案号是什么？
- `sec-compliance-djcp`：等保自查发现的主要风险项有哪些？
- `sec-compliance-djcp`：CRM API 限流整改什么时候完成？

### 4.4 CATEGORY：安全运营（`sec-ops`）

| intent_code | name | 覆盖文档 | top_k |
|-------------|------|----------|-------|
| `sec-ops-incident` | 安全事件应急 | `安全事件通报模板.txt`、`应急响应流程图.png` | 8 |
| `sec-ops-assets` | 系统资产登记 | `系统资产清单.csv` | 6 |

**典型问法示例**

- `sec-ops-incident`：发现安全事件后多长时间内要初报？
- `sec-ops-incident`：安全事件通报模板包含哪些章节？
- `sec-ops-incident`：P0 事件应急处理的六个步骤是什么？
- `sec-ops-incident`：个人信息泄露向监管报告的时限是多少？
- `sec-ops-assets`：SYS-ERP-002 是什么系统？负责人是谁？
- `sec-ops-assets`：哪些系统是等保三级？
- `sec-ops-assets`：数据中台的数据最高密级是多少？

### 4.5 CATEGORY：访问控制（`sec-access`）

| intent_code | name | 覆盖文档 | top_k |
|-------------|------|----------|-------|
| `sec-access-rbac` | 权限申请与审批 | `权限申请对照表.xlsx` | 8 |

**典型问法示例**

- `sec-access-rbac`：申请 ERP 财务总监角色需要谁审批？
- `sec-access-rbac`：CRM 销售代表能访问什么级别的数据？
- `sec-access-rbac`：IAM 管理员是不是特权角色？
- `sec-access-rbac`：L4 数据导出需要哪一级审批？

---

## 5. 文档映射表

| 文档路径 | 格式 | 叶子 intent_code |
|----------|------|------------------|
| `信息安全管理制度.md` | md | `sec-policy-mgmt` |
| `数据分类分级规范.docx` | docx | `sec-policy-classification` |
| `等保合规自查报告.pdf` | pdf | `sec-compliance-djcp` |
| `安全事件通报模板.txt` | txt | `sec-ops-incident` |
| `应急响应流程图.png` | png | `sec-ops-incident` |
| `系统资产清单.csv` | csv | `sec-ops-assets` |
| `权限申请对照表.xlsx` | xlsx | `sec-access-rbac` |

---

## 6. 易混淆意图消歧

| 用户表述 | 应命中 | 不应命中 | 判断依据 |
|----------|--------|----------|----------|
| 数据分级 / L3 / L4 | `sec-policy-classification` | `sec-policy-mgmt` | 是否讨论标签、定级标准 vs 制度总则 |
| 等保 / 合规 / 测评 | `sec-compliance-djcp` | `sec-policy-mgmt` | 是否提到自查报告、符合率、备案号 |
| 安全事件 / 应急 / 通报 | `sec-ops-incident` | `sec-policy-mgmt` | 是否讨论通报模板、流程图、响应时限 |
| 系统编码 / 资产 / 负责人 | `sec-ops-assets` | `sec-access-rbac` | 是否查系统清单 vs 角色权限 |
| 权限 / 角色 / 审批 | `sec-access-rbac` | `sec-policy-mgmt` | 是否查角色对照表 vs 制度原则 |
| 备份 / RPO / RTO | `sec-policy-mgmt` | `sec-ops-incident` | 制度第 10 章业务连续性 |
| 个人信息 / 72小时报告 | `sec-ops-incident` 或 `sec-policy-mgmt` | — | 通报模板语境 vs 制度条款 |
| 加密 / 脱敏 | `sec-policy-classification` | `sec-compliance-djcp` | 分级控制策略 vs 等保测评 |

**歧义引导场景**（分类器可返回最多 3 个候选）：

- 仅问「数据安全」→ `sec-policy-mgmt` + `sec-policy-classification`
- 仅问「三级系统」→ `sec-compliance-djcp` + `sec-ops-assets`
- 仅问「审批」→ `sec-access-rbac` + `sec-policy-mgmt`

---

## 7. 导入步骤

1. 在管理后台创建 `biz-security` 知识库（建议名称：`biz-security` 或 `企业信息安全与合规`）。
2. 上传 `resources/docs/knowledge/biz/biz-security/` 目录下全部 8 个文件，完成分块入库。
   - `.md` / `.txt`：默认 Markdown / Tika 解析
   - `.csv`：Csv 解析器
   - `.xlsx`：ExcelPoi 解析器
   - `.docx` / `.pdf`：MinerU 解析器（需配置 MinerU 服务）
   - `.png`：Image 解析器（需配置 VLM）
3. 查询知识库 ID 与 collection 名称：

   ```sql
   SELECT id, name, collection_name FROM t_knowledge_base WHERE name LIKE '%biz-security%';
   ```

4. 编辑 `docs/examples/biz-security-intent-nodes-import.sql`，全文替换：
   - `__KB_ID_BIZ_SECURITY__`
   - `__COLLECTION_BIZ_SECURITY__`
5. 在 PostgreSQL 中执行 SQL 脚本。
6. 清理意图树缓存：`redis-cli DEL ragent:intent:tree`（或重启服务）。
7. 通过管理后台「意图树」页面确认树结构，抽样测试分类效果。

---

## 8. 评测用例建议

| intent_code | 建议测试 query |
|-------------|----------------|
| `sec-policy-mgmt` | 集团信息安全管理的基本原则是什么？ |
| `sec-policy-mgmt` | 违规泄露 L4 数据会怎么处理？ |
| `sec-policy-classification` | 销售合同数据应该定几级？ |
| `sec-compliance-djcp` | 2025 年等保自查发现了哪些中危风险？ |
| `sec-ops-incident` | P1 事件多长时间内必须响应？ |
| `sec-ops-incident` | 安全事件初报需要填写哪些字段？ |
| `sec-ops-assets` | SYS-HR-004 的技术负责人是谁？ |
| `sec-access-rbac` | eHR 系统管理员需要什么审批？ |

---

## 9. 调优建议

1. **examples 优先**：LLM 分类器主要依据 `id + path + description + examples`，建议根据真实用户问法持续补充。
2. **top_k**：资产清单类节点用 6（表格精确匹配），制度/规范类用 8。
3. **多模态节点**：`sec-ops-incident` 同时覆盖 txt 与 png，检索时注意图片分块是否含流程步骤文字。
4. **跨库消歧**：若与 `biz-oa` 知识库并存，在 `description` 中强调「集团级安全制度」以区别于 OA 系统数据安全规范。

---

## 10. 字段约定

| 字段 | 本知识库取值 |
|------|-------------|
| `level` | 0=DOMAIN, 1=CATEGORY, 2=TOPIC |
| `kind` | 0=KB（全部节点均为知识库检索类） |
| `kb_id` | biz-security 知识库 ID（DOMAIN/CATEGORY/TOPIC 均填写） |
| `collection_name` | 仅 TOPIC 叶子填写 |
| `examples` | JSON 数组字符串 |
| `parent_code` | 父节点的 `intent_code`，根节点为 NULL |
