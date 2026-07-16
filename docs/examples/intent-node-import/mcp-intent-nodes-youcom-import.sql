-- You.com 联网搜索（youcom_search）MCP 意图节点导入脚本
--
-- 前置条件：
-- 1. mcp-server 已配置 youcom.search.api-key（或环境变量 YDC_API_KEY），且 youcom_search 工具已注册
-- 2. bootstrap 已连接 MCP Server（默认 http://localhost:9099），启动日志含 toolId: youcom_search
--
-- 导入后请执行：
--   psql -f docs/examples/intent-node-import/mcp-intent-nodes-youcom-prompt-update.sql
-- 或从 docs/examples/prompt/youcom-mcp-parameter-extract.st 复制到管理后台 youcom-search 节点
--
-- 节点结构：
--   youcom          level=0 DOMAIN   kind=2(MCP)  无 mcp_tool_id
--   youcom-search   level=1 CATEGORY kind=2(MCP)  mcp_tool_id=youcom_search
--
-- 若 intent_code 已存在，请先删除或调整后再导入

INSERT INTO t_intent_node (
    id, kb_id, intent_code, name, level, parent_code, description, examples,
    collection_name, top_k, mcp_tool_id, kind, prompt_snippet, prompt_template,
    param_prompt_template, sort_order, enabled, create_by, update_by,
    create_time, update_time, deleted
) VALUES
(
    1998603043960786948, NULL, 'youcom', '联网搜索服务', 0, NULL,
    '基于 You.com 的实时联网搜索，查询最新资讯、新闻、公开网页信息，适用于本地知识库无法覆盖的时效性问题',
    '[]',
    NULL, NULL, NULL, 2, NULL, NULL, NULL, 19, 1, 'admin', 'admin',
    NOW(), NOW(), 0
),
(
    1998603043960786949, NULL, 'youcom-search', '联网搜索查询', 1, 'youcom',
    '实时联网搜索，如：最新新闻、行业动态、公开资讯、事件进展、产品发布、股价消息等需要联网获取的信息',
    '["今天有什么AI新闻？","帮我搜一下2026年量子计算最新进展","OpenAI最近发布了什么","特斯拉最新股价消息","这周科技圈有什么大事件","搜索一下Claude最新版本特性"]',
    NULL, NULL, 'youcom_search', 2, NULL, '',
    NULL, 20, 1, 'admin', 'admin',
    NOW(), NOW(), 0
);
