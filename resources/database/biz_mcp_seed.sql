-- MCP 业务表模拟数据（可重复执行）
-- 执行前请先运行 biz_mcp_schema.sql

DELETE FROM t_biz_support_ticket;
DELETE FROM t_biz_sales_order;

-- 销售订单：约 300 条，日期覆盖上月～本月工作日
INSERT INTO t_biz_sales_order (id, region, sales_person, product, customer, amount, order_date)
SELECT
    'so_' || LPAD(gs::TEXT, 6, '0'),
    (ARRAY['华东', '华南', '华北', '西南', '西北'])[1 + (gs % 5)],
    (ARRAY[
        '张三', '李四', '王五', '赵六', '钱七', '孙八',
        '周九', '吴十', '郑冬', '陈春', '林夏', '黄秋', '刘一', '杨二', '马三'
    ])[1 + (gs % 15)],
    (ARRAY['企业版', '专业版', '基础版'])[1 + (gs % 3)],
    (ARRAY[
        '腾讯科技', '阿里巴巴', '字节跳动', '美团点评', '京东集团',
        '百度在线', '网易公司', '小米科技', '华为技术', '中兴通讯',
        '用友网络', '金蝶软件', '浪潮集团', '东软集团', '科大讯飞'
    ])[1 + (gs % 15)] || (1 + (gs % 28))::TEXT,
    ROUND((
        CASE (ARRAY['企业版', '专业版', '基础版'])[1 + (gs % 3)]
            WHEN '企业版' THEN 50 + (gs % 150)
            WHEN '专业版' THEN 10 + (gs % 40)
            ELSE 1 + (gs % 9)
        END
    )::NUMERIC, 2),
    d.order_date
FROM generate_series(1, 300) AS gs
CROSS JOIN LATERAL (
    SELECT (
        CURRENT_DATE
        - ((gs % 62) || ' days')::INTERVAL
        - CASE WHEN EXTRACT(DOW FROM CURRENT_DATE - ((gs % 62) || ' days')::INTERVAL) IN (0, 6)
               THEN INTERVAL '2 days' ELSE INTERVAL '0 days' END
    )::DATE AS order_date
) AS d
WHERE d.order_date >= DATE_TRUNC('year', CURRENT_DATE)::DATE;

-- 客户工单：约 100 条，近 30 个工作日
INSERT INTO t_biz_support_ticket (
    id, ticket_no, region, customer, product, status, priority, category, engineer, title, created_date, resolved_date
)
SELECT
    'tk_' || LPAD(gs::TEXT, 6, '0'),
    'TK-' || TO_CHAR(d.created_date, 'YYYYMM') || '-' || LPAD(gs::TEXT, 4, '0'),
    (ARRAY['华东', '华南', '华北', '西南', '西北'])[1 + (gs % 5)],
    (ARRAY[
        '腾讯科技', '阿里巴巴', '字节跳动', '网易公司',
        '美团点评', '京东集团', '小米科技', '格力电器',
        '百度在线', '华为技术', '中兴通讯', '用友网络',
        '科大讯飞', '金蝶软件', '三一重工', '中联重科'
    ])[1 + (gs % 16)],
    (ARRAY['企业版', '专业版', '基础版'])[1 + (gs % 3)],
    (ARRAY['待处理', '处理中', '已解决', '已关闭'])[1 + (gs % 4)],
    (ARRAY['紧急', '高', '中', '低'])[1 + (gs % 4)],
    (ARRAY['功能异常', '性能问题', '安装部署', '使用咨询', '数据问题', '权限问题'])[1 + (gs % 6)],
    (ARRAY[
        '工程师A1', '工程师A2', '工程师B1', '工程师B2',
        '工程师C1', '工程师C2', '工程师D1', '工程师D2', '工程师E1', '工程师E2'
    ])[1 + (gs % 10)],
    (ARRAY[
        '系统登录后页面白屏无法操作',
        '报表导出功能超时失败',
        '用户权限配置不生效',
        '数据同步延迟超过预期',
        '批量导入数据格式校验异常',
        'API接口调用返回500错误',
        '定时任务未按计划执行',
        '搜索功能结果不准确',
        '通知消息无法正常推送',
        '文件上传大小限制配置无效'
    ])[1 + (gs % 10)],
    d.created_date,
    CASE
        WHEN (ARRAY['待处理', '处理中', '已解决', '已关闭'])[1 + (gs % 4)] IN ('已解决', '已关闭')
        THEN d.created_date + ((gs % 5) + 1)
        ELSE NULL
    END
FROM generate_series(1, 100) AS gs
CROSS JOIN LATERAL (
    SELECT (
        CURRENT_DATE
        - ((gs % 30) || ' days')::INTERVAL
        - CASE WHEN EXTRACT(DOW FROM CURRENT_DATE - ((gs % 30) || ' days')::INTERVAL) IN (0, 6)
               THEN INTERVAL '1 day' ELSE INTERVAL '0 days' END
    )::DATE AS created_date
) AS d;
