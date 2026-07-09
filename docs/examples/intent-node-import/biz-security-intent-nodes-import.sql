-- biz-security 知识库意图树导入脚本
--
-- 适用文档目录：resources/docs/knowledge/biz/biz-security/（8 篇多格式文档）
-- 设计说明：resources/docs/knowledge/biz/biz-security/intent-tree-design.md
--
-- 文档清单：
--   信息安全管理制度.md          (Markdown)
--   数据分类分级规范.docx          (MinerU)
--   等保合规自查报告.pdf           (MinerU)
--   应急响应流程图.png             (Image)
--   安全事件通报模板.txt           (Tika)
--   系统资产清单.csv               (Csv)
--   权限申请对照表.xlsx            (ExcelPoi)
--
-- 使用前请替换占位符（全文搜索替换即可）：
SELECT id, name, collection_name FROM t_knowledge_base WHERE name LIKE '%企业信息安全与合规%';
--   __KB_ID_BIZ_SECURITY__       -> t_knowledge_base 中 biz-security 知识库的 id
--   __COLLECTION_BIZ_SECURITY__  -> 该知识库对应的 Milvus collection_name
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
--     'biz-security', 'sec-policy', 'sec-policy-mgmt', 'sec-policy-classification',
--     'sec-compliance', 'sec-compliance-djcp',
--     'sec-ops', 'sec-ops-incident', 'sec-ops-assets',
--     'sec-access', 'sec-access-rbac'
-- );

INSERT INTO t_intent_node (
    id, kb_id, intent_code, name, level, parent_code, description, examples,
    collection_name, top_k, mcp_tool_id, kind, prompt_snippet, prompt_template,
    param_prompt_template, sort_order, enabled, create_by, update_by,
    create_time, update_time, deleted
) VALUES
-- ========== DOMAIN ==========
(
    2059200000000000001, '__KB_ID_BIZ_SECURITY__', 'biz-security', '企业信息安全与合规', 0, NULL,
    '星云科技集团信息安全制度、数据分级、等保合规、应急响应、系统资产与权限管理；集团级安全知识库，区别于 OA/保险等单系统规范',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    1, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: 安全制度与规范 ==========
(
    2059200000000000002, '__KB_ID_BIZ_SECURITY__', 'sec-policy', '安全制度与规范', 1, 'biz-security',
    '集团信息安全总则、数据分类分级标准与标签规则；讨论制度原则、管理职责、违规处理等',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    10, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059200000000000003, '__KB_ID_BIZ_SECURITY__', 'sec-policy-mgmt', '信息安全管理制度', 2, 'sec-policy',
    '《信息安全管理制度》全文：组织职责、资产管理、访问控制、数据安全、网络安全、SDL、第三方管理、事件管理、业务连续性、培训与违规处理；非数据标签定级细则',
    '["集团信息安全管理的基本原则是什么？","安全事件P0级别响应时限是多少？","外包人员能否接触L4数据？","开发环境可以使用生产数据吗？","备份策略RPO和RTO要求是什么？","违反信息安全制度会受到什么处理？"]',
    '__COLLECTION_BIZ_SECURITY__', 8, NULL, 0, NULL, NULL, NULL,
    11, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059200000000000004, '__KB_ID_BIZ_SECURITY__', 'sec-policy-classification', '数据分类分级', 2, 'sec-policy',
    '《数据分类分级规范》：L1～L4 等级定义、8 类数据分类、标签命名 XY.DATA.*、差异化加密脱敏导出策略；讨论字段定级而非等保测评',
    '["L3和L4数据在存储加密上有什么区别？","个人身份证号应该定什么数据等级？","数据标签命名格式是什么？","销售合同数据应该定几级？","个人信息最低定几级？","数据分级判定流程是什么？"]',
    '__COLLECTION_BIZ_SECURITY__', 8, NULL, 0, NULL, NULL, NULL,
    12, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: 合规与审计 ==========
(
    2059200000000000005, '__KB_ID_BIZ_SECURITY__', 'sec-compliance', '合规与审计', 1, 'biz-security',
    '网络安全等级保护合规自查、测评备案、差距分析与整改计划',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    20, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059200000000000006, '__KB_ID_BIZ_SECURITY__', 'sec-compliance-djcp', '等保合规自查', 2, 'sec-compliance',
    '2025 年度等保合规自查报告：三级/二级系统备案号、符合率、中危低危差距项、整改计划 R-01～R-05；讨论等保测评而非日常权限审批',
    '["2025年等保自查整体符合率是多少？","OA系统的等保备案号是什么？","等保自查发现的主要风险项有哪些？","CRM API限流整改什么时候完成？","集团有多少个等保三级系统？","日志留存整改计划何时完成？"]',
    '__COLLECTION_BIZ_SECURITY__', 8, NULL, 0, NULL, NULL, NULL,
    21, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: 安全运营 ==========
(
    2059200000000000007, '__KB_ID_BIZ_SECURITY__', 'sec-ops', '安全运营', 1, 'biz-security',
    '安全事件应急响应、通报模板、流程图与系统资产登记清单',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    30, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059200000000000008, '__KB_ID_BIZ_SECURITY__', 'sec-ops-incident', '安全事件应急', 2, 'sec-ops',
    '安全事件通报模板（初报/续报/终报/对外口径）与应急响应流程图：P0～P3 分级、黄金四小时、SOC 联系方式、监管 72 小时报告；非系统资产编码查询',
    '["发现安全事件后多长时间内要初报？","安全事件通报模板包含哪些章节？","P0事件应急处理的六个步骤是什么？","个人信息泄露向监管报告的时限是多少？","P1事件响应时限是多少？","SOC值班电话是多少？"]',
    '__COLLECTION_BIZ_SECURITY__', 8, NULL, 0, NULL, NULL, NULL,
    31, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059200000000000009, '__KB_ID_BIZ_SECURITY__', 'sec-ops-assets', '系统资产登记', 2, 'sec-ops',
    '系统资产清单 CSV：system_code（如 SYS-OA-001）、系统名称、业务 Owner、技术负责人、等保级别、数据最高密级、部署环境；精确查系统编码与负责人',
    '["SYS-ERP-002是什么系统？负责人是谁？","哪些系统是等保三级？","数据中台的数据最高密级是多少？","SYS-HR-004的技术负责人是谁？","互联网保险核心系统的等保级别？","Ragent知识库的系统编码是什么？"]',
    '__COLLECTION_BIZ_SECURITY__', 6, NULL, 0, NULL, NULL, NULL,
    32, 1, 'admin', 'admin', NOW(), NOW(), 0
),

-- ========== CATEGORY: 访问控制 ==========
(
    2059200000000000010, '__KB_ID_BIZ_SECURITY__', 'sec-access', '访问控制', 1, 'biz-security',
    '角色权限矩阵、特权账号、审批层级与权限复核周期',
    '[]', NULL, NULL, NULL, 0, NULL, NULL, NULL,
    40, 1, 'admin', 'admin', NOW(), NOW(), 0
),
(
    2059200000000000011, '__KB_ID_BIZ_SECURITY__', 'sec-access-rbac', '权限申请与审批', 2, 'sec-access',
    '权限申请对照表：ROLE-* 角色编码、适用系统、数据最高等级、是否特权、审批层级（直属经理/总监/CISO/安全委员会）、复核周期；非等保备案号查询',
    '["申请ERP财务总监角色需要谁审批？","CRM销售代表能访问什么级别的数据？","IAM管理员是不是特权角色？","L4数据导出需要哪一级审批？","eHR系统管理员需要什么审批？","IT运维超级管理员审批层级是什么？"]',
    '__COLLECTION_BIZ_SECURITY__', 8, NULL, 0, NULL, NULL, NULL,
    41, 1, 'admin', 'admin', NOW(), NOW(), 0
);
