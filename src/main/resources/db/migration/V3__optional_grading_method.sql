ALTER TABLE submissions
    DROP CONSTRAINT submissions_completed_has_result;

ALTER TABLE submissions
    ADD CONSTRAINT submissions_completed_has_result CHECK (
        grading_status <> 'COMPLETED'
        OR (score IS NOT NULL AND graded_at IS NOT NULL)
    );
