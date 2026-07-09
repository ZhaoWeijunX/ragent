# ragent-test 知识库意图树设计

> 配套导入脚本：[docs/examples/ragent-test-intent-nodes-import.sql](../../../docs/examples/intent-node-import/ragent-test-intent-nodes-import.sql)

本文档描述 `resources/docs/ragent-test/` 目录下 **52 篇技术文档** 的意图树划分方案，用于 Ragent 意图分类与定向检索。

---

## 1. 设计目标

| 目标 | 说明 |
|------|------|
| 对齐项目机制 | 遵循 `DOMAIN → CATEGORY → TOPIC` 三层结构，仅 **TOPIC（叶子）** 参与 LLM 分类 |
| 系列边界清晰 | 六大文档系列各自独立 CATEGORY，避免跨系列误路由 |
| 消歧同名主题 | 「调度」「限流」「线程池」「意图」「Rerank」「流式」「MCP」等跨系列词汇通过 `description` 区分 |
| 单库聚合 | 52 篇文档同属一个知识库，叶子节点共享 `kbId` 与 `collectionName` |

---

## 2. 意图树总览

```
ragent-docs（DOMAIN）
├── kb-build（AI知识库建设）
│   ├── kb-build-arch
│   ├── kb-build-upload-size
│   ├── kb-build-upload-ratelimit
│   ├── kb-build-upload-api
│   ├── kb-build-chunk
│   ├── kb-build-doc-api
│   └── kb-build-sync
├── infra-ai（大模型调度引擎实战）
│   ├── infra-ai-arch
│   ├── infra-ai-route-circuit
│   ├── infra-ai-chat-stream
│   ├── infra-ai-embedding
│   └── infra-ai-rerank
├── rag-qa（AI知识问答）
│   ├── rag-qa-pipeline
│   ├── rag-qa-memory
│   ├── rag-qa-rewrite
│   ├── rag-qa-intent
│   ├── rag-qa-retrieval
│   ├── rag-qa-mcp
│   ├── rag-qa-prompt
│   ├── rag-qa-stream
│   └── rag-qa-ratelimit
├── rag-eval（RAG 评测）
│   ├── rag-eval-setup
│   ├── rag-eval-runner
│   ├── rag-eval-metrics-intent-retrieval
│   ├── rag-eval-metrics-performance
│   └── rag-eval-ragas
├── local-llm（Ollama与本地部署）
│   ├── local-llm-why
│   └── local-llm-ollama
└── tech-docs（技术文档）
    └── tech-docs-threadpool
```

**节点统计**：1 DOMAIN + 6 CATEGORY + 26 TOPIC = **33 个节点**

系统交互节点（`sys` / `sys-welcome` / `sys-about-bot`）为全局共用，见 `docs/examples/mcp-intent-nodes-import.sql`，不在本知识库脚本中重复导入。

---

## 3. 节点明细

### 3.1 DOMAIN

| intent_code | name | description |
|-------------|------|-------------|
| `ragent-docs` | Ragent 技术专栏 | Ragent 项目配套技术文档，涵盖知识库工程化、AI 知识问答全链路、AI 基础设施层、RAG 评测、本地模型部署与横切技术专题 |

### 3.2 CATEGORY：AI知识库建设（`kb-build`）

| intent_code | name | 覆盖文档 | top_k |
|-------------|------|----------|-------|
| `kb-build-arch` | 知识库宏观设计 | `01.RAG 知识库管理宏观设计.md` | 6 |
| `kb-build-upload-size` | 文件上传与内存优化 | `02`、`03` | 8 |
| `kb-build-upload-ratelimit` | 上传分布式限流 | `04`、`05` | 8 |
| `kb-build-upload-api` | 文档上传接口 | `06` | 8 |
| `kb-build-chunk` | 分块处理与管理 | `07`、`11` | 8 |
| `kb-build-doc-api` | 文档管理接口 | `10` | 8 |
| `kb-build-sync` | URL定时同步 | `08`、`09` | 8 |

**典型问法示例**

- `kb-build-arch`：Ragent 知识库系统整体架构是怎样的？
- `kb-build-upload-size`：为什么上传 30MB 文件会占 100MB 内存？
- `kb-build-upload-ratelimit`：文件上传分布式限流应该放在哪一层？
- `kb-build-sync`：URL 文档定时同步是怎么实现的？

### 3.3 CATEGORY：大模型调度引擎实战（`infra-ai`）

| intent_code | name | 覆盖文档 | top_k |
|-------------|------|----------|-------|
| `infra-ai-arch` | AI基础设施层宏观设计 | `01` | 6 |
| `infra-ai-route-circuit` | 多模型路由与熔断 | `02`、`03` | 8 |
| `infra-ai-chat-stream` | Chat调用与流式路由 | `04`、`05`、`06` | 8 |
| `infra-ai-embedding` | Embedding向量化客户端 | `07` | 8 |
| `infra-ai-rerank` | Rerank重排序 | `08` | 8 |

**典型问法示例**

- `infra-ai-route-circuit`：三态熔断器的工作原理是什么？
- `infra-ai-chat-stream`：流式路由的首包探测机制是什么？
- `infra-ai-rerank`：Rerank 重排序在 Ragent 里怎么实现？

### 3.4 CATEGORY：AI知识问答（`rag-qa`）

| intent_code | name | 覆盖文档 | top_k |
|-------------|------|----------|-------|
| `rag-qa-pipeline` | 问答全链路全景 | `01` | 6 |
| `rag-qa-memory` | 会话记忆与摘要 | `02`、`03` | 8 |
| `rag-qa-rewrite` | 查询改写与拆分 | `04` | 8 |
| `rag-qa-intent` | 意图识别与引导 | `05`～`09` | 8 |
| `rag-qa-retrieval` | 多通道检索与后处理 | `10`、`11` | 8 |
| `rag-qa-mcp` | MCP 工具调用 | `12`、`13` | 8 |
| `rag-qa-prompt` | Prompt 组装 | `14` | 8 |
| `rag-qa-stream` | 流式生成链路 | `15`、`16` | 8 |
| `rag-qa-ratelimit` | 排队限流 | `17`、`18` | 8 |

**典型问法示例**

- `rag-qa-pipeline`：StreamChatPipeline 的八个阶段分别是什么？
- `rag-qa-memory`：会话记忆是怎么加载和存储的？
- `rag-qa-rewrite`：查询改写和子问题拆分是怎么做的？
- `rag-qa-intent`：意图树为什么要设计成三级结构？
- `rag-qa-intent`：意图分数出来后怎么决定查哪个库、查多少条？
- `rag-qa-retrieval`：多通道并行检索有哪些通道？
- `rag-qa-mcp`：MCP 工具在问答流水线里什么时候被调用？
- `rag-qa-stream`：流式生成异常时怎么处理？
- `rag-qa-ratelimit`：对话入口的分布式排队限流是怎么工作的？

### 3.5 CATEGORY：RAG 评测（`rag-eval`）

| intent_code | name | 覆盖文档 | top_k |
|-------------|------|----------|-------|
| `rag-eval-setup` | 评测基建与数据初始化 | `01`、`02`、`03` | 6 |
| `rag-eval-runner` | 评测录制与全链路 | `04` | 8 |
| `rag-eval-metrics-intent-retrieval` | 意图与检索指标 | `05` | 6 |
| `rag-eval-metrics-performance` | 性能指标 | `06` | 6 |
| `rag-eval-ragas` | RAGAS评测实践 | `07`～`11` | 8 |

**典型问法示例**

- `rag-eval-setup`：评估集应该怎么设计？
- `rag-eval-runner`：为什么评测需要跑两个接口？
- `rag-eval-metrics-intent-retrieval`：intent_top1 怎么算？
- `rag-eval-ragas`：RAGAS 五个指标分别衡量什么？

### 3.6 CATEGORY：Ollama与本地部署（`local-llm`）

| intent_code | name | 覆盖文档 | top_k |
|-------------|------|----------|-------|
| `local-llm-why` | 为什么本地部署 | `01` | 6 |
| `local-llm-ollama` | Ollama概念与实战 | `02`、`03` | 8 |

**典型问法示例**

- `local-llm-why`：什么场景必须本地部署大模型？
- `local-llm-ollama`：ollama serve 和 ollama run 有什么区别？

### 3.7 CATEGORY：技术文档（`tech-docs`）

| intent_code | name | 覆盖文档 | top_k |
|-------------|------|----------|-------|
| `tech-docs-threadpool` | 线程池设计与实现 | `01` | 8 |

**典型问法示例**

- `tech-docs-threadpool`：Ragent 里有多少个线程池，分别干什么用？
- `tech-docs-threadpool`：`chatEntryExecutor` 和全局限流是怎么配合的？
- `tech-docs-threadpool`：为什么检索链路用 `CallerRunsPolicy` 而入口用 `AbortPolicy`？

---

## 4. 文档映射表

| 文档路径 | 叶子 intent_code |
|----------|------------------|
| AI知识库建设/01 | `kb-build-arch` |
| AI知识库建设/02, 03 | `kb-build-upload-size` |
| AI知识库建设/04, 05 | `kb-build-upload-ratelimit` |
| AI知识库建设/06 | `kb-build-upload-api` |
| AI知识库建设/07, 11 | `kb-build-chunk` |
| AI知识库建设/10 | `kb-build-doc-api` |
| AI知识库建设/08, 09 | `kb-build-sync` |
| 大模型调度引擎实战/01 | `infra-ai-arch` |
| 大模型调度引擎实战/02, 03 | `infra-ai-route-circuit` |
| 大模型调度引擎实战/04, 05, 06 | `infra-ai-chat-stream` |
| 大模型调度引擎实战/07 | `infra-ai-embedding` |
| 大模型调度引擎实战/08 | `infra-ai-rerank` |
| AI知识问答篇/01 | `rag-qa-pipeline` |
| AI知识问答篇/02, 03 | `rag-qa-memory` |
| AI知识问答篇/04 | `rag-qa-rewrite` |
| AI知识问答篇/05～09 | `rag-qa-intent` |
| AI知识问答篇/10, 11 | `rag-qa-retrieval` |
| AI知识问答篇/12, 13 | `rag-qa-mcp` |
| AI知识问答篇/14 | `rag-qa-prompt` |
| AI知识问答篇/15, 16 | `rag-qa-stream` |
| AI知识问答篇/17, 18 | `rag-qa-ratelimit` |
| RAG 评测/01, 02, 03 | `rag-eval-setup` |
| RAG 评测/04 | `rag-eval-runner` |
| RAG 评测/05 | `rag-eval-metrics-intent-retrieval` |
| RAG 评测/06 | `rag-eval-metrics-performance` |
| RAG 评测/07～11 | `rag-eval-ragas` |
| Ollama、vLLM扫盲/01 | `local-llm-why` |
| Ollama、vLLM扫盲/02, 03 | `local-llm-ollama` |
| 技术文档/01 | `tech-docs-threadpool` |

---

## 5. 易混淆意图消歧

| 用户表述 | 应命中 | 不应命中 | 判断依据 |
|----------|--------|----------|----------|
| 调度引擎 | `kb-build-sync`（文档 cron 同步）或 `infra-ai-route-circuit`（模型路由） | 跨系列盲选 | 是否提到 URL/文档同步 vs 模型/熔断 |
| 分布式限流 | `kb-build-upload-ratelimit`（上传）或 `tech-docs-threadpool`（对话 `chatEntryExecutor` + Redis 信号量） | 跨系列盲选 | 是否提到文件上传 vs 对话并发 / SSE 入口 |
| 线程池 | `tech-docs-threadpool` | `kb-build-chunk`（仅提及分块池） | 是否讨论 ThreadPoolExecutorConfig 或多池分层 |
| 意图准确率 / intent_top1 | `rag-eval-metrics-intent-retrieval` | `kb-build-*` | 评测指标语境 |
| 意图树怎么建 | `rag-qa-intent`（设计）或 `rag-eval-setup`（评测脚本） | `kb-build-*` | 讨论树结构/Prompt vs 初始化 SQL |
| 意图 / 歧义引导 | `rag-qa-intent` | `rag-eval-metrics-intent-retrieval` | 运行时分类 vs 评测指标 |
| 多通道检索 | `rag-qa-retrieval`（问答链路） | `rag-eval-ragas` | 实现细节 vs 效果评测 |
| MCP 工具 | `rag-qa-mcp`（流水线调用） | MCP 意图节点 `kind=2` | 技术文档 vs 业务工具配置 |
| 流式 / SSE | `rag-qa-stream`（问答推送）或 `infra-ai-chat-stream`（模型客户端） | `rag-eval-metrics-performance` | 问答 Pipeline vs infra 客户端 vs TTFT 评测 |
| 排队限流 | `rag-qa-ratelimit`（对话入口） | `kb-build-upload-ratelimit`（上传）或 `tech-docs-threadpool` | 对话 SSE 入口 vs 文件上传 vs 线程池 |
| Embedding | `infra-ai-embedding`（客户端）或 `kb-build-arch`（入库流程） | — | 是否提到 infra-ai / 供应商 |
| Rerank | `infra-ai-rerank`（实现）或 `rag-eval-ragas`（效果评测） | — | 是否提到 RAGAS / 指标 |
| SSE / 流式 | `rag-qa-stream`（问答 Pipeline）或 `infra-ai-chat-stream`（调用）或 `rag-eval-metrics-performance`（TTFT 评测） | — | 问答推送 vs 模型客户端 vs 评测 |
| Ollama / vLLM | `local-llm-*` | `infra-ai-*` | 实体强匹配 |

**歧义引导场景**（分类器可返回最多 3 个候选）：

- 仅问「调度引擎」→ `kb-build-sync` + `infra-ai-route-circuit`
- 仅问「Rerank」→ `infra-ai-rerank` + `rag-eval-ragas`
- 仅问「意图」→ `rag-qa-intent` + `rag-eval-metrics-intent-retrieval`
- 仅问「MCP」→ `rag-qa-mcp` + MCP 工具意图
- 仅问「流式」→ `rag-qa-stream` + `infra-ai-chat-stream`
- 仅问「限流」→ `rag-qa-ratelimit` + `kb-build-upload-ratelimit` + `tech-docs-threadpool`
- 仅问「并发」→ `tech-docs-threadpool` + `rag-eval-metrics-performance`

---

## 6. 导入步骤

1. 创建 `ragent-test` 知识库并完成 **52 篇**文档灌入与分块。
2. 查询知识库 ID 与 collection 名称：

   ```sql
   SELECT id, name, collection_name FROM t_knowledge_base WHERE name LIKE '%ragent-test%';
   ```

3. 编辑 `docs/examples/intent-node-import/ragent-test-intent-nodes-import.sql`，全文替换：
   - `__KB_ID_RAGENT_TEST__`
   - `__COLLECTION_RAGENT_TEST__`
4. 在 PostgreSQL 中执行 SQL 脚本。
5. 清理意图树缓存：`redis-cli DEL ragent:intent:tree`（或重启服务）。
6. 通过管理后台「意图树」页面确认树结构，抽样测试分类效果。

---

## 7. 调优建议

1. **examples 优先**：LLM 分类器主要依据 `id + path + description + examples`，建议根据真实用户问法持续补充。
2. **top_k**：宏观设计类节点用 6，API/实现细节类用 8，可按检索效果微调。
3. **promptSnippet**：若某叶子需要特殊回答格式，可在管理后台为节点配置 `prompt_snippet`。
4. **评测对齐**：若对接 ragenteval，可将叶子 `intent_code` 作为评估集 `intent_l2` 标注值，并为易混意图各补充 3～5 条测试 query。

---

## 8. 字段约定

| 字段 | 本知识库取值 |
|------|-------------|
| `level` | 0=DOMAIN, 1=CATEGORY, 2=TOPIC |
| `kind` | 0=KB（全部节点均为知识库检索类） |
| `kb_id` | ragent-test 知识库 ID（DOMAIN/CATEGORY/TOPIC 均填写） |
| `collection_name` | 仅 TOPIC 叶子填写 |
| `examples` | JSON 数组字符串 |
| `parent_code` | 父节点的 `intent_code`，根节点为 NULL |
