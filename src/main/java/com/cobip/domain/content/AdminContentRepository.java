package com.cobip.domain.content;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AdminContentRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    AdminContentRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    Long createTemplate(TemplateSaveRequest request, Long adminId) {
        String sql = """
                INSERT INTO templates (category_id, language_id, created_by, title, summary,
                                       description, difficulty, display_order)
                VALUES (:categoryId, :languageId, :adminId, :title, :summary,
                        :description, :difficulty, :displayOrder)
                RETURNING template_id
                """;
        return jdbc.queryForObject(sql, templateParams(request).addValue("adminId", adminId), Long.class);
    }

    boolean updateTemplate(Long id, TemplateSaveRequest request) {
        String sql = """
                UPDATE templates SET category_id = :categoryId, language_id = :languageId,
                    title = :title, summary = :summary, description = :description,
                    difficulty = :difficulty, display_order = :displayOrder, updated_at = now()
                WHERE template_id = :id
                """;
        return jdbc.update(sql, templateParams(request).addValue("id", id)) > 0;
    }

    Long createSection(Long templateId, SectionSaveRequest request) {
        String sql = """
                INSERT INTO template_sections (template_id, parent_section_id, title, body,
                                               example_code, display_order)
                VALUES (:templateId, :parentSectionId, :title, :body, :exampleCode, :displayOrder)
                RETURNING section_id
                """;
        return jdbc.queryForObject(sql, sectionParams(request).addValue("templateId", templateId), Long.class);
    }

    boolean updateSection(Long templateId, Long sectionId, SectionUpdateRequest request) {
        String sql = """
                UPDATE template_sections SET title = :title, body = :body, example_code = :exampleCode,
                    display_order = :displayOrder
                WHERE template_id = :templateId AND section_id = :sectionId
                """;
        return jdbc.update(sql, new MapSqlParameterSource("title", request.title())
                .addValue("body", request.body()).addValue("exampleCode", request.exampleCode())
                .addValue("displayOrder", request.displayOrder())
                .addValue("templateId", templateId).addValue("sectionId", sectionId)) > 0;
    }

    Long createQuestion(QuestionSaveRequest request, Long adminId) {
        String sql = """
                INSERT INTO questions (category_id, related_template_id, related_section_id,
                                       created_by, language_id, question_type, prompt, code_snippet,
                                       choice_options, correct_choice_key, reference_answer,
                                       grading_criteria, explanation, difficulty, pass_score, display_order)
                VALUES (:categoryId, :relatedTemplateId, :relatedSectionId,
                        :adminId, :languageId, :questionType, :prompt, :codeSnippet,
                        CAST(:choiceOptions AS jsonb), :correctChoiceKey, :referenceAnswer,
                        CAST(:gradingCriteria AS jsonb), :explanation, :difficulty, :passScore, :displayOrder)
                RETURNING question_id
                """;
        return jdbc.queryForObject(sql, questionParams(request).addValue("adminId", adminId), Long.class);
    }

    boolean updateQuestion(Long id, QuestionSaveRequest request) {
        String sql = """
                UPDATE questions SET category_id = :categoryId,
                    related_template_id = :relatedTemplateId, related_section_id = :relatedSectionId,
                    language_id = :languageId, question_type = :questionType, prompt = :prompt,
                    code_snippet = :codeSnippet, choice_options = CAST(:choiceOptions AS jsonb),
                    correct_choice_key = :correctChoiceKey, reference_answer = :referenceAnswer,
                    grading_criteria = CAST(:gradingCriteria AS jsonb), explanation = :explanation,
                    difficulty = :difficulty, pass_score = :passScore,
                    display_order = :displayOrder, updated_at = now()
                WHERE question_id = :id
                """;
        return jdbc.update(sql, questionParams(request).addValue("id", id)) > 0;
    }

    Optional<AdminQuestionResponse> findQuestion(Long id) {
        String sql = """
                SELECT question_id, category_id, related_template_id, related_section_id,
                       language_id, question_type, prompt, code_snippet, choice_options,
                       correct_choice_key, reference_answer, grading_criteria, explanation,
                       difficulty, pass_score, display_order, is_published, created_at, updated_at
                FROM questions WHERE question_id = :id
                """;
        return jdbc.query(sql, new MapSqlParameterSource("id", id),
                (rs, rowNum) -> mapQuestion(rs)).stream().findFirst();
    }

    boolean setTemplatePublished(Long id, boolean published) {
        return jdbc.update("UPDATE templates SET is_published = :published, updated_at = now() WHERE template_id = :id",
                new MapSqlParameterSource("id", id).addValue("published", published)) > 0;
    }

    boolean setQuestionPublished(Long id, boolean published) {
        return jdbc.update("UPDATE questions SET is_published = :published, updated_at = now() WHERE question_id = :id",
                new MapSqlParameterSource("id", id).addValue("published", published)) > 0;
    }

    private MapSqlParameterSource templateParams(TemplateSaveRequest request) {
        return new MapSqlParameterSource()
                .addValue("categoryId", request.categoryId()).addValue("languageId", request.languageId())
                .addValue("title", request.title()).addValue("summary", request.summary())
                .addValue("description", request.description()).addValue("difficulty", request.difficulty())
                .addValue("displayOrder", request.displayOrder());
    }

    private MapSqlParameterSource sectionParams(SectionSaveRequest request) {
        return new MapSqlParameterSource()
                .addValue("parentSectionId", request.parentSectionId())
                .addValue("title", request.title()).addValue("body", request.body())
                .addValue("exampleCode", request.exampleCode()).addValue("displayOrder", request.displayOrder());
    }

    private MapSqlParameterSource questionParams(QuestionSaveRequest request) {
        return new MapSqlParameterSource()
                .addValue("categoryId", request.categoryId())
                .addValue("relatedTemplateId", request.relatedTemplateId())
                .addValue("relatedSectionId", request.relatedSectionId())
                .addValue("languageId", request.languageId())
                .addValue("questionType", request.questionType()).addValue("prompt", request.prompt())
                .addValue("codeSnippet", request.codeSnippet())
                .addValue("choiceOptions", request.choiceOptions() == null ? null : request.choiceOptions().toString())
                .addValue("correctChoiceKey", request.correctChoiceKey())
                .addValue("referenceAnswer", request.referenceAnswer())
                .addValue("gradingCriteria", request.gradingCriteria() == null ? null : request.gradingCriteria().toString())
                .addValue("explanation", request.explanation()).addValue("difficulty", request.difficulty())
                .addValue("passScore", request.passScore()).addValue("displayOrder", request.displayOrder());
    }

    private AdminQuestionResponse mapQuestion(ResultSet rs) throws SQLException {
        return new AdminQuestionResponse(
                rs.getLong("question_id"), rs.getLong("category_id"),
                rs.getObject("related_template_id", Long.class),
                rs.getObject("related_section_id", Long.class),
                rs.getObject("language_id", Long.class), rs.getString("question_type"),
                rs.getString("prompt"), rs.getString("code_snippet"),
                readJson(rs.getString("choice_options")), rs.getString("correct_choice_key"),
                rs.getString("reference_answer"), readJson(rs.getString("grading_criteria")),
                rs.getString("explanation"), rs.getString("difficulty"),
                rs.getInt("pass_score"), rs.getInt("display_order"),
                rs.getBoolean("is_published"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    private JsonNode readJson(String value) {
        if (value == null) return null;
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored JSON could not be parsed.", exception);
        }
    }
}
