# onboarding 知识库意图树设计

> 配套导入脚本：[docs/examples/intent-node-import/onboarding-intent-nodes-import.sql](../../../../docs/examples/intent-node-import/onboarding-intent-nodes-import.sql)

本文档描述 `resources/docs/knowledge/group/onboarding/` 目录下 **8 篇多格式文档** 的意图树划分方案，用于 Ragent 意图分类与定向检索。

---

## 1. 设计目标

| 目标 | 说明 |
|------|------|
| 对齐项目机制 | 遵循 `DOMAIN → CATEGORY → TOPIC` 三层结构，仅 **TOPIC（叶子）** 参与 LLM 分类 |
| 系列边界清晰 | 入职流程、制度手册、培训体系、办公环境四大 CATEGORY 独立 |
| 消歧同名主题 | 「培训」「考勤」「入职」等词汇通过 `description` 区分语境 |
| 单库聚合 | 8 篇文档同属一个知识库，叶子节点共享 `kbId` 与 `collectionName` |
| HR 联动 | 与 `group/hr` 人事制度、薪资福利等文档互补，本库聚焦 **入职与培训** 场景 |
| 格式覆盖 | 覆盖 Markdown、Tika(txt)、CSV、Excel、MinerU(docx/pdf)、Image(png) 全解析链路 |

---

## 2. 知识库文档清单

| 文档 | 格式 | 解析器 | 说明 |
|------|------|--------|------|
| `新员工入职指南.md` | Markdown | Markdown | 入职前准备、当天流程、首周安排、FAQ |
| `入职第一天待办.txt` | Plain Text | Tika | 首日 checklist，按时段逐项勾选 |
| `员工手册.pdf` | PDF | MinerU | 聘用、考勤、假期、薪酬、纪律、离职 |
| `岗位职责说明书.docx` | Word | MinerU | 研发/产品/HRBP/销售/运维等典型 JD |
| `培训课程表.xlsx` | Excel | ExcelPoi | 入职必修 + 通用/专业/管理类课程 |
| `办公地点与门禁卡.csv` | CSV | Csv | 楼层、工位、门禁、停车、前台电话 |
| `组织架构图.png` | PNG | Image | 集团组织架构与事业部划分 |

---

## 3. 意图树总览

```
onboarding（DOMAIN）
├── onb-process（入职流程）
│   ├── onb-process-guide           # 新员工入职指南.md
│   └── onb-process-firstday        # 入职第一天待办.txt
├── onb-handbook（制度与手册）
│   ├── onb-handbook-employee       # 员工手册.pdf
│   └── onb-handbook-jd             # 岗位职责说明书.docx
├── onb-training（培训体系）
│   └── onb-training-courses        # 培训课程表.xlsx
└── onb-facility（办公与环境）
    ├── onb-facility-location       # 办公地点与门禁卡.csv
    └── onb-facility-orgchart       # 组织架构图.png
```

**节点统计**：1 DOMAIN + 4 CATEGORY + 6 TOPIC = **11 个节点**

系统交互节点（`sys` / `sys-welcome` / `sys-about-bot`）为全局共用，见 `docs/examples/intent-node-import/mcp-intent-nodes-import.sql`，不在本知识库脚本中重复导入。

---

## 4. 节点明细

### 4.1 DOMAIN

| intent_code | name | description |
|-------------|------|-------------|
| `onboarding` | 新员工入职与培训 | 星云科技集团新员工入职流程、首日待办、员工手册、岗位职责、培训课程、办公地点与组织架构 |

### 4.2 CATEGORY：入职流程（`onb-process`）

| intent_code | name | 覆盖文档 | top_k |
|-------------|------|----------|-------|
| `onb-process-guide` | 入职指南 | `新员工入职指南.md` | 8 |
| `onb-process-firstday` | 首日待办 | `入职第一天待办.txt` | 6 |

**典型问法示例**

- `onb-process-guide`：入职当天需要带什么材料？
- `onb-process-guide`：入职首周有哪些必修培训？
- `onb-process-guide`：Buddy 制度是什么？
- `onb-process-firstday`：入职第一天上午要做哪些事？
- `onb-process-firstday`：ONB-002 课程什么时候截止？

### 4.3 CATEGORY：制度与手册（`onb-handbook`）

| intent_code | name | 覆盖文档 | top_k |
|-------------|------|----------|-------|
| `onb-handbook-employee` | 员工手册 | `员工手册.pdf` | 8 |
| `onb-handbook-jd` | 岗位职责 | `岗位职责说明书.docx` | 8 |

**典型问法示例**

- `onb-handbook-employee`：年假有多少天？
- `onb-handbook-employee`：试用期一般多长？
- `onb-handbook-employee`：辞职需要提前多久通知？
- `onb-handbook-jd`：后端研发工程师的核心职责是什么？
- `onb-handbook-jd`：产品经理需要哪些任职资格？

### 4.4 CATEGORY：培训体系（`onb-training`）

| intent_code | name | 覆盖文档 | top_k |
|-------------|------|----------|-------|
| `onb-training-courses` | 培训课程 | `培训课程表.xlsx` | 8 |

**典型问法示例**

- `onb-training-courses`：新员工必修课程有哪些？
- `onb-training-courses`：ONB-001 是什么课？谁讲？
- `onb-training-courses`：有没有 Java 进阶培训？
- `onb-training-courses`：新经理领导力工作坊什么时候开课？

### 4.5 CATEGORY：办公与环境（`onb-facility`）

| intent_code | name | 覆盖文档 | top_k |
|-------------|------|----------|-------|
| `onb-facility-location` | 办公地点与门禁 | `办公地点与门禁卡.csv` | 6 |
| `onb-facility-orgchart` | 组织架构 | `组织架构图.png` | 6 |

**典型问法示例**

- `onb-facility-location`：入职手续在哪个楼层办理？
- `onb-facility-location`：研发工位在几楼？
- `onb-facility-location`：LOC-HQ-A3F-001 是什么区域？
- `onb-facility-orgchart`：公司有哪些事业部？
- `onb-facility-orgchart`：AI平台部在哪个楼层？

---

## 5. 文档映射表

| 文档路径 | 格式 | 叶子 intent_code |
|----------|------|------------------|
| `新员工入职指南.md` | md | `onb-process-guide` |
| `入职第一天待办.txt` | txt | `onb-process-firstday` |
| `员工手册.pdf` | pdf | `onb-handbook-employee` |
| `岗位职责说明书.docx` | docx | `onb-handbook-jd` |
| `培训课程表.xlsx` | xlsx | `onb-training-courses` |
| `办公地点与门禁卡.csv` | csv | `onb-facility-location` |
| `组织架构图.png` | png | `onb-facility-orgchart` |

---

## 6. 易混淆意图消歧

| 用户表述 | 应命中 | 不应命中 | 判断依据 |
|----------|--------|----------|----------|
| 入职流程 / 入职材料 | `onb-process-guide` | `onb-process-firstday` | 全流程指南 vs 首日 checklist |
| 第一天 / 待办 / checklist | `onb-process-firstday` | `onb-process-guide` | 是否按时段列待办项 |
| 年假 / 考勤 / 离职 | `onb-handbook-employee` | `onb-process-guide` | 员工手册制度 vs 入职指南概览 |
| 岗位职责 / JD / 任职资格 | `onb-handbook-jd` | `onb-handbook-employee` | 岗位说明书 vs 通用员工手册 |
| 培训课程 / ONB- | `onb-training-courses` | `onb-process-guide` | 课程表编码 vs 指南中提及培训 |
| 楼层 / 工位 / 门禁 | `onb-facility-location` | `onb-facility-orgchart` | CSV 精确查询 vs 架构图 |
| 组织架构 / 事业部 | `onb-facility-orgchart` | `onb-facility-location` | 部门划分 vs 物理位置 |
| 薪资福利 | `group/hr` 知识库 | `onboarding` | 跨库：详询薪资与福利政策 |

**歧义引导场景**：

- 仅问「入职」→ `onb-process-guide` + `onb-process-firstday`
- 仅问「培训」→ `onb-training-courses` + `onb-process-guide`
- 仅问「几楼」→ `onb-facility-location` + `onb-facility-orgchart`

---

## 7. 导入步骤

1. 创建 `onboarding` 知识库（建议名称：`新员工入职与培训`），上传 8 个文件并完成分块。
2. 查询知识库 ID：

   ```sql
   SELECT id, name, collection_name FROM t_knowledge_base WHERE name LIKE '%onboarding%' OR name LIKE '%入职%';
   ```

3. 编辑 `docs/examples/intent-node-import/onboarding-intent-nodes-import.sql`，替换：
   - `__KB_ID_ONBOARDING__`
   - `__COLLECTION_ONBOARDING__`
4. 执行 SQL，清理缓存：`redis-cli DEL ragent:intent:tree`
5. 可选：执行 `sample-questions-import.sql` 中 onboarding 段示例问题。

---

## 8. 评测用例建议

| intent_code | 建议测试 query |
|-------------|----------------|
| `onb-process-guide` | 入职当天需要带什么材料？ |
| `onb-process-firstday` | 入职第一天下午 4 点要做什么？ |
| `onb-handbook-employee` | 工作 5 年年假有几天？ |
| `onb-handbook-jd` | 销售经理的 KPI 有哪些？ |
| `onb-training-courses` | 信息安全与保密课什么时候截止？ |
| `onb-facility-location` | HR 服务中心在哪一层？ |
| `onb-facility-orgchart` | 研发效能部负责什么？ |

---

## 9. 字段约定

| 字段 | 本知识库取值 |
|------|-------------|
| `level` | 0=DOMAIN, 1=CATEGORY, 2=TOPIC |
| `kind` | 0=KB |
| `kb_id` | onboarding 知识库 ID |
| `collection_name` | 仅 TOPIC 叶子填写 |
| `examples` | JSON 数组字符串 |
| `parent_code` | 父节点 intent_code，根为 NULL |
