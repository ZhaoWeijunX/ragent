-- logistics-visibility 知识库首页示例问题导入脚本
--
-- 用途：填充 t_sample_question，供首页「试试这些开场」随机展示
-- 前置：请先执行 resources/database/imports/intent-nodes/logistics-visibility-intent-nodes-import.sql，
--       并确认 logistics-visibility 知识库文档已经完成导入和向量化
--
-- 可选：清理本脚本示例后重新导入
-- DELETE FROM t_sample_question
-- WHERE id BETWEEN 2059900000000000001 AND 2059900000000000025;

INSERT INTO t_sample_question (
    id, title, description, question, create_time, update_time, deleted
) VALUES
-- ========== 新人导航 ==========
(
    '2059900000000000001',
    '七服务架构',
    'logistics-visibility · 服务职责与协作边界',
    '物流可视化平台的七个服务分别负责什么？',
    NOW(), NOW(), 0
),
(
    '2059900000000000002',
    '核心模型',
    'logistics-visibility · Subscription、Job 与 Task',
    'Subscription、Job 和 Task 之间是什么关系？',
    NOW(), NOW(), 0
),
(
    '2059900000000000003',
    '最小联调',
    'logistics-visibility · 环境、启动顺序与调用路径',
    '本地如何跑通一次最小订阅联调链路？',
    NOW(), NOW(), 0
),

-- ========== 核心业务链路 ==========
(
    '2059900000000000004',
    '订阅判重',
    'logistics-visibility · 订阅入口、复用与生命周期',
    '相同的物流订阅请求如何判重和复用？',
    NOW(), NOW(), 0
),
(
    '2059900000000000005',
    '任务拆分',
    'logistics-visibility · 船司 Job 与港区 Job',
    '全链路订阅为什么要拆分为船司和港区 Job？',
    NOW(), NOW(), 0
),
(
    '2059900000000000006',
    '状态机',
    'logistics-visibility · Schedule Job 与 Task 推进',
    'Job 和 Task 的状态是如何推进的？',
    NOW(), NOW(), 0
),
(
    '2059900000000000007',
    '采集清洗',
    'logistics-visibility · 采集回执与清洗任务',
    '采集完成后，系统如何生成清洗任务？',
    NOW(), NOW(), 0
),
(
    '2059900000000000008',
    '消息顺序',
    'logistics-visibility · dataId 回填与一致性',
    '为什么必须先回填 dataId 再发送后续消息？',
    NOW(), NOW(), 0
),
(
    '2059900000000000009',
    '融合模式',
    'logistics-visibility · v1、v2 与定制融合',
    '全链路融合 v1、v2 和美的定制模式有什么区别？',
    NOW(), NOW(), 0
),
(
    '2059900000000000010',
    '变化通知',
    'logistics-visibility · DataCompare 与用户通知',
    'DataCompare 如何判断轨迹变化并触发通知？',
    NOW(), NOW(), 0
),

-- ========== 可靠性与平台能力 ==========
(
    '2059900000000000011',
    '消息隔离',
    'logistics-visibility · RocketMQ Topic、Tag 与消费组',
    'RocketMQ 的 Topic、Tag 和消费组是如何隔离业务的？',
    NOW(), NOW(), 0
),
(
    '2059900000000000012',
    '并发幂等',
    'logistics-visibility · 分布式锁与幂等键',
    '相同订阅并发提交时如何避免重复创建任务？',
    NOW(), NOW(), 0
),
(
    '2059900000000000013',
    '失败补偿',
    'logistics-visibility · MQ 重试与人工重放',
    'MQ 消费失败后有哪些自动和人工补偿手段？',
    NOW(), NOW(), 0
),
(
    '2059900000000000014',
    '削峰去重',
    'logistics-visibility · Redis ZSET 与漏桶',
    '强制融合如何使用 Redis ZSET 去重和削峰？',
    NOW(), NOW(), 0
),
(
    '2059900000000000015',
    '缓存一致性',
    'logistics-visibility · Redis 与 Caffeine',
    'Redis 和 Caffeine 多级缓存不一致时怎么排查？',
    NOW(), NOW(), 0
),
(
    '2059900000000000016',
    '双存储',
    'logistics-visibility · MySQL 与 Mongo 数据边界',
    '哪些数据存 MySQL，哪些数据存 Mongo？',
    NOW(), NOW(), 0
),
(
    '2059900000000000017',
    '日志追踪',
    'logistics-visibility · SLS、traceId 与告警',
    '如何用 traceId 追踪一次订阅的完整处理链路？',
    NOW(), NOW(), 0
),

-- ========== 运营后台与问题排查 ==========
(
    '2059900000000000018',
    '后台权限',
    'logistics-visibility · Admin 鉴权与真实安全边界',
    '前端没有菜单是否代表后端接口一定安全？',
    NOW(), NOW(), 0
),
(
    '2059900000000000019',
    '任务查询',
    'logistics-visibility · 订阅、Job、Task 与 Mongo',
    '如何从一个订阅定位到 Job、Task 和 Mongo 轨迹？',
    NOW(), NOW(), 0
),
(
    '2059900000000000020',
    '重订阅',
    'logistics-visibility · reSub 调用链与风险控制',
    '执行 reSub 前需要检查哪些状态和风险？',
    NOW(), NOW(), 0
),
(
    '2059900000000000021',
    '人工补偿',
    'logistics-visibility · reSendMq 与 rePush',
    'reSendMq 和 rePush 分别适用于什么问题？',
    NOW(), NOW(), 0
),
(
    '2059900000000000022',
    '异常排查',
    'logistics-visibility · 订阅到通知的证据链',
    '订阅成功但一直没有轨迹数据应该怎么排查？',
    NOW(), NOW(), 0
),

-- ========== 专项业务链路 ==========
(
    '2059900000000000023',
    '码头链路',
    'logistics-visibility · WHARF 订阅到退订',
    'WHARF 码头订阅、清洗、融合和退订链路是什么？',
    NOW(), NOW(), 0
),
(
    '2059900000000000024',
    '海关与快递',
    'logistics-visibility · CUSTOMS、EXPRESS 数据查询',
    'CUSTOMS 月表路由和 EXPRESS AF 数据查询分别怎么实现？',
    NOW(), NOW(), 0
),
(
    '2059900000000000025',
    '客户交付',
    'logistics-visibility · API 下载、通知与主动推送',
    'API 下载、系统通知和客户主动推送有什么区别？',
    NOW(), NOW(), 0
);
