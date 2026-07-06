-- ragent-test 知识库意图树导入脚本
--
-- 适用文档目录：resources/docs/ragent-test/（33 篇 Markdown）
-- 设计说明：resources/docs/ragent-test/intent-tree-design.md
--

-- 使用前请替换占位符（全文搜索替换即可）：
-- SELECT id, name, collection_name FROM t_knowledge_base WHERE name LIKE '%ragent-test%';
--   __KB_ID_RAGENT_TEST__       -> t_knowledge_base 中 ragent-test 知识库的 id
--   __COLLECTION_RAGENT_TEST__  -> 该知识库对应的 Milvus collection_name
--
-- 导入后请清理意图树 Redis 缓存（任选其一）：
--   redis-cli DEL ragent:intent:tree
--   或重启 bootstrap 服务
--
-- 系统交互节点（sys / sys-welcome / sys-about-bot）如已通过
-- docs/examples/mcp-intent-nodes-import.sql 导入，请勿重复插入。

-- ---------------------------------------------------------------------------
-- 可选：清理本脚本涉及的意图节点后重新导入（慎用，会物理删除记录）
-- ---------------------------------------------------------------------------
-- DELETE FROM t_intent_node
-- WHERE intent_code IN (
--     'ragent-docs', 'kb-build', 'kb-build-arch', 'kb-build-upload-size',
--     'kb-build-upload-ratelimit', 'kb-build-upload-api', 'kb-build-chunk',
--     'kb-build-doc-api', 'kb-build-sync',
--     'infra-ai', 'infra-ai-arch', 'infra-ai-route-circuit', 'infra-ai-chat-stream',
--     'infra-ai-embedding', 'infra-ai-rerank',
--     'rag-eval', 'rag-eval-setup', 'rag-eval-runner',
--     'rag-eval-metrics-intent-retrieval', 'rag-eval-metrics-performance', 'rag-eval-ragas',
--     'local-llm', 'local-llm-why', 'local-llm-ollama'
-- );

INSERT INTO t_intent_node (
    id, kb_id, intent_code, name, level, parent_code, description, examples,
    collection_name, top_k, mcp_tool_id, kind, prompt_snippet, prompt_template,
    param_prompt_template, sort_order, enabled, create_by, update_by,
    create_time, update_time, deleted
) VALUES
-- ========== DOMAIN ==========
(
    2059100000000000001, '2072555556962385920', 'ragent-docs', 'Ragent 技术专栏', 0, NULL,
    'Ragent 项目配套技术文档，涵盖知识库工程化、AI 基础设施层、RAG 评测与本地模型部署',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    1, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: AI知识库建设 ==========
(
    2059100000000000002, '2072555556962385920', 'kb-build', 'AI知识库建设', 1, 'ragent-docs',
    '知识库系统的宏观架构、文档上传、分块入库、文档管理与 URL 定时同步',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    10, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000003, '2072555556962385920', 'kb-build-arch', '知识库宏观设计', 2, 'kb-build',
    '知识库三层架构、核心概念（知识库/文档/分块）、数据库表设计、异步处理流程、多知识库隔离',
    '["Ragent 知识库系统整体架构是怎样的？","知识库、文档、分块三者是什么关系？","文档从上传到入库的完整流程是什么？","企业级知识库系统分哪几层？"]',
    'test-1', 6, NULL, 0, NULL, NULL, NULL,
    11, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000004, '2072555556962385920', 'kb-build-upload-size', '文件上传与内存优化', 2, 'kb-build',
    'Spring Boot max-file-size、multipart 解析、预签名 URL、流式上传、S3 分块签名，解决大文件上传内存膨胀问题',
    '["为什么上传 30MB 文件会占 100MB 内存？","知识库单文件上传大小限制怎么配置？","预签名 URL 流式上传是怎么做的？","max-request-size 和 max-file-size 有什么区别？"]',
    'test-1', 8, NULL, 0, NULL, NULL, NULL,
    12, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000005, '2072555556962385920', 'kb-build-upload-ratelimit', '上传分布式限流', 2, 'kb-build',
    '文件上传并发控制、RPermitExpirableSemaphore、Filter/Gateway/Service 各层限流时机、Tomcat multipart 解析时机；非模型 API 限流',
    '["文件上传分布式限流应该放在哪一层？","为什么在 Service 层做限流太晚了？","Gateway 层限流和 Filter 层限流有什么区别？","上传接口怎么做并发控制？"]',
    'test-1', 8, NULL, 0, NULL, NULL, NULL,
    13, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000006, '2072555556962385920', 'kb-build-upload-api', '文档上传接口', 2, 'kb-build',
    'POST /knowledge-base/{kb-id}/docs/upload 接口设计、本地上传与 URL 来源、参数校验与入库流程',
    '["知识库文档上传接口怎么设计的？","上传接口支持哪些来源类型？","文档上传接口需要传哪些参数？"]',
    'test-1', 8, NULL, 0, NULL, NULL, NULL,
    14, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000007, '2072555556962385920', 'kb-build-chunk', '分块处理与管理', 2, 'kb-build',
    'startChunk 异步分块、RocketMQ 事务消息、分块策略配置、分块 CRUD 与管理接口',
    '["点击开始分块后系统做了什么？","分块接口如何触发异步处理？","怎么管理知识库里的 chunk？","RocketMQ 事务消息在分块流程里起什么作用？"]',
    'test-1', 8, NULL, 0, NULL, NULL, NULL,
    15, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000008, '2072555556962385920', 'kb-build-doc-api', '文档管理接口', 2, 'kb-build',
    '文档列表、启用/禁用、删除、状态查询等文档生命周期管理 API',
    '["怎么删除知识库里的某篇文档？","文档管理接口有哪些能力？","如何查询文档处理状态？"]',
    'test-1', 8, NULL, 0, NULL, NULL, NULL,
    16, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000009, '2072555556962385920', 'kb-build-sync', 'URL定时同步', 2, 'kb-build',
    '飞书/Notion 等远程文档的 scheduleCron 定时同步、分布式锁、变更检测、KnowledgeDocumentScheduleJob 扫描调度引擎与故障恢复；非模型路由调度',
    '["URL 文档定时同步是怎么实现的？","定时同步的调度引擎如何扫描到期任务？","多实例部署时怎么防止同步任务重复执行？","scheduleCron 怎么配置？"]',
    'test-1', 8, NULL, 0, NULL, NULL, NULL,
    17, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: 大模型调度引擎实战 ==========
(
    2059100000000000010, '2072555556962385920', 'infra-ai', '大模型调度引擎实战', 1, 'ragent-docs',
    'Ragent 的 infra-ai 模块：多供应商 Chat/Embedding/Rerank 统一调用、模型路由、熔断与流式执行',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    20, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000011, '2072555556962385920', 'infra-ai-arch', 'AI基础设施层宏观设计', 2, 'infra-ai',
    '为什么需要 infra 层、供应商差异屏蔽、配置驱动、infra-ai 模块职责划分与整体架构',
    '["Ragent 为什么需要 AI 基础设施层？","infra-ai 模块解决什么问题？","没有基础设施层会有哪些痛点？"]',
    'test-1', 6, NULL, 0, NULL, NULL, NULL,
    21, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000012, '2072555556962385920', 'infra-ai-route-circuit', '多模型路由与熔断', 2, 'infra-ai',
    'ModelSelector 候选排序、ModelHealthStore 三态熔断（CLOSED/OPEN/HALF_OPEN）、ModelRoutingExecutor 故障转移；非文档定时同步调度',
    '["百炼挂了怎么自动切换到硅基流动？","三态熔断器的工作原理是什么？","ModelSelector 怎么选择模型？","模型故障转移是怎么实现的？"]',
    'test-1', 8, NULL, 0, NULL, NULL, NULL,
    22, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000013, '2072555556962385920', 'infra-ai-chat-stream', 'Chat调用与流式路由', 2, 'infra-ai',
    'AbstractOpenAIStyleChatClient 模板方法、SSE 流式解析与异步执行、流式路由首包探测机制、TTFT 优化',
    '["Chat 同步调用是怎么封装的？","SSE 流式响应如何解析？","流式路由的首包探测机制是什么？","模板方法模式在 Chat 客户端里怎么用？"]',
    'test-1', 8, NULL, 0, NULL, NULL, NULL,
    23, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000014, '2072555556962385920', 'infra-ai-embedding', 'Embedding向量化客户端', 2, 'infra-ai',
    'Embedding 多供应商客户端实现、向量维度、批量调用、与知识库向量化入库的调用侧关系',
    '["Embedding 客户端怎么屏蔽供应商差异？","Ragent 支持哪些 Embedding 模型？","向量化客户端在 infra-ai 里怎么组织？"]',
    'test-1', 8, NULL, 0, NULL, NULL, NULL,
    24, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000015, '2072555556962385920', 'infra-ai-rerank', 'Rerank重排序', 2, 'infra-ai',
    'BaiLianRerankClient、检索后 Rerank 重排序实现、Rerank 在 RAG 链路中的代码位置；非 RAGAS 评测指标',
    '["Rerank 重排序在 Ragent 里怎么实现？","百炼 Rerank 客户端怎么用？","检索后为什么要做 Rerank？"]',
    'test-1', 8, NULL, 0, NULL, NULL, NULL,
    25, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: RAG 评测 ==========
(
    2059100000000000016, '2072555556962385920', 'rag-eval', 'RAG 评测', 1, 'ragent-docs',
    'Ragent 评测体系：评估集设计、runner 双接口录制、自建指标、RAGAS 实践与测评报告（ragenteval 项目）',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    30, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000017, '2072555556962385920', 'rag-eval-setup', '评测基建与数据初始化', 2, 'rag-eval',
    '评测全景图 init→run→score→report、评估集 schema 设计、create_kbs.py 建库灌文档与意图树初始化脚本',
    '["RAG 效果怎么衡量？","评估集应该怎么设计？","测评数据初始化三个脚本分别做什么？","评测为什么要分成两个仓库？"]',
    'test-1', 6, NULL, 0, NULL, NULL, NULL,
    31, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000018, '2072555556962385920', 'rag-eval-runner', '评测录制与全链路', 2, 'rag-eval',
    '/rag/v3/chat SSE 与 /rag/eval 双接口聚合、EvalController 评测旁路、EvalRecord 字段与录制一次评分 N 次设计',
    '["为什么评测需要跑两个接口？","EvalController 旁路接口返回什么？","runner 怎么一次性收齐答案和检索证据？","录制一次评分 N 次是什么意思？"]',
    'test-1', 8, NULL, 0, NULL, NULL, NULL,
    32, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000019, '2072555556962385920', 'rag-eval-metrics-intent-retrieval', '意图与检索指标', 2, 'rag-eval',
    'intent_top1 准确率、Hit@K、Recall@K、MRR 等自建评测指标；讨论意图分类评测而非意图树配置教程',
    '["intent_top1 怎么算？","意图分类错了对检索有什么影响？","Hit@K 和 Recall@K 有什么区别？","意图指标为什么要自建？"]',
    'test-1', 6, NULL, 0, NULL, NULL, NULL,
    33, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000020, '2072555556962385920', 'rag-eval-metrics-performance', '性能指标', 2, 'rag-eval',
    'TTFT 首字耗时、端到端延迟、P95 口径选择、SSE 流式场景下的性能统计方式',
    '["TTFT 在评测里怎么统计？","性能指标 P95 口径怎么选？","评测里怎么衡量响应速度？"]',
    'test-1', 6, NULL, 0, NULL, NULL, NULL,
    34, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000021, '2072555556962385920', 'rag-eval-ragas', 'RAGAS评测实践', 2, 'rag-eval',
    'RAGAS 选型与五个指标解读、Python judge 并发与 NaN 重试、测评报告生成、RAGAS 常见踩坑',
    '["RAGAS 五个指标分别衡量什么？","RAGAS 跑评测有哪些坑？","测评报告怎么生成？","faithfulness 和 answer_relevancy 有什么区别？"]',
    'test-1', 8, NULL, 0, NULL, NULL, NULL,
    35, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: Ollama与本地部署 ==========
(
    2059100000000000022, '2072555556962385920', 'local-llm', 'Ollama与本地部署', 1, 'ragent-docs',
    '本地大模型部署动机、合规与成本选型、Ollama 架构与安装调用实战',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    40, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000023, '2072555556962385920', 'local-llm-why', '为什么本地部署', 2, 'local-llm',
    '数据合规与隐私、成本结构、离线环境、云端 API vs 本地部署选型、GPU 与机房成本估算',
    '["什么场景必须本地部署大模型？","本地部署和云端 API 成本怎么比？","医疗行业为什么不能用云端大模型 API？","本地部署有哪些中间件？"]',
    'test-1', 6, NULL, 0, NULL, NULL, NULL,
    41, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000024, '2072555556962385920', 'local-llm-ollama', 'Ollama概念与实战', 2, 'local-llm',
    'Ollama Client-Server 架构、ollama serve 与 ollama run、Modelfile、REST API、与 Ragent OllamaChatClient 对接',
    '["Ollama 和 Docker 的类比关系是什么？","ollama serve 和 ollama run 有什么区别？","Ragent 怎么调用本地 Ollama？","Ollama 模型文件存在哪里？"]',
    'test-1', 8, NULL, 0, NULL, NULL, NULL,
    42, 1, 'admin', 'admin', NOW(), NOW(), 0
);
