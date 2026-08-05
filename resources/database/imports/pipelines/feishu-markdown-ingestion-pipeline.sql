-- 飞书 Markdown 摄取流水线（content-format=markdown）
-- 飞书接入与使用说明见 docs/integrations/feishu/wiki.md
-- 节点链: fetcher → parser(MARKDOWN/TEXT) → chunker → indexer
--
-- 执行示例:
--   psql -f resources/database/imports/pipelines/feishu-markdown-ingestion-pipeline.sql

-- 可重复执行：先删节点再删流水线，再插入
DELETE FROM t_ingestion_pipeline_node WHERE pipeline_id = '2080100000000000010';
DELETE FROM t_ingestion_pipeline WHERE id = '2080100000000000010';

INSERT INTO t_ingestion_pipeline (
    id, name, description, created_by, updated_by, create_time, update_time, deleted
) VALUES (
    '2080100000000000010', 'feishu-markdown-ingestion-pipeline', '飞书云文档摄取流水线（Markdown 格式）- Markdown 解析、结构感知分块、向量化',
    'admin', 'admin', NOW(), NOW(), 0
);

INSERT INTO t_ingestion_pipeline_node (
    id, pipeline_id, node_id, node_type, next_node_id,
    settings_json, condition_json, created_by, updated_by,
    create_time, update_time, deleted
) VALUES
(
    '2080100000000000011', '2080100000000000010', 'feishu_fetcher-1', 'fetcher', 'feishu_parser-1',
    NULL, NULL, 'admin', 'admin',
    NOW(), NOW(), 0
),
(
    '2080100000000000012', '2080100000000000010', 'feishu_parser-1', 'parser', 'feishu_chunker-1',
    $json${"rules": [{"mimeType": "MARKDOWN"}, {"mimeType": "TEXT"}]}$json$::jsonb, NULL, 'admin', 'admin',
    NOW(), NOW(), 0
),
(
    '2080100000000000013', '2080100000000000010', 'feishu_chunker-1', 'chunker', 'feishu_indexer-1',
    $json${"strategy": "structure_aware", "chunkSize": 1400, "overlapSize": 0}$json$::jsonb, NULL, 'admin', 'admin',
    NOW(), NOW(), 0
),
(
    '2080100000000000014', '2080100000000000010', 'feishu_indexer-1', 'indexer', NULL,
    $json${"embeddingModel": "qwen-emb-8b", "metadataFields": ["source_type", "source_location"]}$json$::jsonb, NULL, 'admin', 'admin',
    NOW(), NOW(), 0
);
