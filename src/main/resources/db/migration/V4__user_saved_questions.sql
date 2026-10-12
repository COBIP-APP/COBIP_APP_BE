ALTER TABLE questions
    ADD COLUMN client_request_id uuid;

ALTER TABLE questions
    DROP CONSTRAINT questions_question_type_check;

ALTER TABLE questions
    ADD CONSTRAINT questions_question_type_check CHECK (
        question_type IN (
            'MULTIPLE_CHOICE', 'OX', 'SHORT_ANSWER', 'FILL_BLANK', 'DESCRIPTIVE',
            'CODE_EXPLANATION', 'CODE_WRITING'
        )
    );

CREATE UNIQUE INDEX questions_owner_request_uq
    ON questions (created_by, client_request_id)
    WHERE client_request_id IS NOT NULL;
