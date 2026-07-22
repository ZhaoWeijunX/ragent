-- ragent v1.5 -> v1.6 升级脚本
-- 会话消息新增「推荐追问问题」与「推荐 grounding 片段」列（懒加载生成、按需落库，不参与模型上下文）

ALTER TABLE t_message ADD COLUMN IF NOT EXISTS recommended_questions JSONB;
ALTER TABLE t_message ADD COLUMN IF NOT EXISTS retrieved_chunks JSONB;

COMMENT ON COLUMN t_message.recommended_questions IS '推荐追问问题';
COMMENT ON COLUMN t_message.retrieved_chunks IS '推荐问题 grounding 片段';
