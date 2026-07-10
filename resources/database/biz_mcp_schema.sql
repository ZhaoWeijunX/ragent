-- MCP 业务查询表（销售订单、客户工单）
-- 供 mcp-server 的 sales_query / ticket_query 工具实时查询

CREATE TABLE IF NOT EXISTS t_biz_sales_order (
    id            VARCHAR(32)    NOT NULL PRIMARY KEY,
    region        VARCHAR(16)    NOT NULL,
    sales_person  VARCHAR(64)    NOT NULL,
    product       VARCHAR(32)    NOT NULL,
    customer      VARCHAR(128)   NOT NULL,
    amount        NUMERIC(12, 2) NOT NULL,
    order_date    DATE           NOT NULL,
    create_time   TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT       DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_biz_sales_region_date ON t_biz_sales_order (region, order_date);
CREATE INDEX IF NOT EXISTS idx_biz_sales_person ON t_biz_sales_order (sales_person);
CREATE INDEX IF NOT EXISTS idx_biz_sales_product ON t_biz_sales_order (product);
COMMENT ON TABLE t_biz_sales_order IS 'MCP 销售订单模拟数据';

CREATE TABLE IF NOT EXISTS t_biz_support_ticket (
    id            VARCHAR(32)    NOT NULL PRIMARY KEY,
    ticket_no     VARCHAR(32)    NOT NULL,
    region        VARCHAR(16)    NOT NULL,
    customer      VARCHAR(128)   NOT NULL,
    product       VARCHAR(32)    NOT NULL,
    status        VARCHAR(16)    NOT NULL,
    priority      VARCHAR(8)     NOT NULL,
    category      VARCHAR(32)    NOT NULL,
    engineer      VARCHAR(64)    NOT NULL,
    title         VARCHAR(256)   NOT NULL,
    created_date  DATE           NOT NULL,
    resolved_date DATE,
    create_time   TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT       DEFAULT 0,
    CONSTRAINT uk_biz_ticket_no UNIQUE (ticket_no)
);
CREATE INDEX IF NOT EXISTS idx_biz_ticket_region_status ON t_biz_support_ticket (region, status);
CREATE INDEX IF NOT EXISTS idx_biz_ticket_priority ON t_biz_support_ticket (priority);
CREATE INDEX IF NOT EXISTS idx_biz_ticket_customer ON t_biz_support_ticket (customer);
CREATE INDEX IF NOT EXISTS idx_biz_ticket_created ON t_biz_support_ticket (created_date);
COMMENT ON TABLE t_biz_support_ticket IS 'MCP 客户工单模拟数据';
