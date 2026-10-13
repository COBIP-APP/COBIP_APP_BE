ALTER TABLE templates ADD COLUMN deleted_at timestamptz;
ALTER TABLE template_sections ADD COLUMN deleted_at timestamptz;

ALTER TABLE templates DROP CONSTRAINT templates_display_order_uq;
CREATE UNIQUE INDEX templates_active_display_order_uq
    ON templates (category_id, language_id, display_order) NULLS NOT DISTINCT
    WHERE deleted_at IS NULL;

ALTER TABLE template_sections DROP CONSTRAINT template_sections_order_uq;
CREATE UNIQUE INDEX template_sections_active_order_uq
    ON template_sections (template_id, parent_section_id, display_order) NULLS NOT DISTINCT
    WHERE deleted_at IS NULL;

DROP INDEX questions_section_order_uq;
CREATE UNIQUE INDEX questions_active_section_order_uq
    ON questions (related_section_id, display_order)
    WHERE related_section_id IS NOT NULL AND deleted_at IS NULL;
