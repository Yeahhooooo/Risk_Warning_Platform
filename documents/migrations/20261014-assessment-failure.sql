-- Run against the application database before deploying the updated services.
-- Enum additions must commit before using the new value.
ALTER TYPE public.assessment_status_enum ADD VALUE IF NOT EXISTS '上传中';
BEGIN;
ALTER TABLE public.t_assessment_result
    ADD COLUMN IF NOT EXISTS failure_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS failure_reason TEXT,
    ADD COLUMN IF NOT EXISTS failure_stage VARCHAR(64),
    ADD COLUMN IF NOT EXISTS failed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS upload_task_id VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS assessment_upload_task_unique
    ON public.t_assessment_result(upload_task_id);
COMMENT ON COLUMN public.t_assessment_result.failure_reason IS '可向用户展示的失败摘要，不包含原始异常堆栈';
COMMENT ON COLUMN public.t_assessment_result.upload_task_id IS '上传批次幂等标识，初始化上传时创建评估';
COMMIT;
