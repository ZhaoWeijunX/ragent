-- ragent-test 知识库意图树导入脚本
--
-- 适用文档目录：resources/docs/ragent-test/（52 篇 Markdown）
-- 设计说明：resources/docs/ragent-test/intent-tree-design.md
--
-- 文档系列：
--   AI知识库建设（11）| 大模型调度引擎实战（8）| AI知识问答篇（18）
--   RAG 评测（11）| Ollama与vLLM扫盲（3）| 技术文档（1）

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
--     'local-llm', 'local-llm-why', 'local-llm-ollama',
--     'rag-qa', 'rag-qa-pipeline', 'rag-qa-memory', 'rag-qa-rewrite', 'rag-qa-intent',
--     'rag-qa-retrieval', 'rag-qa-mcp', 'rag-qa-prompt', 'rag-qa-stream', 'rag-qa-ratelimit',
--     'tech-docs', 'tech-docs-threadpool'
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
    'Ragent 项目配套技术文档，涵盖知识库工程化、AI 知识问答全链路、AI 基础设施层、RAG 评测、本地模型部署与横切技术专题',
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

-- ========== CATEGORY: AI知识问答 ==========
(
    2059100000000000027, '2072555556962385920', 'rag-qa', 'AI知识问答', 1, 'ragent-docs',
    'StreamChatPipeline 八阶段问答全链路：记忆、改写、意图、检索、MCP、Prompt、流式、排队限流',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    45, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000028, '2072555556962385920', 'rag-qa-pipeline', '问答全链路全景', 2, 'rag-qa',
    'StreamChatPipeline 八个阶段、三个短路点、loadMemory→rewrite→resolveIntents→guidance→retrieve→streamRagResponse 全景地图；非单环节深挖',
    '["StreamChatPipeline的八个阶段分别是什么？","一次知识问答在后端经历哪些步骤？","问答Pipeline有哪些短路点？","handleGuidance什么时候触发？","空检索时系统怎么处理？"]',
    'test-1', 6, NULL, 0, NULL, NULL, NULL,
    46, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000029, '2072555556962385920', 'rag-qa-memory', '会话记忆与摘要', 2, 'rag-qa',
    'JdbcConversationMemoryStore、对话历史加载、滑动窗口、ConversationSummaryService 摘要压缩策略与触发时机',
    '["会话记忆是怎么加载和存储的？","对话历史为什么要做摘要压缩？","history-keep-turns和summary-start-turns怎么配？","记忆加载为什么要在改写之前？","摘要压缩的Prompt约束有哪些？"]',
    'test-1', 8, NULL, 0, NULL, NULL, NULL,
    47, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000030, '2072555556962385920', 'rag-qa-rewrite', '查询改写与拆分', 2, 'rag-qa',
    'QueryRewriteService、同义词标准化、LLM 改写与子问题拆分、RewriteResult、标点规则 fallback',
    '["查询改写和子问题拆分是怎么做的？","RewriteResult包含哪些字段？","代词消解在改写里怎么处理？","复合问题怎么拆成多个子问题？","改写失败时的fallback策略是什么？"]',
    'test-1', 8, NULL, 0, NULL, NULL, NULL,
    48, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000031, '2072555556962385920', 'rag-qa-intent', '意图识别与引导', 2, 'rag-qa',
    '意图树 DOMAIN/CATEGORY/TOPIC、分类 Prompt 模板、候选封顶算法、歧义引导 IntentGuidanceService、top_k 与 collection 映射；非评测 intent_top1 指标',
    '["意图树为什么要设计成三级结构？","意图分类Prompt模板包含哪些部分？","多个子问题的意图候选怎么封顶？","歧义引导什么时候触发？","意图分数出来后怎么决定查哪个库、查多少条？","只有TOPIC叶子参与分类是什么意思？"]',
    'test-1', 8, NULL, 0, NULL, NULL, NULL,
    49, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000032, '2072555556962385920', 'rag-qa-retrieval', '多通道检索与后处理', 2, 'rag-qa',
    'MultiChannelRetrievalEngine、IntentDirectedSearchChannel、VectorGlobalSearchChannel、RRF 融合、DeduplicationPostProcessor、RerankPostProcessor',
    '["多通道并行检索有哪些通道？","向量全局检索什么时候触发？","检索后处理流水线有哪些步骤？","RRF融合策略怎么配置？","意图定向检索和全局检索怎么配合？"]',
    'test-1', 8, NULL, 0, NULL, NULL, NULL,
    50, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000033, '2072555556962385920', 'rag-qa-mcp', 'MCP工具调用', 2, 'rag-qa',
    '问答流水线中 MCP 触发时机、McpParameterExtractService 参数提取、KB+MCP 混合 Prompt、与 kind=2 意图节点关系；非 MCP Server 工具实现代码',
    '["MCP工具在问答流水线里什么时候被调用？","MCP参数提取器怎么工作？","知识库和MCP工具混合回答怎么组装Prompt？","MCP意图和知识库意图怎么区分？"]',
    'test-1', 8, NULL, 0, NULL, NULL, NULL,
    51, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000034, '2072555556962385920', 'rag-qa-prompt', 'Prompt组装', 2, 'rag-qa',
    '检索上下文格式化、answer-chat-kb / answer-chat-mcp 等模板选择、系统 Prompt 与子问题证据拼接',
    '["RAG回答的Prompt是怎么组装的？","检索到的chunk怎么格式化进上下文？","KB和MCP混答用哪个Prompt模板？","context-format模板做什么？"]',
    'test-1', 8, NULL, 0, NULL, NULL, NULL,
    52, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000035, '2072555556962385920', 'rag-qa-stream', '流式生成链路', 2, 'rag-qa',
    'streamRagResponse、SSE 推送、正常流式链路、异常路径与客户端断开处理；非 infra-ai 首包探测与模型路由',
    '["流式回答是怎么推送给前端的？","streamRagResponse主要做什么？","流式生成异常时怎么处理？","用户断开SSE连接后端怎么感知？"]',
    'test-1', 8, NULL, 0, NULL, NULL, NULL,
    53, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000036, '2072555556962385920', 'rag-qa-ratelimit', '排队限流', 2, 'rag-qa',
    'FairDistributedRateLimiter、对话入口 chatEntryExecutor、ZSET 排队、Lua 原子出队、SSE 排队状态推送；非文件上传限流',
    '["对话入口的分布式排队限流是怎么工作的？","FairDistributedRateLimiter怎么实现公平排队？","排队状态怎么通过SSE推给用户？","对话限流和文件上传限流有什么区别？"]',
    'test-1', 8, NULL, 0, NULL, NULL, NULL,
    54, 1, 'admin', 'admin', NOW(), NOW(), 0
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
),
-- ========== CATEGORY: 技术文档 ==========
(
    2059100000000000025, '2072555556962385920', 'tech-docs', '技术文档', 1, 'ragent-docs',
    '横切技术专题：并发与线程池、可观测性等不隶属于单一业务系列的深度实现文档',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    50, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059100000000000026, '2072555556962385920', 'tech-docs-threadpool', '线程池设计与实现', 2, 'tech-docs',
    'ThreadPoolExecutorConfig 九个线程池、分层并行（子问题/通道/内层检索）、TtlExecutors 上下文传递、SynchronousQueue 与拒绝策略选型；非文件上传限流、非 RAGAS 性能评测指标',
    '["Ragent 里有多少个线程池，分别干什么用？","chatEntryExecutor 和全局限流是怎么配合的？","ragContextExecutor 和 ragRetrievalExecutor 有什么区别？","为什么检索链路用 CallerRunsPolicy 而入口用 AbortPolicy？","线程池为什么要用 TtlExecutors 包装？"]',
    'test-1', 8, NULL, 0, NULL, NULL, NULL,
    51, 1, 'admin', 'admin', NOW(), NOW(), 0
);
