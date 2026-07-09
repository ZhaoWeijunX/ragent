-- onboarding 知识库意图树导入脚本
--
-- 适用文档目录：resources/docs/knowledge/group/onboarding/（8 篇多格式文档）
-- 设计说明：resources/docs/knowledge/group/onboarding/intent-tree-design.md
--
-- 文档清单：
--   新员工入职指南.md            (Markdown)
--   入职第一天待办.txt             (Tika)
--   员工手册.pdf                   (MinerU)
--   岗位职责说明书.docx            (MinerU)
--   培训课程表.xlsx                (ExcelPoi)
--   办公地点与门禁卡.csv           (Csv)
--   组织架构图.png                 (Image)
--
-- 使用前请替换占位符（全文搜索替换即可）：
-- SELECT id, name, collection_name FROM t_knowledge_base WHERE name LIKE '%onboarding%' OR name LIKE '%入职%';
--   __KB_ID_ONBOARDING__       -> t_knowledge_base 中 onboarding 知识库的 id
--   __COLLECTION_ONBOARDING__  -> 该知识库对应的 Milvus collection_name
--
-- 导入后请清理意图树 Redis 缓存（任选其一）：
--   redis-cli DEL ragent:intent:tree
--   或重启 bootstrap 服务
--
-- 系统交互节点（sys / sys-welcome / sys-about-bot）如已通过
-- docs/examples/intent-node-import/mcp-intent-nodes-import.sql 导入，请勿重复插入。

-- ---------------------------------------------------------------------------
-- 可选：清理本脚本涉及的意图节点后重新导入（慎用，会物理删除记录）
-- ---------------------------------------------------------------------------
-- DELETE FROM t_intent_node
-- WHERE intent_code IN (
--     'onboarding', 'onb-process', 'onb-process-guide', 'onb-process-firstday',
--     'onb-handbook', 'onb-handbook-employee', 'onb-handbook-jd',
--     'onb-training', 'onb-training-courses',
--     'onb-facility', 'onb-facility-location', 'onb-facility-orgchart'
-- );

INSERT INTO t_intent_node (
    id, kb_id, intent_code, name, level, parent_code, description, examples,
    collection_name, top_k, mcp_tool_id, kind, prompt_snippet, prompt_template,
    param_prompt_template, sort_order, enabled, create_by, update_by,
    create_time, update_time, deleted
) VALUES
-- ========== DOMAIN ==========
(
    2059300000000000001, '2075057575803707392', 'onboarding', '新员工入职与培训', 0, NULL,
    '星云科技集团新员工入职流程、首日待办、员工手册、岗位职责、培训课程、办公地点与组织架构；与 group/hr 人事制度互补，聚焦入职场景',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    1, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: 入职流程 ==========
(
    2059300000000000002, '2075057575803707392', 'onb-process', '入职流程', 1, 'onboarding',
    '入职前准备、入职当天全流程、首周安排、Buddy 制度与试用期目标；讨论入职指南而非首日 checklist 勾选',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    10, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059300000000000003, '2075057575803707392', 'onb-process-guide', '入职指南', 2, 'onb-process',
    '《新员工入职指南》：材料清单、入职日时间线、账号开通、首周必修、薪酬考勤福利速览、FAQ；非按时段待办 checklist',
    '["入职当天需要带什么材料？","入职首周有哪些必修培训？","Buddy制度是什么？","入职当天账号有哪些？","试用期目标什么时候制定？","工资哪天发？"]',
    'onboarding', 8, NULL, 0, NULL, NULL, NULL,
    11, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059300000000000004, '2075057575803707392', 'onb-process-firstday', '首日待办', 2, 'onb-process',
    '《入职第一天待办》checklist：09:30 签到、HR 办手续、IT 领设备、培训 ONB-001、系统自检、经理 1:1；按时段逐项待办',
    '["入职第一天上午要做哪些事？","ONB-002课程什么时候截止？","首日待办清单有哪些？","下午4点要完成什么系统自检？","入职第一天几点到前台签到？"]',
    'onboarding', 6, NULL, 0, NULL, NULL, NULL,
    12, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: 制度与手册 ==========
(
    2059300000000000005, '2075057575803707392', 'onb-handbook', '制度与手册', 1, 'onboarding',
    '员工手册制度条款与典型岗位职责说明书；讨论年假考勤离职或 JD 任职资格',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    20, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059300000000000006, '2075057575803707392', 'onb-handbook-employee', '员工手册', 2, 'onb-handbook',
    '《员工手册》PDF：聘用试用期、工作时间考勤、假期制度表、薪酬福利、行为纪律、知识产权、离职管理；非入职流程时间线',
    '["年假有多少天？","试用期一般多长？","辞职需要提前多久通知？","标准工作时间是什么？","薪酬保密有什么规定？","旷工几天会被解除合同？"]',
    'onboarding', 8, NULL, 0, NULL, NULL, NULL,
    21, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059300000000000007, '2075057575803707392', 'onb-handbook-jd', '岗位职责', 2, 'onb-handbook',
    '《岗位职责说明书》：研发/产品/HRBP/销售/运维等典型 JD、核心职责、任职资格、试用期 KPI；非员工手册通用制度',
    '["后端研发工程师的核心职责是什么？","产品经理需要哪些任职资格？","销售经理的KPI有哪些？","HRBP主要负责什么？","运维工程师试用期考核指标？"]',
    'onboarding', 8, NULL, 0, NULL, NULL, NULL,
    22, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: 培训体系 ==========
(
    2059300000000000008, '2075057575803707392', 'onb-training', '培训体系', 1, 'onboarding',
    '入职必修与通用/专业/管理类培训课程表、报名与开课时间',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    30, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059300000000000009, '2075057575803707392', 'onb-training-courses', '培训课程', 2, 'onb-training',
    '培训课程表 XLSX：课程编码 ONB-/GEN-/DEV-/MGT-、课程名称、类别、讲师、开课时间、是否必修；查课表而非入职流程',
    '["新员工必修课程有哪些？","ONB-001是什么课？谁讲？","有没有Java进阶培训？","新经理领导力工作坊什么时候开课？","信息安全与保密课什么时候截止？","Buddy伙伴训练营怎么报名？"]',
    'onboarding', 8, NULL, 0, NULL, NULL, NULL,
    31, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: 办公与环境 ==========
(
    2059300000000000010, '2075057575803707392', 'onb-facility', '办公与环境', 1, 'onboarding',
    '办公楼层工位、门禁权限、停车场与集团组织架构',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    40, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059300000000000011, '2075057575803707392', 'onb-facility-location', '办公地点与门禁', 2, 'onb-facility',
    '办公地点与门禁卡 CSV：location_code、楼层、区域、工位范围、部门、门禁级别、停车区、前台电话；精确查楼层工位',
    '["入职手续在哪个楼层办理？","研发工位在几楼？","LOC-HQ-A3F-001是什么区域？","IT服务窗口在哪？","健身房在几楼？","员工停车场怎么进？"]',
    'onboarding', 6, NULL, 0, NULL, NULL, NULL,
    41, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059300000000000012, '2075057575803707392', 'onb-facility-orgchart', '组织架构', 2, 'onb-facility',
    '组织架构图 PNG：董事会、C-level、事业部（研发/AI/产品/销售/市场等）、共享服务；讨论部门划分而非具体楼层',
    '["公司有哪些事业部？","AI平台部在哪个楼层？","研发效能部负责什么？","COO管哪些部门？","共享服务包括哪些职能？"]',
    'onboarding', 6, NULL, 0, NULL, NULL, NULL,
    42, 1, 'admin', 'admin', NOW(), NOW(), 0
);
