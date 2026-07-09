-- MCP 意图节点导入脚本
--
-- 说明：
-- 1. sales-data / ticket-data 的 param_prompt_template 留空时，bootstrap 使用全局
--    bootstrap/src/main/resources/prompt/mcp-parameter-extract.st
-- 2. weather-data 导入后请执行 mcp-intent-nodes-weather-prompt-update.sql，
--    或从 docs/examples/prompt/weather-mcp-parameter-extract.st 复制到管理后台

INSERT INTO t_intent_node (
    id, kb_id, intent_code, name, level, parent_code, description, examples,
    collection_name, top_k, mcp_tool_id, kind, prompt_snippet, prompt_template,
    param_prompt_template, sort_order, enabled, create_by, update_by,
    create_time, update_time, deleted
) VALUES
(
    1998603043843346433, NULL, 'sales', '销售汇总数据统计', 0, NULL, NULL, '[]',
    NULL, NULL, NULL, 2, NULL, NULL, NULL, 13, 1, 'admin', 'admin',
    NOW(), NOW(), 0
),
(
    1998603043843346434, NULL, 'ticket', '客户工单服务管理', 0, NULL, NULL, '[]',
    NULL, NULL, NULL, 2, NULL, NULL, NULL, 15, 1, 'admin', 'admin',
    NOW(), NOW(), 0
),
(
    1998603043843346435, NULL, 'weather', '天气信息查询服务', 0, NULL, NULL, '[]',
    NULL, NULL, NULL, 2, NULL, NULL, NULL, 17, 1, 'admin', 'admin',
    NOW(), NOW(), 0
),
(
    1998603043868512258, NULL, 'sales-data', '销售数据统计', 1, 'sales',
    '销售数据统计，如：销售总额、销售量、销售占比、销售趋势、销售预测等',
    '["销售总额是多少？","销售量是多少？","今年的销售业绩","某位员工的销售业绩如何？","华东销售额是多少？","华南销售额是多少？"]',
    NULL, NULL, 'sales_query', 2, NULL, '',
    NULL, 14, 1, 'admin', 'admin',
    NOW(), NOW(), 0
),
(
    1998603043868512259, NULL, 'ticket-data', '客户工单查询', 1, 'ticket',
    '客户技术支持工单查询，如：工单状态、工单数量、解决率、紧急工单、处理进度等',
    '["华东区有多少待处理工单？","紧急工单有哪些？","本月工单解决率是多少？","腾讯科技的工单进展如何？","企业版产品有多少未关闭工单？","各地区工单数量统计"]',
    NULL, NULL, 'ticket_query', 2, NULL, '',
    NULL, 16, 1, 'admin', 'admin',
    NOW(), NOW(), 0
),
(
    1998603043868512260, NULL, 'weather-data', '天气查询', 1, 'weather',
    '城市天气信息查询，如：当前天气、天气预报、温度、湿度、风力、空气质量等',
    '["北京今天天气怎么样？","上海明天会下雨吗？","广州未来三天天气预报","杭州现在多少度？","成都这周天气如何？","深圳空气质量怎么样？"]',
    NULL, NULL, 'weather_query', 2, NULL, '',
    NULL, 18, 1, 'admin', 'admin',
    NOW(), NOW(), 0
),
(
    1998603043906260994, NULL, 'sys', '系统交互', 0, NULL, NULL, '[]',
    NULL, NULL, NULL, 1, NULL, NULL, NULL, 15, 1, 'admin', 'admin',
    NOW(), NOW(), 0
),
(
    1998603043935621121, NULL, 'sys-welcome', '欢迎与问候', 1, 'sys',
    '用户与助手打招呼，如：你好、早上好、hi、在吗 等',
    '["你好","hello","早上好","在吗","嗨"]',
    NULL, NULL, NULL, 1, NULL, NULL, NULL, 16, 1, 'admin', 'admin',
    NOW(), NOW(), 0
),
(
    1998603043960786946, NULL, 'sys-about-bot', '关于助手', 1, 'sys',
    '询问助手是做什么的、是谁、能做什么等',
    '["你是谁","你是做什么的","你能帮我做什么","你是什么AI"]',
    NULL, NULL, NULL, 1, NULL, NULL, NULL, 17, 1, 'admin', 'admin',
    NOW(), NOW(), 0
),
(
    1998603043960786947, NULL, 'sys-feedback', '情感反馈', 1, 'sys',
    '用户对助手回答的情感反馈，包括表扬、感谢、质疑、纠正、不满等情绪表达',
    '["真棒","好样的","太厉害了","说得好","你说的不对","不太准确","回答得不错","谢谢你","辛苦了","答非所问","很有帮助","太棒了","回答的一般"]',
    '', NULL, NULL, 1, NULL,
    '你是企业内部知识助手「小码」。用户刚才对你的回答给出了情感反馈（如表扬、感谢、质疑、纠正等）。

请根据对话上下文，判断用户的情绪倾向，并做出自然、简短、有温度的回应：

- 正向反馈（表扬、感谢）：真诚回应，表示乐意帮忙
- 负向反馈（质疑、纠正、不满）：先表示歉意，主动询问哪里不准确，表达愿意重新回答的态度
- 中性反馈（感叹、随意评价）：自然回应，保持友好

要求：
1. 只回应用户的情绪，1-2句话即可，不超过100个字
2. 严禁复述、总结、重新整理之前已回答过的任何内容
3. 不要自我介绍，不要列举你能做什么
4. 不要主动引导用户提问',
    NULL, 18, 1, 'admin', 'admin',
    NOW(), NOW(), 0
);
