-- ragent v1.2 -> v1.3 升级脚本
-- 飞书 Wiki 批量导入任务表 + 文档飞书节点去重字段
-- 2026.6.30

ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS feishu_node_token VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_feishu_node ON t_knowledge_document (kb_id, feishu_node_token)
    WHERE feishu_node_token IS NOT NULL AND deleted = 0;

CREATE TABLE IF NOT EXISTS t_feishu_wiki_import_job (
    id               VARCHAR(20)   NOT NULL PRIMARY KEY,
    kb_id            VARCHAR(20)   NOT NULL,
    root_url         VARCHAR(1024) NOT NULL,
    scope            VARCHAR(32)   NOT NULL,
    space_id         VARCHAR(64),
    status           VARCHAR(16)   NOT NULL DEFAULT 'pending',
    total_count      INTEGER       NOT NULL DEFAULT 0,
    success_count    INTEGER       NOT NULL DEFAULT 0,
    failed_count     INTEGER       NOT NULL DEFAULT 0,
    skipped_count    INTEGER       NOT NULL DEFAULT 0,
    auto_chunk       SMALLINT      NOT NULL DEFAULT 0,
    process_mode     VARCHAR(16),
    chunk_strategy   VARCHAR(32),
    chunk_config     JSONB,
    pipeline_id      VARCHAR(20),
    schedule_enabled SMALLINT,
    schedule_cron    VARCHAR(64),
    error_message    TEXT,
    created_by       VARCHAR(20)   NOT NULL,
    create_time      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_feishu_wiki_job_kb ON t_feishu_wiki_import_job (kb_id);

CREATE TABLE IF NOT EXISTS t_feishu_wiki_import_item (
    id            VARCHAR(20)   NOT NULL PRIMARY KEY,
    job_id        VARCHAR(20)   NOT NULL,
    node_token    VARCHAR(64)   NOT NULL,
    wiki_url      VARCHAR(1024) NOT NULL,
    title         VARCHAR(256),
    status        VARCHAR(16)   NOT NULL DEFAULT 'pending',
    doc_id        VARCHAR(20),
    error_message TEXT,
    sort_order    INTEGER       NOT NULL DEFAULT 0,
    create_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_feishu_wiki_item_job ON t_feishu_wiki_import_item (job_id);


-- t_knowledge_vector 表：新增知识库 Collection 字段

ALTER TABLE t_knowledge_vector
    ADD COLUMN collection_name VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX idx_kv_collection_name
    ON t_knowledge_vector (collection_name);

COMMENT ON COLUMN t_knowledge_vector.collection_name IS '知识库Collection';