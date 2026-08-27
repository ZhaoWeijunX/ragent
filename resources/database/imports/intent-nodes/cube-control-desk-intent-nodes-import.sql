-- cube-control-desk 知识库意图树导入脚本
--
-- 适用文档目录：D:/code/cube-control-desk/cube-control-desk-knowledge-base/
-- 文档范围：核心业务链路、平台支撑能力、跨域流程、开发入门、角色协作、治理与演进证据
-- 设计原则：按用户要解决的业务/研发问题分叶子节点，不按 Markdown 文件名机械拆分
--
-- 使用前请替换占位符（全文搜索替换即可）：
-- SELECT id, name, collection_name FROM t_knowledge_base
-- WHERE name LIKE '%cube-control-desk%' OR name LIKE '%cube%';
--   __KB_ID_CUBE_CONTROL_DESK__       -> t_knowledge_base 中该知识库的 id
--   __COLLECTION_CUBE_CONTROL_DESK__   -> 该知识库对应的 collection_name
--
-- 导入后请清理意图树 Redis 缓存（任选其一）：
--   redis-cli DEL ragent:intent:tree
--   或重启 bootstrap 服务
--
-- 本脚本只写入意图树；首页展示用示例问题见：
-- resources/database/imports/sample-questions/cube-control-desk-sample-questions-import.sql

-- ---------------------------------------------------------------------------
-- 可选：清理本脚本涉及的意图节点后重新导入（慎用，会物理删除记录）
-- ---------------------------------------------------------------------------
-- DELETE FROM t_intent_node
-- WHERE intent_code IN (
--     'cube-control-desk',
--     'ccd-core-business', 'ccd-entrusted-shipping', 'ccd-booking', 'ccd-release',
--     'ccd-bill-bl-intake', 'ccd-bill-input', 'ccd-vgm-intake', 'ccd-vgm-input',
--     'ccd-manifest-intake', 'ccd-manifest-input', 'ccd-shipment-tracking', 'ccd-plan-schedule',
--     'ccd-platform', 'ccd-task-state', 'ccd-account-carrier-config', 'ccd-mail-agent',
--     'ccd-third-party-integration', 'ccd-file-excel-oss', 'ccd-job-retry-script',
--     'ccd-validation-extension', 'ccd-system-tenant-ops', 'ccd-observability',
--     'ccd-chat-collaboration', 'ccd-route-location',
--     'ccd-engineering',
--     'ccd-cross-domain', 'ccd-developer-onboarding', 'ccd-role-collaboration',
--     'ccd-governance-evidence'
-- );

INSERT INTO t_intent_node (
    id, kb_id, intent_code, name, level, parent_code, description, examples,
    collection_name, top_k, mcp_tool_id, kind, prompt_snippet, prompt_template,
    param_prompt_template, sort_order, enabled, create_by, update_by,
    create_time, update_time, deleted
) VALUES
-- ========== DOMAIN ==========
(
    2059600000000000001, '__KB_ID_CUBE_CONTROL_DESK__', 'cube-control-desk', 'cube-control-desk 项目知识库', 0, NULL,
    'cube-control-desk 海运业务平台知识库，覆盖接单、订舱、放舱、提单、VGM、舱单、追踪、计划，以及任务、配置、文件、三方集成和研发协作能力',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    1, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: 核心业务链路 ==========
(
    2059600000000000002, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-core-business', '核心业务链路', 1, 'cube-control-desk',
    '围绕海运业务事实和生命周期的核心模块；回答入口、数据、状态、下游调用、回调和排障，不吞并平台支撑能力的内部实现',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    10, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000003, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-entrusted-shipping', 'SHIPPING 接单与托书工单', 2, 'ccd-core-business',
    'SHIPPING 接单链路：邮件或对话记录进入接单流程，经过 Agent 解析、工单创建、分配和审核，最终进入去订舱；重点区分 entrusted_work_order、entrusted_info 与其他接单域',
    '["SHIPPING邮件接单到托书工单的完整流程是什么？","邮件没有生成SHIPPING工单应该从哪里排查？","SHIPPING工单和BILL工单的数据边界是什么？","去订舱后如何确认接单链路结果？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    11, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000004, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-booking', 'Booking 订舱', 2, 'ccd-core-business',
    'Booking 订舱任务的创建、参数装配、账号和渠道选择、外部执行、bookingCallback 回执及当前状态；订舱成功不等于放舱成功',
    '["Booking订舱任务是怎么创建的？","订舱从请求到回调的完整链路是什么？","订舱任务没有生成怎么排查？","订舱回执成功后页面为什么没有更新？","Booking和Release是什么关系？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    12, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000005, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-release', 'Release 放舱', 2, 'ccd-core-business',
    'Release 从订舱成功派生放舱任务，经过监听策略、外部执行、releaseSpaceCallback 回写和历史记录；重点回答放舱当前态、任务和回执排障',
    '["放舱任务什么时候创建？","订舱成功但没有生成Release任务怎么办？","Release回调如何推进放舱状态？","放舱页面状态和历史记录不一致怎么查？","Booking成功是否代表Release成功？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    13, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000006, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-bill-bl-intake', 'BILL/BL 接单', 2, 'ccd-core-business',
    'BL 接单侧门面：从邮件、Agent 或人工操作形成独立 bl_* 主数据、工单和快照，再与 Bill Input 协作；不把 BL Intake 和通道侧 Bill Input 混为一个数据域',
    '["BILL/BL接单端到端流程是什么？","BL Intake和Bill Input有什么区别？","BL接单使用哪些主表和当前状态？","BL工单回调异常如何排查？","BL接单如何进入后续补料流程？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    14, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000007, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-bill-input', 'Bill Input 提单补料', 2, 'ccd-core-business',
    '通道侧提单补料能力：数据转换与校验、船司策略、官网提交、回执、提交检查、文件监听、文件识别和比对；主记录和 BL 接单侧职责分离',
    '["Bill Input从补料到官网提交的流程是什么？","Bill Input的校验策略如何选择？","提单提交后如何处理回执？","提交检查和文件监听分别做什么？","Bill Input校验失败应该查哪一层？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    15, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000008, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-vgm-intake', 'VGM Intake 接单侧', 2, 'ccd-core-business',
    '接单侧 VGM 门面：维护 vgm_info 当前态和 vgm_detail 详情快照，支持从 BL 创建、保存、提交，以及独立或联合提交流程；不复用通道侧 VGM 主记录',
    '["VGM Intake和VGM Input的数据边界是什么？","接单侧VGM从哪里创建？","VGM独立提交和联合提交有什么区别？","vgm_info和vgm_detail分别保存什么？","VGM接单状态异常怎么排查？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    16, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000009, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-vgm-input', 'VGM Input 通道侧', 2, 'ccd-core-business',
    '独立 VGM 官网填写和提交能力：使用 biz_vgm_record、biz_vgm_container 等模型，通过执行任务、官网调用、回执和状态推进完成通道侧流程',
    '["VGM Input通道侧提交链路是什么？","biz_vgm_record和vgm_info有什么区别？","VGM官网执行任务失败怎么排查？","VGM回执如何更新状态？","VGM Input如何扩展船司字段？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    17, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000010, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-manifest-intake', 'Manifest 接单', 2, 'ccd-core-business',
    '舱单接单侧：创建、编辑、提交、回调和重提，维护接单侧数据与状态；当前代码、设计稿和任务包存在差异时以代码证据为准',
    '["Manifest接单侧的完整流程是什么？","Manifest接单如何创建和编辑？","Manifest提交回调如何处理？","Manifest失败后如何重提？","Manifest Intake和Manifest Input如何分工？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    18, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000011, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-manifest-input', 'Manifest Input 通道侧', 2, 'ccd-core-business',
    '舱单通道侧提交能力：提交、持久化、任务分支、外部回执和并发边界；明确区分已由当前代码证明的能力与仅存在于设计材料中的内容',
    '["Manifest Input提交和持久化链路是什么？","Manifest Input外部回执如何推进状态？","Manifest Input有哪些并发和事务风险？","Manifest Input当前代码能证明哪些能力？","Manifest Input异常应该如何分层排查？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    19, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000012, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-shipment-tracking', 'Shipment Tracking 货物追踪', 2, 'ccd-core-business',
    '货物追踪链路：事件订阅、回调或增量数据、展示计算、通知和异常补偿；重点区分追踪事实、展示投影与跨租户数据边界',
    '["Shipment Tracking的事件链路是什么？","货物追踪事件如何进入通知？","Tracking当前态和历史事件如何区分？","追踪数据没有更新应该查什么？","Tracking异常补偿如何定位？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    20, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000013, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-plan-schedule', 'Plan 与 Schedule 计划船期', 2, 'ccd-core-business',
    '计划监控与船期能力：Monitor 创建与调度、通知、超时关闭、Schedule 刷新，以及与订舱业务的责任边界和状态时间关系',
    '["Plan和Schedule分别负责什么？","计划Monitor如何创建和调度？","计划超时关闭和通知怎么实现？","Schedule刷新链路是什么？","Plan状态和Booking状态如何关联？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    21, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: 平台支撑能力 ==========
(
    2059600000000000014, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-platform', '平台支撑能力', 1, 'cube-control-desk',
    '被多个业务域复用的任务、配置、消息、文件、Job、校验、系统和可观测能力；回答公共能力如何被调用及其边界，不替代具体业务域的生命周期',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    30, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000015, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-task-state', '任务与状态机', 2, 'ccd-platform',
    'biz_task、biz_customer_task 等任务壳，状态机动作、租户任务、派生任务、回调守卫和状态转换；说明任务状态不等于业务当前态',
    '["cube-control-desk的任务模型有哪些？","任务状态机如何推进状态？","任务回调如何保证幂等？","业务当前态和任务状态有什么区别？","如何排查任务卡住或重复执行？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    31, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000016, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-account-carrier-config', '账号、船司与业务配置', 2, 'ccd-platform',
    '租户、船司、账号、渠道和运行时业务配置的解析、选择、缓存与变更边界；重点定位配置读取点，不把配置存在误判为生产已启用',
    '["订舱账号和船司配置如何解析？","租户和船司配置的优先级是什么？","运行时配置变更后如何生效？","账号配置缺失如何排查？","新增船司能力需要改哪些配置和代码？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    32, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000017, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-mail-agent', '邮件、通知与 Agent', 2, 'ccd-platform',
    '邮件记录、模板、通知、推送、公告和 Agent 解析能力；覆盖邮件到工单的公共链路，以及通知失败、重试和调用方责任',
    '["邮件记录如何驱动工单创建？","Agent解析结果如何进入业务工单？","通知模板和发送配置在哪里？","邮件通知失败如何排查？","公告和业务通知的边界是什么？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    33, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000018, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-third-party-integration', '三方 FMS 与客户端集成', 2, 'ccd-platform',
    '外部 FMS、客户端和通道集成：统一客户端封装、配置解析、请求响应、错误映射和外部调用边界',
    '["三方FMS调用链如何定位？","外部客户端配置从哪里读取？","第三方接口成功但业务状态没更新怎么办？","FMS客户端如何处理超时和错误？","新增三方接口需要遵循什么边界？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    34, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000019, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-file-excel-oss', '文件、Excel、模板与 OSS', 2, 'ccd-platform',
    '文件上传、Excel 识别与导入、模板、附件和对象存储的生命周期；区分文件落盘、领域识别、数据写入和附件事务边界',
    '["Excel文件如何识别并导入领域数据？","文件上传到OSS的链路是什么？","Excel模板和业务导入如何关联？","文件存在但领域数据没写入怎么查？","附件和领域数据是否在同一个事务里？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    35, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000020, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-job-retry-script', 'Job、重试、调度与脚本', 2, 'ccd-platform',
    '定时 Job、业务重试任务、调度、Groovy 脚本和补偿操作的运行模型；重点回答触发、并发、幂等、积压、重试和人工恢复',
    '["cube-control-desk有哪些Job和调度任务？","业务重试任务如何执行？","Job重复执行如何保证幂等？","脚本任务如何接入和排障？","任务积压或超时应该如何处理？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    36, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000021, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-validation-extension', '校验策略与扩展点', 2, 'ccd-platform',
    'Processor、校验器、船司或渠道策略、规则选择和扩展点；回答字段校验、跨字段规则、策略分发和新增实现的最小改动面',
    '["Bill Input的校验处理链是什么？","船司校验策略如何选择？","新增一个字段校验应该改哪里？","跨字段校验和单字段校验如何分工？","校验失败如何定位到具体规则？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    37, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000022, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-system-tenant-ops', '系统、租户、安全与运维', 2, 'ccd-platform',
    '系统配置、租户上下文、权限与审计、运维操作和安全边界；区分前端入口表现与后端真实控制，关注数据隔离和受控运维',
    '["cube-control-desk的租户上下文在哪里生效？","系统操作日志如何记录？","运维接口的后端权限边界是什么？","如何确认数据是否按租户隔离？","配置和运维变更如何审计？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    38, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000023, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-observability', '日志、统计、BI 与可观测性', 2, 'ccd-platform',
    '日志采集、统计查询、BI 图表、ES 查询契约和可观测指标；回答从请求/任务到展示的证据链，不将静态配置当作运行结果',
    '["cube-control-desk的日志和统计链路是什么？","BI图表数据从哪里查询？","如何从日志追踪一次业务请求？","统计时间窗口和ES查询有什么约束？","页面没有数据应该如何定位？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    39, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000024, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-chat-collaboration', 'Chat 与内部协作', 2, 'ccd-platform',
    'Chat 会话、消息、任务关联和内部协同能力；说明消息写入顺序、关联键、调用方集成和异常边界',
    '["Chat会话、消息和任务如何关联？","内部协同消息的写入顺序是什么？","Chat消息发送失败怎么排查？","如何从会话追到业务任务？","Chat能力和邮件通知有什么区别？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    40, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000025, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-route-location', '航线、港口、国家与地点基础', 2, 'ccd-platform',
    'PortService 等地点基础能力，以及航线、港口、国家数据的查询、参数契约、配置和被业务模块复用的边界',
    '["港口基础数据由哪个服务查询？","航线和港口参数如何传递？","新增港口地点需要改哪些地方？","PortService查询失败如何排查？","地点基础数据如何被Booking和Tracking复用？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    41, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: 跨域与研发协作 ==========
(
    2059600000000000026, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-engineering', '跨域与研发协作', 1, 'cube-control-desk',
    '跨模块业务链路、开发入门、角色协作和知识库治理；回答如何理解、修改、验证和交接项目，不与单一业务域的运行职责混淆',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    50, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000027, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-cross-domain', '跨域业务链路', 2, 'ccd-engineering',
    '跨模块端到端流程：接单到订舱到放舱、BL 到 Bill Input、VGM/Manifest Intake 到 Input、任务回调、邮件建单、文件导入和 Tracking 补偿',
    '["接单到订舱再到放舱的完整链路是什么？","BL Intake如何进入Bill Input？","VGM Intake到VGM Input如何协作？","任务下发、回调和状态流转如何串起来？","邮件、Agent和工单创建如何关联？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    51, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000028, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-developer-onboarding', '开发入门与代码定位', 2, 'ccd-engineering',
    '新人环境启动、仓库导航、入口定位、Controller 到 Manager/Service/Mapper、MySQL/Mongo/Redis/OSS 边界、配置解析、调试和安全变更流程',
    '["cube-control-desk本地环境如何启动？","如何从Controller定位到最终状态写入？","MySQL、Mongo、Redis和OSS分别负责什么？","如何定位一个Job或回调的入口？","修改一个业务需求前应该检查什么？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    52, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000029, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-role-collaboration', '前端、测试与运维协作', 2, 'ccd-engineering',
    '前端 API 联调、测试场景设计、api-test 证据、运维配置/Job/告警和事故诊断交接；强调已执行证据与静态代码能力的边界',
    '["前后端联调需要准备哪些信息？","如何为一个业务不变量设计测试场景？","api-test存在是否代表已经验证通过？","运维接手Job和告警需要哪些信息？","事故诊断交接包应该包含什么？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 8, NULL, 0, NULL, NULL, NULL,
    53, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059600000000000030, '__KB_ID_CUBE_CONTROL_DESK__', 'ccd-governance-evidence', '知识库治理与演进证据', 2, 'ccd-engineering',
    '知识库来源可靠性、文章维护、代码入口索引、数据库与配置索引、文档代码差异、未知项、历史设计、任务包和发布证据的正确使用',
    '["cube-control-desk知识库的来源优先级是什么？","如何处理文档和当前代码不一致？","任务卡能否证明功能已经上线？","如何维护知识库文章的保鲜？","怎样区分静态代码证据和运行验证证据？"]',
    '__COLLECTION_CUBE_CONTROL_DESK__', 6, NULL, 0, NULL, NULL, NULL,
    54, 1, 'admin', 'admin', NOW(), NOW(), 0
);
