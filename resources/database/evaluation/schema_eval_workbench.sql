-- =============================================================================
-- RAG 评测工作台表结构（权威脚本）
-- 目录：resources/database/evaluation/
-- 适用：新环境可单独执行；已有环境也可执行（IF NOT EXISTS）
-- 对齐：docs/rag-evaluation-workbench-requirements.md §16、docs/evaluation/phase-0-adr.md
-- schemaVersion: 1.0.0
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 评估集
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_eval_dataset (
    id           VARCHAR(20)  NOT NULL PRIMARY KEY,
    name         VARCHAR(128) NOT NULL,
    description  TEXT,
    domain       VARCHAR(64),
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_by   VARCHAR(20),
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT     NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_eval_dataset_status
    ON t_eval_dataset (status) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_eval_dataset_update_time
    ON t_eval_dataset (update_time DESC) WHERE deleted = 0;

COMMENT ON TABLE t_eval_dataset IS '评测评估集元数据';
COMMENT ON COLUMN t_eval_dataset.status IS 'ACTIVE / ARCHIVED（展示用；Run 引用校验看版本状态）';

-- -----------------------------------------------------------------------------
-- 2. 评估集版本（不可变发布快照）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_eval_dataset_version (
    id            VARCHAR(20)  NOT NULL PRIMARY KEY,
    dataset_id    VARCHAR(20)  NOT NULL,
    version       VARCHAR(32)  NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    sample_count  INTEGER      NOT NULL DEFAULT 0,
    content_hash  VARCHAR(128),
    published_by  VARCHAR(20),
    published_at  TIMESTAMP,
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT     NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_eval_dataset_version
    ON t_eval_dataset_version (dataset_id, version) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_eval_dataset_version_dataset
    ON t_eval_dataset_version (dataset_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_eval_dataset_version_status
    ON t_eval_dataset_version (status) WHERE deleted = 0;

COMMENT ON TABLE t_eval_dataset_version IS '评估集版本；DRAFT/PUBLISHED/ARCHIVED';
COMMENT ON COLUMN t_eval_dataset_version.status IS 'DRAFT可改；PUBLISHED不可变且可被Run引用；ARCHIVED不可新建Run';

-- -----------------------------------------------------------------------------
-- 3. 评估样本 Case
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_eval_case (
    id                   VARCHAR(20)  NOT NULL PRIMARY KEY,
    dataset_version_id   VARCHAR(20)  NOT NULL,
    query_id             VARCHAR(64)  NOT NULL,
    query                TEXT         NOT NULL,
    intent_l1            VARCHAR(64),
    intent_l2            VARCHAR(128),
    difficulty           VARCHAR(16)  DEFAULT 'medium',
    requires_rag         BOOLEAN      NOT NULL DEFAULT FALSE,
    expected_answer_type VARCHAR(64),
    expected_doc_ids     JSONB        NOT NULL DEFAULT '[]'::jsonb,
    nice_to_have_doc_ids JSONB        NOT NULL DEFAULT '[]'::jsonb,
    ground_truth         TEXT,
    trap_type            VARCHAR(64),
    enabled_metrics      JSONB        NOT NULL DEFAULT '[]'::jsonb,
    tags                 JSONB        NOT NULL DEFAULT '[]'::jsonb,
    metadata             JSONB        NOT NULL DEFAULT '{}'::jsonb,
    create_time          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              SMALLINT     NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_eval_case_query
    ON t_eval_case (dataset_version_id, query_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_eval_case_version
    ON t_eval_case (dataset_version_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_eval_case_intent_l2
    ON t_eval_case (intent_l2) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_eval_case_difficulty
    ON t_eval_case (difficulty) WHERE deleted = 0;

COMMENT ON TABLE t_eval_case IS '评估集版本内样本';
COMMENT ON COLUMN t_eval_case.expected_doc_ids IS 'must 文档业务码数组（doc_name去后缀）';
COMMENT ON COLUMN t_eval_case.nice_to_have_doc_ids IS 'nice 文档业务码；对齐 expected_doc_ids_nice';

-- -----------------------------------------------------------------------------
-- 4. 评测运行 Run
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_eval_run (
    id                   VARCHAR(20)  NOT NULL PRIMARY KEY,
    name                 VARCHAR(128) NOT NULL,
    dataset_version_id   VARCHAR(20)  NOT NULL,
    baseline_run_id      VARCHAR(20),
    status               VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    current_phase        VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    quality_verdict      VARCHAR(32)  NOT NULL DEFAULT 'NOT_EVALUATED',
    cancel_requested     SMALLINT     NOT NULL DEFAULT 0,
    config_snapshot      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    threshold_snapshot   JSONB        NOT NULL DEFAULT '{}'::jsonb,
    tags                 JSONB        NOT NULL DEFAULT '{}'::jsonb,
    total_count          INTEGER      NOT NULL DEFAULT 0,
    success_count        INTEGER      NOT NULL DEFAULT 0,
    failed_count         INTEGER      NOT NULL DEFAULT 0,
    progress             INTEGER      NOT NULL DEFAULT 0,
    ragas_enabled        BOOLEAN      NOT NULL DEFAULT FALSE,
    lease_owner          VARCHAR(128),
    lease_expire_at      TIMESTAMP,
    created_by           VARCHAR(20),
    started_at           TIMESTAMP,
    finished_at          TIMESTAMP,
    error_message        TEXT,
    create_time          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              SMALLINT     NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_eval_run_version
    ON t_eval_run (dataset_version_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_eval_run_status
    ON t_eval_run (status) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_eval_run_lease
    ON t_eval_run (lease_expire_at) WHERE deleted = 0 AND status NOT IN ('COMPLETED', 'PARTIAL_SUCCESS', 'FAILED', 'CANCELLED');
CREATE INDEX IF NOT EXISTS idx_eval_run_created
    ON t_eval_run (create_time DESC) WHERE deleted = 0;

COMMENT ON TABLE t_eval_run IS '评测运行；status与current_phase分离；终态互斥';
COMMENT ON COLUMN t_eval_run.status IS 'PENDING/RECORDING/DETERMINISTIC_SCORING/RAGAS_SCORING/REPORTING/COMPLETED/PARTIAL_SUCCESS/FAILED/CANCELLED';
COMMENT ON COLUMN t_eval_run.current_phase IS '当前执行阶段';
COMMENT ON COLUMN t_eval_run.quality_verdict IS 'PASS/FAIL/WARN/NOT_EVALUATED，与执行状态独立';
COMMENT ON COLUMN t_eval_run.tags IS 'gitBranch/gitCommit/environment/appVersion 等';
COMMENT ON COLUMN t_eval_run.lease_owner IS 'Runner租约持有者，用于崩溃恢复';
COMMENT ON COLUMN t_eval_run.lease_expire_at IS '租约过期时间';

-- -----------------------------------------------------------------------------
-- 5. 录制 Record（不可变业务语义；失败可幂等重写）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_eval_record (
    id                        VARCHAR(20)  NOT NULL PRIMARY KEY,
    run_id                    VARCHAR(20)  NOT NULL,
    case_id                   VARCHAR(20)  NOT NULL,
    status                    VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    question                  TEXT         NOT NULL,
    response                  TEXT,
    thinking                  TEXT,
    retrieved_doc_ids         JSONB        NOT NULL DEFAULT '[]'::jsonb,
    retrieved_chunk_ids       JSONB        NOT NULL DEFAULT '[]'::jsonb,
    retrieved_contexts        JSONB,
    retrieved_context_doc_ids JSONB        NOT NULL DEFAULT '[]'::jsonb,
    predicted_intents         JSONB        NOT NULL DEFAULT '[]'::jsonb,
    intent_pred               VARCHAR(128),
    has_kb                    BOOLEAN,
    has_mcp                   BOOLEAN,
    retrieval_skipped         BOOLEAN      NOT NULL DEFAULT FALSE,
    skip_reason               VARCHAR(64),
    ttft_ms                   BIGINT,
    total_latency_ms          BIGINT,
    eval_latency_ms           BIGINT,
    conversation_id           VARCHAR(64),
    task_id                   VARCHAR(64),
    trace_id                  VARCHAR(64),
    evidence_source           VARCHAR(64)  NOT NULL DEFAULT 'DUAL_PATH_CHAT_AND_EVAL',
    error_code                VARCHAR(64),
    error_message             TEXT,
    raw_payload               JSONB,
    started_at                TIMESTAMP,
    finished_at               TIMESTAMP,
    create_time               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                   SMALLINT     NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_eval_record_run_case
    ON t_eval_record (run_id, case_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_eval_record_run_status
    ON t_eval_record (run_id, status) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_eval_record_trace
    ON t_eval_record (trace_id) WHERE deleted = 0 AND trace_id IS NOT NULL;

COMMENT ON TABLE t_eval_record IS '单样本录制结果；MVP evidence_source=DUAL_PATH_CHAT_AND_EVAL';
COMMENT ON COLUMN t_eval_record.thinking IS 'MVP默认不落库，保持NULL';
COMMENT ON COLUMN t_eval_record.eval_latency_ms IS '旁路/rag/eval耗时，不等于TTFT';
COMMENT ON COLUMN t_eval_record.status IS 'success/refused/error/cancelled/unknown 等与EvalRecord.finalStatus对齐';

-- -----------------------------------------------------------------------------
-- 6. 评分批次
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_eval_score_batch (
    id                     VARCHAR(20)     NOT NULL PRIMARY KEY,
    run_id                 VARCHAR(20)     NOT NULL,
    score_type             VARCHAR(32)     NOT NULL,
    status                 VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    algorithm_version      VARCHAR(64)     NOT NULL,
    judge_config_snapshot  JSONB           NOT NULL DEFAULT '{}'::jsonb,
    threshold_snapshot     JSONB           NOT NULL DEFAULT '{}'::jsonb,
    sample_count           INTEGER         NOT NULL DEFAULT 0,
    token_usage            JSONB           NOT NULL DEFAULT '{}'::jsonb,
    estimated_cost         NUMERIC(12, 6),
    external_job_id        VARCHAR(128),
    started_at             TIMESTAMP,
    finished_at            TIMESTAMP,
    error_message          TEXT,
    create_time            TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time            TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                SMALLINT        NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_eval_score_batch_run
    ON t_eval_score_batch (run_id, score_type) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_eval_score_batch_status
    ON t_eval_score_batch (status) WHERE deleted = 0;

COMMENT ON TABLE t_eval_score_batch IS '评分批次；DETERMINISTIC / RAGAS；重评分新建批次不覆盖';
COMMENT ON COLUMN t_eval_score_batch.score_type IS 'DETERMINISTIC / RAGAS';
COMMENT ON COLUMN t_eval_score_batch.external_job_id IS 'RAGAS服务job_id';

-- -----------------------------------------------------------------------------
-- 7. 指标分数
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_eval_score (
    id               VARCHAR(20)     NOT NULL PRIMARY KEY,
    score_batch_id   VARCHAR(20)     NOT NULL,
    run_id           VARCHAR(20)     NOT NULL,
    record_id        VARCHAR(20),
    metric_name      VARCHAR(64)     NOT NULL,
    dimension_type   VARCHAR(32)     NOT NULL,
    dimension_value  VARCHAR(128),
    score_value      NUMERIC(12, 6),
    sample_count     INTEGER,
    detail           JSONB           NOT NULL DEFAULT '{}'::jsonb,
    create_time      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          SMALLINT        NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_eval_score_batch_metric
    ON t_eval_score (score_batch_id, metric_name) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_eval_score_run_dim
    ON t_eval_score (run_id, dimension_type, dimension_value) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_eval_score_record
    ON t_eval_score (record_id) WHERE deleted = 0 AND record_id IS NOT NULL;

COMMENT ON TABLE t_eval_score IS '聚合或样本级指标；dimension_type=OVERALL/INTENT_L1/INTENT_L2/DIFFICULTY/SAMPLE';
COMMENT ON COLUMN t_eval_score.record_id IS '聚合指标为空；SAMPLE维度指向t_eval_record.id';
