-- logistics-visibility 知识库意图树导入脚本
--
-- 适用文档目录：D:\code\logistics-visibility-kb（36 篇主题文档）
-- 设计说明：按知识库现有五个主题域组织，每篇主题文档对应一个叶子意图。
--
-- 使用前请替换占位符：
-- SELECT id, name, collection_name FROM t_knowledge_base
-- WHERE name LIKE '%logistics-visibility%' OR name LIKE '%物流可视%';
--   __KB_ID_LOGISTICS_VISIBILITY__       -> 知识库 id
--   __COLLECTION_LOGISTICS_VISIBILITY__  -> collection_name
--
-- 导入后：redis-cli DEL ragent:intent:tree
--
-- 系统交互节点 sys* 见 system-intent-nodes-import.sql，请勿重复插入。

-- ---------------------------------------------------------------------------
-- 可选：清理后重导（慎用）
-- ---------------------------------------------------------------------------
-- DELETE FROM t_intent_node
-- WHERE intent_code IN (
--     'logistics-visibility',
--     'lv-onboarding', 'lv-architecture', 'lv-contracts', 'lv-local-start',
--     'lv-core-flow', 'lv-subscription-lifecycle', 'lv-subscription-jobs',
--     'lv-schedule-task-state', 'lv-collection-cleaning', 'lv-sf-cleaning',
--     'lv-dataid-ordering', 'lv-fusion', 'lv-data-compare-notify', 'lv-job-end-resubscribe',
--     'lv-reliability', 'lv-mq-isolation', 'lv-lock-idempotency', 'lv-mq-retry',
--     'lv-force-fusion-throttle', 'lv-cache-consistency', 'lv-api-limit-billing',
--     'lv-dual-storage', 'lv-observability', 'lv-nacos-security', 'lv-status-mapping-performance',
--     'lv-operations', 'lv-admin-security', 'lv-admin-data-query', 'lv-admin-resub',
--     'lv-admin-resend-repush', 'lv-source-channel-crontab', 'lv-troubleshooting',
--     'lv-alert-suppression',
--     'lv-specialized', 'lv-wharf', 'lv-customs', 'lv-express', 'lv-terminal',
--     'lv-carrier-cleaning', 'lv-midea-fusion', 'lv-api-download-push'
-- );

INSERT INTO t_intent_node (
    id, kb_id, intent_code, name, level, parent_code, description, examples,
    collection_name, top_k, mcp_tool_id, kind, prompt_snippet, prompt_template,
    param_prompt_template, sort_order, enabled, create_by, update_by,
    create_time, update_time, deleted
) VALUES
-- ========== DOMAIN ==========
(
    '2059800000000000001', '__KB_ID_LOGISTICS_VISIBILITY__', 'logistics-visibility', '物流可视化全链路', 0, NULL,
    '物流可视化平台知识库：七服务架构、订阅调度采集清洗融合通知链路、可靠性能力、运营排障与专项业务',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    1, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: 新人导航 ==========
(
    '2059800000000000002', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-onboarding', '新人导航与系统全景', 1, 'logistics-visibility',
    '面向新人的七服务职责、核心模型与消息契约、环境配置和最小联调路径；用于建立系统全景',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    10, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000003', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-architecture', '七服务职责与整体架构', 2, 'lv-onboarding',
    '七个服务的职责边界、关键技术、核心代码位置、完整调用流程以及服务间协作关系',
    '["物流可视化平台由哪些服务组成？","七个服务分别负责什么？","订阅、调度、清洗、融合和通知如何串起来？","整体架构的核心调用流程是什么？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    11, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000004', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-contracts', '数据模型与消息契约', 2, 'lv-onboarding',
    '订阅、Job、Task、轨迹数据等核心对象关系，以及跨服务 RocketMQ 消息契约和字段流转',
    '["核心数据模型有哪些？","Subscription、Job 和 Task 是什么关系？","跨服务消息契约如何定义？","一个字段如何贯穿各服务？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    12, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000005', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-local-start', '环境启动与最小联调', 2, 'lv-onboarding',
    '各服务启动入口、配置加载链路、依赖环境、启动顺序和从订阅到结果查询的最小联调路径',
    '["本地环境如何启动？","七个服务应该按什么顺序启动？","Nacos配置从哪里加载？","最小联调链路怎么跑通？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    13, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: 核心业务链路 ==========
(
    '2059800000000000006', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-core-flow', '核心业务链路', 1, 'logistics-visibility',
    '从订阅入口到 Job/Task、采集、清洗、融合、变化判断、通知和生命周期结束的主业务链路',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    20, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000007', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-subscription-lifecycle', '订阅入口与生命周期', 2, 'lv-core-flow',
    '订阅请求入口、参数处理、重复判断、已有订阅复用、状态变化以及订阅生命周期边界',
    '["订阅入口在哪里？","相同订阅如何判重？","已有订阅在什么情况下会被复用？","订阅生命周期包含哪些状态？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    21, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000008', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-subscription-jobs', '全链路订阅拆分与聚合', 2, 'lv-core-flow',
    '全链路订阅如何拆分为船司与港区 Job，以及子 Job 创建、关联、执行和结果聚合',
    '["全链路订阅为什么要拆成多个Job？","船司Job和港区Job如何生成？","子Job如何关联原订阅？","拆分后的结果怎样聚合？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    22, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000009', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-schedule-task-state', 'Schedule Job与Task状态机', 2, 'lv-core-flow',
    'Schedule 服务中的 Job 创建、Task 调度推进、状态字段、状态转换条件和异常边界',
    '["Schedule如何创建Job和Task？","Job与Task状态如何推进？","Task失败后Job状态怎么变化？","状态机有哪些关键字段？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    23, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000010', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-collection-cleaning', '采集回执与清洗任务生成', 2, 'lv-core-flow',
    '采集任务下发、采集结果回执、结果校验，以及回执如何驱动后续清洗任务生成',
    '["采集任务如何下发？","采集回执由谁消费？","采集完成后如何生成清洗任务？","采集失败会怎样处理？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    24, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000011', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-sf-cleaning', 'SF清洗路由与结果落库', 2, 'lv-core-flow',
    'SF 服务按模板和渠道选择清洗路径，执行标准化、状态映射，并将清洗结果写入存储',
    '["SF清洗任务从哪里进入？","不同渠道如何选择清洗模板？","清洗结果存到哪里？","清洗失败如何定位？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    25, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000012', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-dataid-ordering', 'dataId回填与消息顺序', 2, 'lv-core-flow',
    '清洗结果 dataId 的生成和回填、相关消息发送顺序，以及乱序或回填失败的一致性影响',
    '["dataId在哪里生成和回填？","为什么要先回填dataId再发消息？","消息乱序会产生什么问题？","dataId回填失败怎么处理？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    26, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000013', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-fusion', '全链路融合模式', 2, 'lv-core-flow',
    '轨迹全链路融合 v1、v2 与美的定制模式的入口、数据处理差异、适用边界和隔离关系',
    '["全链路融合v1和v2有什么区别？","融合任务如何触发？","美的定制融合走哪条链路？","不同融合模式如何隔离？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    27, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000014', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-data-compare-notify', '状态变化判断与通知', 2, 'lv-core-flow',
    'DataCompare 如何比较新旧轨迹、判断状态变化、生成通知事件并推动用户通知',
    '["DataCompare如何判断轨迹变化？","什么情况下会给用户发通知？","新旧数据比较发生在哪里？","状态没变化为什么没有通知？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    28, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000015', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-job-end-resubscribe', 'Job结束与再订阅', 2, 'lv-core-flow',
    'JobEnd 条件、停止推送、任务重开、重新订阅，以及结束状态与后续补偿之间的边界',
    '["Job在什么条件下结束？","JobEnd后为什么停止推送？","已结束任务如何重开？","再订阅会复用旧任务吗？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    29, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: 可靠性与平台能力 ==========
(
    '2059800000000000016', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-reliability', '可靠性与平台能力', 1, 'logistics-visibility',
    '消息隔离、并发幂等、补偿削峰、缓存配置、限流计费、双存储、可观测性和性能治理',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    30, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000017', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-mq-isolation', 'RocketMQ消息隔离', 2, 'lv-reliability',
    'RocketMQ Topic、Tag、消费组的职责划分，生产消费关系，以及不同业务消息的隔离边界',
    '["RocketMQ的Topic和Tag如何划分？","不同消费组为什么能隔离消费？","一条消息会被哪些服务消费？","新增消息类型应该放在哪个Topic？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    31, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000018', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-lock-idempotency', '分布式锁与幂等串行化', 2, 'lv-reliability',
    '相同订阅的分布式锁、幂等键设计、串行处理范围，以及锁超时和并发请求的边界',
    '["相同订阅如何避免并发重复创建？","分布式锁的key怎么设计？","幂等键包含哪些字段？","锁过期会不会产生重复任务？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    32, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000019', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-mq-retry', 'MQ重试补偿与人工重放', 2, 'lv-reliability',
    '消息回执、消费失败重试、自动补偿、死信或异常处理，以及人工重放的分层策略',
    '["MQ消费失败后如何重试？","回执失败如何补偿？","什么时候需要人工重放？","重放消息如何避免重复副作用？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    33, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000020', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-force-fusion-throttle', '强制融合去重与削峰', 2, 'lv-reliability',
    '强制融合场景使用 Redis ZSET 去重，并通过漏桶式处理控制任务进入速度和热点压力',
    '["强制融合为什么使用Redis ZSET？","重复融合任务如何去重？","漏桶削峰是怎么实现的？","ZSET积压时如何排查？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    34, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000021', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-cache-consistency', 'Redis与Caffeine缓存一致性', 2, 'lv-reliability',
    'Redis 分布式缓存与 Caffeine 本地缓存的分层使用、配置刷新、失效策略和一致性风险',
    '["Redis和Caffeine分别缓存什么？","配置更新后本地缓存如何刷新？","多级缓存不一致怎么排查？","缓存失效策略是什么？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    35, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000022', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-api-limit-billing', 'API限流计费与状态边界', 2, 'lv-reliability',
    'API 请求限流、订阅次数或费用计算、业务状态与接口响应状态之间的真实边界',
    '["API限流在哪里实现？","订阅如何计费？","接口成功是否代表订阅成功？","限流状态和业务状态有什么区别？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    36, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000023', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-dual-storage', 'MySQL与Mongo双存储', 2, 'lv-reliability',
    'MySQL 与 Mongo 的数据职责、写入路径、历史轨迹查询方式以及跨存储一致性边界',
    '["哪些数据存MySQL，哪些存Mongo？","历史轨迹从哪里查询？","双存储写入失败如何处理？","MySQL和Mongo数据不一致怎么定位？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    37, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000024', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-observability', '日志追踪与监控告警', 2, 'lv-reliability',
    'SLS 全链路日志、traceId 传播、关键日志定位、监控指标与告警排障链路',
    '["如何用traceId追踪一次订阅？","SLS里应该查哪些关键日志？","跨服务traceId如何传递？","监控告警后怎么定位具体任务？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    38, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000025', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-nacos-security', 'Nacos配置与敏感信息治理', 2, 'lv-reliability',
    'Nacos 环境配置加载优先级、分环境管理、配置变更影响，以及账号密钥等敏感配置风险',
    '["Nacos配置优先级是什么？","不同环境的配置如何隔离？","配置修改后哪些服务会受影响？","敏感配置应该如何治理？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    39, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000026', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-status-mapping-performance', '状态映射与热点性能', 2, 'lv-reliability',
    '船司状态映射缓存、清洗热点调用路径、频繁查询与对象转换的性能优化和回归边界',
    '["船司状态映射缓存在哪里？","状态映射为什么是热点路径？","如何减少重复查询和转换？","优化后要回归哪些清洗场景？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    40, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: 运营后台与问题排查 ==========
(
    '2059800000000000027', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-operations', '运营后台与问题排查', 1, 'logistics-visibility',
    'Admin 后台安全与数据查询、重订阅和人工补偿、渠道切换、端到端故障定位与告警治理',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    50, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000028', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-admin-security', 'Admin鉴权与安全边界', 2, 'lv-operations',
    'Admin 登录认证、动态菜单、前端权限表现，以及后端接口真实鉴权和数据访问安全边界',
    '["Admin登录鉴权流程是什么？","动态菜单如何生成？","前端没有菜单是否代表后端接口安全？","后台接口的真实权限边界在哪里？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    51, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000029', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-admin-data-query', '订阅任务与Mongo查询', 2, 'lv-operations',
    '运营后台查询订阅、Job、Task 和 Mongo 轨迹数据的入口、关联条件、查询顺序与权限边界',
    '["后台如何查询一个订阅的Job和Task？","怎样从订阅定位Mongo轨迹？","查询链路的关联字段是什么？","为什么后台查不到某条轨迹？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    52, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000030', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-admin-resub', 'reSub重订阅', 2, 'lv-operations',
    'reSub 后台操作的完整调用链、旧任务处理、新订阅创建、并发风险和操作前检查',
    '["reSub重订阅会做什么？","reSub会复用旧订阅吗？","重订阅前需要检查哪些状态？","并发执行reSub有什么风险？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    53, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000031', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-admin-resend-repush', 'reSendMq与rePush补偿', 2, 'lv-operations',
    'reSendMq、rePush 等人工补偿入口的作用差异、调用路径、适用故障和重复执行风险',
    '["reSendMq和rePush有什么区别？","消息丢失应该用哪个补偿操作？","客户没收到通知怎么补推？","人工补偿如何避免重复处理？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    54, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000032', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-source-channel-crontab', '数据源渠道切换与调度重算', 2, 'lv-operations',
    '运营后台调整数据源或渠道后的配置落库、任务影响，以及 Crontab 重新计算和生效边界',
    '["如何切换数据源或采集渠道？","渠道切换后旧任务会怎样？","Crontab什么时候重新计算？","修改配置后为什么调度时间没变化？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    55, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000033', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-troubleshooting', '端到端异常定位', 2, 'lv-operations',
    '沿订阅、Job、Task、MQ、清洗、融合和通知分段排查常见异常，建立端到端证据链',
    '["订阅成功但没有轨迹怎么排查？","Task一直不执行应该查哪里？","清洗成功但没有通知怎么办？","如何按traceId端到端定位故障？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 10, NULL, 0, NULL, NULL, NULL,
    56, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000034', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-alert-suppression', '告警静默与重复抑制', 2, 'lv-operations',
    '监控告警生成、静默窗口、重复告警抑制、恢复条件和可能漏报的边界场景',
    '["重复告警如何抑制？","静默窗口是怎么计算的？","告警恢复后何时可以再次触发？","为什么故障发生了却没有告警？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    57, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: 专项业务链路 ==========
(
    '2059800000000000035', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-specialized', '专项业务链路', 1, 'logistics-visibility',
    '码头、海关、快递、船计划、单船司清洗、美的定制融合与客户数据交付等专项链路',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    60, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000036', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-wharf', 'WHARF码头全链路', 2, 'lv-specialized',
    'WHARF 码头业务从订阅、调度、采集清洗、融合到退订的完整链路和边界',
    '["WHARF码头订阅如何创建？","码头任务如何调度和清洗？","WHARF数据怎样参与融合？","码头订阅如何退订？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    61, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000037', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-customs', 'CUSTOMS海关链路', 2, 'lv-specialized',
    'CUSTOMS 海关订阅与数据处理、按月表路由、Mongo 查询及跨月份数据边界',
    '["CUSTOMS海关链路怎么走？","海关数据为什么按月分表？","跨月数据如何查询？","CUSTOMS数据如何路由到Mongo？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    62, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000038', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-express', 'EXPRESS快递链路', 2, 'lv-specialized',
    'EXPRESS 快递订阅、任务处理、AF 数据查询以及快递数据返回和异常排查路径',
    '["EXPRESS快递如何订阅？","快递任务如何生成？","AF数据从哪里查询？","快递订阅成功但查不到数据怎么办？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    63, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000039', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-terminal', 'TERMINAL船计划链路', 2, 'lv-specialized',
    'TERMINAL 船计划数据与港区数据的采集、联合清洗、状态匹配和结果输出链路',
    '["TERMINAL船计划数据如何获取？","船计划与港区数据如何联合清洗？","两类数据按什么字段匹配？","联合清洗失败怎么排查？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    64, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000040', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-carrier-cleaning', '单船司清洗与回归', 2, 'lv-specialized',
    '单船司清洗规则、状态映射配置、代码扩展位置，以及变更后的定向回归验证方法',
    '["新增船司清洗规则应该改哪里？","船司原始状态如何映射？","单船司清洗如何验证？","状态映射调整后要回归哪些场景？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    65, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000041', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-midea-fusion', '美的定制融合隔离', 2, 'lv-specialized',
    '美的定制融合与普通融合的入口、客户识别、处理差异、数据隔离和误入链路风险',
    '["美的定制融合与普通融合有什么区别？","如何识别美的客户数据？","两种融合链路怎样隔离？","普通数据误入定制融合会怎样？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    66, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    '2059800000000000042', '__KB_ID_LOGISTICS_VISIBILITY__', 'lv-api-download-push', 'API下载通知与客户推送', 2, 'lv-specialized',
    'API 下载、站内或消息通知、客户主动推送三类数据交付方式的权限、频率与状态边界',
    '["API下载需要什么权限？","客户推送频率如何控制？","下载、通知和主动推送有什么区别？","接口返回成功但客户没收到数据怎么排查？"]',
    '__COLLECTION_LOGISTICS_VISIBILITY__', 8, NULL, 0, NULL, NULL, NULL,
    67, 1, 'admin', 'admin', NOW(), NOW(), 0
);
