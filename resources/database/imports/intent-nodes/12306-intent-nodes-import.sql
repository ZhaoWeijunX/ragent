-- 12306（拿个offer-12306）知识库意图树导入脚本
--
-- 适用文档目录：resources/docs/12306-pdf-doc/（89 篇 PDF，其中 2 篇近似空文档不纳入叶子映射）
-- 设计说明：resources/docs/12306-pdf-doc/intent-tree-design.md（按正文内容聚类，非原目录结构）
--
-- 空文档（建议不入库）：
--   4.核心技术文档/用户敏感信息展示时学会脱敏.pdf          （仅跳转说明）
--   5.手摸手…/手摸手之梳理核心业务.pdf                      （仅 ProcessOn 外链）
--
-- 使用前请替换占位符：
-- SELECT id, name, collection_name FROM t_knowledge_base WHERE name LIKE '%12306%' OR name LIKE '%拿个offer%';
--   __KB_ID_12306__       -> 知识库 id
--   __COLLECTION_12306__  -> collection_name
--
-- 导入后：redis-cli DEL ragent:intent:tree
--
-- 系统交互节点 sys* 见 mcp-intent-nodes-import.sql，请勿重复插入。

-- ---------------------------------------------------------------------------
-- 可选：清理后重导（慎用）
-- ---------------------------------------------------------------------------
-- DELETE FROM t_intent_node
-- WHERE intent_code IN (
--     'train12306',
--     't12306-bootstrap', 't12306-boot-run', 't12306-boot-scaffold', 't12306-boot-standards',
--     't12306-primitives', 't12306-prim-patterns', 't12306-prim-threadpool', 't12306-prim-cache',
--     't12306-prim-sharding', 't12306-prim-security', 't12306-prim-components',
--     't12306-domains', 't12306-dom-user', 't12306-dom-passenger', 't12306-dom-ticket',
--     't12306-hotspots', 't12306-hot-inventory', 't12306-hot-concurrency',
--     't12306-ops', 't12306-ops-troubleshoot', 't12306-ops-reliability', 't12306-ops-deploy'
-- );

INSERT INTO t_intent_node (
    id, kb_id, intent_code, name, level, parent_code, description, examples,
    collection_name, top_k, mcp_tool_id, kind, prompt_snippet, prompt_template,
    param_prompt_template, sort_order, enabled, create_by, update_by,
    create_time, update_time, deleted
) VALUES
-- ========== DOMAIN ==========
(
    2059500000000000001, '__KB_ID_12306__', 'train12306', '拿个offer-12306实战', 0, NULL,
    '高铁售票系统工程实战：本地启动与脚手架、技术原语、用户/乘车人/购票业务落地、余票一致性与高并发、排错与云部署',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    1, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: 入门与工程搭建 ==========
(
    2059500000000000002, '__KB_ID_12306__', 't12306-bootstrap', '入门与工程搭建', 1, 'train12306',
    '如何把 12306 项目在本地跑起来、搭工程骨架与建模、遵守开发规范；非云上部署、非业务购票细节',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    10, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059500000000000003, '__KB_ID_12306__', 't12306-boot-run', '本地启动跑通', 2, 't12306-bootstrap',
    '克隆项目、安装中间件、快速启动前后端、用户体系建设概要；讨论本地如何跑通，非云服务器部署、非编译启动报错排查',
    '["12306项目怎么克隆和启动？","需要安装哪些中间件？","后端如何快速启动？","前端如何快速启动？","用户体系建设概要是什么？"]',
    '__COLLECTION_12306__', 8, NULL, 0, NULL, NULL, NULL,
    11, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059500000000000004, '__KB_ID_12306__', 't12306-boot-scaffold', '工程脚手架与建模', 2, 't12306-bootstrap',
    'SpringBoot 单/多模块创建、工程目录设计、初始数据库表、梳理表关系；工程起步与建模，不含空文档「梳理核心业务」外链',
    '["工程目录结构如何设计？","如何创建SpringBoot多模块？","初始数据库表有哪些？","如何梳理数据库表关系？"]',
    '__COLLECTION_12306__', 8, NULL, 0, NULL, NULL, NULL,
    12, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059500000000000005, '__KB_ID_12306__', 't12306-boot-standards', '开发规范', 2, 't12306-bootstrap',
    '架构师编程规范、代码格式化、代码检查、消息队列正确使用姿势',
    '["架构师编程规范有哪些？","代码格式化为什么重要？","代码检查怎么做？","消息队列正确使用姿势是什么？"]',
    '__COLLECTION_12306__', 6, NULL, 0, NULL, NULL, NULL,
    13, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: 通用技术原语 ==========
(
    2059500000000000006, '__KB_ID_12306__', 't12306-primitives', '通用技术原语', 1, 'train12306',
    '可复用的横切技术：设计模式、线程池、Redis 锁、分库分表、安全原理、基础组件库；偏原理与组件，非具体购票业务步骤',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    20, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059500000000000007, '__KB_ID_12306__', 't12306-prim-patterns', '设计模式', 2, 't12306-primitives',
    '责任链重构与抽象、策略模式落地与抽象；模式原理，非购票责任链业务代码细节',
    '["责任链模式怎么抽象？","策略模式在项目里怎么用？","复杂业务怎么用责任链重构？","如何抽象策略模式？"]',
    '__COLLECTION_12306__', 8, NULL, 0, NULL, NULL, NULL,
    21, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059500000000000008, '__KB_ID_12306__', 't12306-prim-threadpool', '线程池与并发基础', 2, 't12306-primitives',
    'Hutool Builder 建池、Dubbo 快速消费线程池、开源线程池框架、Mybatis 拒绝策略、lock 写法、Java8 并行流；实现原理，非面试场景/参数 FAQ',
    '["Hutool怎么用Builder创建线程池？","参考Dubbo怎么做快速消费线程池？","lock.lock为什么写到try外面？","Java8并行流有什么坑？","Mybatis拒绝策略怎么扩展？"]',
    '__COLLECTION_12306__', 8, NULL, 0, NULL, NULL, NULL,
    22, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059500000000000009, '__KB_ID_12306__', 't12306-prim-cache', 'Redis锁与缓存原理', 2, 't12306-primitives',
    'Redis 分布式锁演进、Redisson 原理、缓存与数据库一致性方案；原理文，非节假日 Redis 扛量、非余票一致性落地',
    '["Redis分布式锁怎么演进的？","Redisson分布式锁原理是什么？","缓存与数据库一致性如何解决？"]',
    '__COLLECTION_12306__', 8, NULL, 0, NULL, NULL, NULL,
    23, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059500000000000010, '__KB_ID_12306__', 't12306-prim-sharding', '分片与大数据查询', 2, 't12306-primitives',
    '分库分表平滑上线回滚、ShardingSphere-Proxy、深分页、千万数据防内存溢出；通用方案，非用户/乘车人/订单表落地',
    '["分库分表如何平滑上线与回滚？","ShardingSphere-Proxy怎么入门？","如何解决深分页？","查询千万数据如何避免内存溢出？"]',
    '__COLLECTION_12306__', 8, NULL, 0, NULL, NULL, NULL,
    24, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059500000000000011, '__KB_ID_12306__', 't12306-prim-security', '数据与配置安全原理', 2, 't12306-primitives',
    '防止用户敏感数据泄露、配置文件敏感信息泄漏、ShardingSphere 加密上线思路；安全原理，脱敏落地见用户域（空 stub 脱敏文不入树）',
    '["如何防止用户敏感数据泄露？","如何防止配置文件敏感信息泄漏？","ShardingSphere数据加密怎么上线？"]',
    '__COLLECTION_12306__', 8, NULL, 0, NULL, NULL, NULL,
    25, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059500000000000012, '__KB_ID_12306__', 't12306-prim-components', '基础组件库', 2, 't12306-primitives',
    '从零写组件库及公共/Web/持久层/日志/幂等/分布式ID/用户基础/规约/设计模式/基础模块；组件怎么实现，非幂等 HTTP 应用场景 FAQ',
    '["如何从零到一写组件库？","幂等组件库怎么实现？","分布式ID组件怎么做？","Web组件库有什么？","日志组件库如何实现？"]',
    '__COLLECTION_12306__', 8, NULL, 0, NULL, NULL, NULL,
    26, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: 业务域落地 ==========
(
    2059500000000000013, '__KB_ID_12306__', 't12306-domains', '业务域落地', 1, 'train12306',
    '用户、乘车人、购票交易等业务代码与流程落地；偏手摸手实现，非纯面试题表述',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    30, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059500000000000014, '__KB_ID_12306__', 't12306-dom-user', '用户域', 2, 't12306-domains',
    '注册接口、用户分库分表、敏感信息加密存储、脱敏展示落地、注册防缓存穿透；用户域实现，非面试布隆容量题、非空脱敏 stub',
    '["注册用户接口怎么做？","用户如何实现分库分表？","如何加密存储敏感信息？","用户敏感信息展示如何脱敏？","注册如何防止缓存穿透？"]',
    '__COLLECTION_12306__', 8, NULL, 0, NULL, NULL, NULL,
    31, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059500000000000015, '__KB_ID_12306__', 't12306-dom-passenger', '乘车人域', 2, 't12306-domains',
    '乘车人模块开发、乘车人分库分表、本人车票订单查看',
    '["乘车人模块怎么开发？","乘车人如何分库分表？","本人车票订单怎么查看？"]',
    '__COLLECTION_12306__', 8, NULL, 0, NULL, NULL, NULL,
    32, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059500000000000016, '__KB_ID_12306__', 't12306-dom-ticket', '购票交易域', 2, 't12306-domains',
    '购票流程/v2、购票责任链验证、列车检索、座位分配、支付、令牌限流、车票搜索用 Redis 非 ES、订单分库；购票实现，非超卖/Binlog 面试专题',
    '["列车购票流程怎么实现？","v2购票流程有何不同？","购票责任链如何验证？","如何完成列车数据检索？","如何发起支付？","为什么库存扣减要令牌限流？","车票搜索为什么用Redis不是ES？","订单如何分库分表？"]',
    '__COLLECTION_12306__', 8, NULL, 0, NULL, NULL, NULL,
    33, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: 高频难题 ==========
(
    2059500000000000017, '__KB_ID_12306__', 't12306-hotspots', '高频难题', 1, 'train12306',
    '余票库存一致性与高并发性能等跨文档热点：超卖、Binlog、缓存击穿、节假日流量等',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    40, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059500000000000018, '__KB_ID_12306__', 't12306-hot-inventory', '余票库存与一致性', 2, 't12306-hotspots',
    '防库存超卖、中间站点余票更新、Binlog 延迟、RocketMQ 顺序消费、列车余票缓存与数据库一致性；库存一致性专题',
    '["购买列车余票如何防止库存超卖？","中间站点余票如何更新？","余票Binlog更新延迟怎么解决？","监听Binlog的RocketMQ如何保证顺序性？","列车余票如何保障缓存数据库一致性？"]',
    '__COLLECTION_12306__', 8, NULL, 0, NULL, NULL, NULL,
    41, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059500000000000019, '__KB_ID_12306__', 't12306-hot-concurrency', '高并发与性能', 2, 't12306-hotspots',
    '缓存击穿双重判定锁、节假日购票 Redis 扛量、注册布隆容量与碰撞率、核心接口性能优化、项目线程池使用场景；高并发面试与性能',
    '["缓存击穿双重判定锁如何优化？","节假日高并发购票Redis能扛住吗？","布隆过滤器容量和碰撞率怎么设？","12306核心接口性能优化做了什么？","项目什么场景使用线程池？"]',
    '__COLLECTION_12306__', 8, NULL, 0, NULL, NULL, NULL,
    42, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: 排错与上线 ==========
(
    2059500000000000020, '__KB_ID_12306__', 't12306-ops', '排错与上线', 1, 'train12306',
    '本地报错排查、可靠性与可观测、压测与云服务器部署上线',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    50, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059500000000000021, '__KB_ID_12306__', 't12306-ops-troubleshoot', '启动排错与实践FAQ', 2, 't12306-ops',
    'Maven 编译、MySQL8 启动、Windows Command line too long、分布式模式调用报错、构造器注入、幂等 HTTP 场景、线程池参数配置；排错与实践 FAQ',
    '["Maven编译报错怎么办？","MySQL8启动项目报错？","Windows启动Command line is too long怎么办？","为什么分布式模式调用报错？","为什么用构造器注入？","幂等组件HTTP应用场景？","线程池参数怎么配置合理？"]',
    '__COLLECTION_12306__', 6, NULL, 0, NULL, NULL, NULL,
    51, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059500000000000022, '__KB_ID_12306__', 't12306-ops-reliability', '可靠性与可观测', 2, 't12306-ops',
    'OOM 第一时间感知、全局异常拦截、订单延时关闭技术选型、延时关单消息处理、抽象响应实体、雪花 ID、CPU 小知识、上线前压测',
    '["应用OOM如何第一时间知道？","订单延时关闭怎么做技术选型？","创建订单支付后延时关闭消息怎么办？","如何抽象响应实体？","如何生成雪花算法ID？","上线前如何压测？"]',
    '__COLLECTION_12306__', 8, NULL, 0, NULL, NULL, NULL,
    52, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059500000000000023, '__KB_ID_12306__', 't12306-ops-deploy', '云上部署', 2, 't12306-ops',
    '云服务器部署 12306：机型、防火墙端口、JDK/Nginx 与上线步骤；云部署，非本地快速启动',
    '["云服务器怎么部署12306？","需要开放哪些端口？","JDK和Nginx怎么安装？","阿里云如何部署12306项目？"]',
    '__COLLECTION_12306__', 8, NULL, 0, NULL, NULL, NULL,
    53, 1, 'admin', 'admin', NOW(), NOW(), 0
);
