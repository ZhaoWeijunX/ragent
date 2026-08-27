-- cube-control-desk 知识库首页示例问题导入脚本
--
-- 用途：填充 t_sample_question，供首页「试试这些开场」随机展示
-- 前置：请先执行 resources/database/imports/intent-nodes/cube-control-desk-intent-nodes-import.sql，
--       并确认 cube-control-desk 知识库文档已经完成导入和向量化
--
-- 可选：清理本脚本示例后重新导入
-- DELETE FROM t_sample_question
-- WHERE id BETWEEN 2059700000000000001 AND 2059700000000000024;

INSERT INTO t_sample_question (
    id, title, description, question, create_time, update_time, deleted
) VALUES
-- ========== 核心业务链路 ==========
(
    2059700000000000001,
    '接单到订舱',
    'cube-control-desk · SHIPPING、Booking 与 Release 跨域链路',
    '接单到订舱再到放舱的完整链路是什么？',
    NOW(), NOW(), 0
),
(
    2059700000000000002,
    'Booking回调',
    'cube-control-desk · 订舱任务、外部执行与回执',
    '订舱回执成功后页面为什么没有更新？',
    NOW(), NOW(), 0
),
(
    2059700000000000003,
    'BL与Bill Input',
    'cube-control-desk · 接单侧与通道侧边界',
    'BL Intake 和 Bill Input 有什么区别？',
    NOW(), NOW(), 0
),
(
    2059700000000000004,
    'VGM边界',
    'cube-control-desk · VGM Intake 与 VGM Input 数据边界',
    'VGM Intake 和 VGM Input 的数据边界是什么？',
    NOW(), NOW(), 0
),
(
    2059700000000000005,
    'Manifest回调',
    'cube-control-desk · 舱单接单与通道提交',
    'Manifest 提交回调如何处理？',
    NOW(), NOW(), 0
),
(
    2059700000000000006,
    '货物追踪',
    'cube-control-desk · Tracking 事件、通知与补偿',
    '货物追踪事件如何进入通知？',
    NOW(), NOW(), 0
),
(
    2059700000000000007,
    '计划船期',
    'cube-control-desk · Plan、Schedule 与订舱协作',
    'Plan 和 Schedule 分别负责什么？',
    NOW(), NOW(), 0
),

-- ========== 平台支撑能力 ==========
(
    2059700000000000008,
    '任务状态机',
    'cube-control-desk · 任务壳、状态推进与回调幂等',
    '业务当前态和任务状态有什么区别？',
    NOW(), NOW(), 0
),
(
    2059700000000000009,
    '船司配置',
    'cube-control-desk · 租户、船司、账号与运行时配置',
    '租户和船司配置的优先级是什么？',
    NOW(), NOW(), 0
),
(
    2059700000000000010,
    '邮件建单',
    'cube-control-desk · 邮件、Agent 与工单创建',
    '邮件记录如何驱动工单创建？',
    NOW(), NOW(), 0
),
(
    2059700000000000011,
    '三方集成',
    'cube-control-desk · FMS 客户端与外部状态边界',
    '第三方接口成功但业务状态没更新怎么办？',
    NOW(), NOW(), 0
),
(
    2059700000000000012,
    'Excel导入',
    'cube-control-desk · 文件识别、模板与领域写入',
    'Excel 文件如何识别并导入领域数据？',
    NOW(), NOW(), 0
),
(
    2059700000000000013,
    'Job重试',
    'cube-control-desk · 定时任务、重试与补偿',
    '业务重试任务如何执行？',
    NOW(), NOW(), 0
),
(
    2059700000000000014,
    '校验策略',
    'cube-control-desk · Processor、船司策略与扩展点',
    '新增一个字段校验应该改哪里？',
    NOW(), NOW(), 0
),
(
    2059700000000000015,
    '租户安全',
    'cube-control-desk · 租户上下文、权限与审计',
    '如何确认数据是否按租户隔离？',
    NOW(), NOW(), 0
),
(
    2059700000000000016,
    '日志追踪',
    'cube-control-desk · 日志、统计与可观测证据链',
    '如何从日志追踪一次业务请求？',
    NOW(), NOW(), 0
),
(
    2059700000000000017,
    'Chat协作',
    'cube-control-desk · 会话、消息与业务任务关联',
    'Chat 会话、消息和任务如何关联？',
    NOW(), NOW(), 0
),
(
    2059700000000000018,
    '港口基础',
    'cube-control-desk · 航线、港口、国家与地点查询',
    '港口基础数据由哪个服务查询？',
    NOW(), NOW(), 0
),

-- ========== 研发与治理 ==========
(
    2059700000000000019,
    '本地启动',
    'cube-control-desk · 新人环境与工程入口',
    'cube-control-desk 本地环境如何启动？',
    NOW(), NOW(), 0
),
(
    2059700000000000020,
    '代码定位',
    'cube-control-desk · Controller 到最终状态写入',
    '如何从 Controller 定位到最终状态写入？',
    NOW(), NOW(), 0
),
(
    2059700000000000021,
    '联调准备',
    'cube-control-desk · 前端、测试与后端协作',
    '前后端联调需要准备哪些信息？',
    NOW(), NOW(), 0
),
(
    2059700000000000022,
    '测试证据',
    'cube-control-desk · api-test 与验证边界',
    'api-test 存在是否代表已经验证通过？',
    NOW(), NOW(), 0
),
(
    2059700000000000023,
    '文档冲突',
    'cube-control-desk · 文档、代码与历史证据',
    '如何处理文档和当前代码不一致？',
    NOW(), NOW(), 0
),
(
    2059700000000000024,
    '安全变更',
    'cube-control-desk · 变更前检查、验证与发布',
    '修改一个业务需求前应该检查什么？',
    NOW(), NOW(), 0
);
