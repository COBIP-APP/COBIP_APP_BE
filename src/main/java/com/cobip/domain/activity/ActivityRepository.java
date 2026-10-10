package com.cobip.domain.activity;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ActivityRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    ActivityRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    boolean publishedTemplateExists(Long templateId) {
        String sql = "SELECT EXISTS (SELECT 1 FROM templates WHERE template_id = :id AND is_published = true)";
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                sql, new MapSqlParameterSource("id", templateId), Boolean.class));
    }

    Optional<QuestionAnswerRow> findQuestionAnswer(Long questionId) {
        String sql = """
                SELECT question_id, question_type, correct_choice_key, is_published
                FROM questions
                WHERE question_id = :questionId
                """;

        return jdbcTemplate.query(sql,
                new MapSqlParameterSource("questionId", questionId),
                (rs, rowNum) -> new QuestionAnswerRow(
                        rs.getLong("question_id"),
                        rs.getString("question_type"),
                        rs.getString("correct_choice_key"),
                        rs.getBoolean("is_published")))
                .stream()
                .findFirst();
    }

    SubmissionResponse createSubmission(
            Long userId,
            Long questionId,
            String selectedChoiceKey,
            String answerText,
            String sourceCode,
            GradingDecision grading
    ) {
        String sql = """
                INSERT INTO submissions (
                    user_id, question_id, selected_choice_key, answer_text, source_code,
                    grading_status, grading_method, score, feedback, graded_at
                )
                VALUES (
                    :userId, :questionId, :selectedChoiceKey, :answerText, :sourceCode,
                    :gradingStatus, :gradingMethod, :score, :feedback, :gradedAt
                )
                RETURNING submission_id
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("questionId", questionId)
                .addValue("selectedChoiceKey", selectedChoiceKey)
                .addValue("answerText", answerText)
                .addValue("sourceCode", sourceCode)
                .addValue("gradingStatus", grading.status())
                .addValue("gradingMethod", grading.method())
                .addValue("score", grading.score())
                .addValue("feedback", grading.feedback())
                .addValue("gradedAt", grading.gradedAt());

        Long submissionId = jdbcTemplate.queryForObject(sql, params, Long.class);
        return findSubmission(submissionId).orElseThrow();
    }

    Optional<SubmissionResponse> findSubmission(Long submissionId) {
        String sql = """
                SELECT submission_id, user_id, question_id, selected_choice_key, answer_text, source_code,
                       grading_status, grading_method, score, feedback, criteria_results,
                       ai_model_version, submitted_at, graded_at
                FROM submissions
                WHERE submission_id = :submissionId
                """;

        return jdbcTemplate.query(sql,
                new MapSqlParameterSource("submissionId", submissionId),
                (rs, rowNum) -> mapSubmission(rs))
                .stream()
                .findFirst();
    }

    Optional<SubmissionResponse> findLatestSubmission(Long userId, Long questionId) {
        String sql = """
                SELECT submission_id, user_id, question_id, selected_choice_key, answer_text, source_code,
                       grading_status, grading_method, score, feedback, criteria_results,
                       ai_model_version, submitted_at, graded_at
                FROM submissions
                WHERE user_id = :userId AND question_id = :questionId
                ORDER BY submitted_at DESC, submission_id DESC
                LIMIT 1
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("questionId", questionId);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> mapSubmission(rs))
                .stream()
                .findFirst();
    }

    ProgressResponse upsertProgress(Long userId, Long templateId, Long lastSectionId, Boolean completed) {
        String sql = """
                INSERT INTO user_progress (
                    user_id, template_id, last_section_id, completed_at
                )
                VALUES (
                    :userId, :templateId, :lastSectionId,
                    CASE WHEN :completed = true THEN now() ELSE NULL END
                )
                ON CONFLICT (user_id, template_id)
                DO UPDATE SET
                    last_section_id = COALESCE(EXCLUDED.last_section_id, user_progress.last_section_id),
                    last_studied_at = now(),
                    completed_at = CASE
                        WHEN :completed = true THEN COALESCE(user_progress.completed_at, now())
                        WHEN :completed = false THEN NULL
                        ELSE user_progress.completed_at
                    END
                RETURNING user_id, template_id, last_section_id, started_at, last_studied_at, completed_at
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("templateId", templateId)
                .addValue("lastSectionId", lastSectionId)
                .addValue("completed", completed);

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> mapProgress(rs));
    }

    Optional<ProgressResponse> findProgress(Long userId, Long templateId) {
        String sql = """
                SELECT user_id, template_id, last_section_id, started_at, last_studied_at, completed_at
                FROM user_progress
                WHERE user_id = :userId AND template_id = :templateId
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("templateId", templateId);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> mapProgress(rs))
                .stream()
                .findFirst();
    }

    List<LearningProgressResponse> findLearningProgress(Long userId) {
        String sql = """
                SELECT p.template_id, t.title, c.code AS category_code, c.name AS category_name,
                       l.code AS language_code, l.name AS language_name,
                       p.last_section_id, s.title AS last_section_title,
                       p.started_at, p.last_studied_at, p.completed_at
                FROM user_progress p
                JOIN templates t ON t.template_id = p.template_id
                JOIN categories c ON c.category_id = t.category_id
                LEFT JOIN languages l ON l.language_id = t.language_id
                LEFT JOIN template_sections s ON s.section_id = p.last_section_id
                WHERE p.user_id = :userId AND t.is_published = true
                ORDER BY p.last_studied_at DESC, p.template_id DESC
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("userId", userId),
                (rs, rowNum) -> new LearningProgressResponse(
                        rs.getLong("template_id"),
                        rs.getString("title"),
                        rs.getString("category_code"),
                        rs.getString("category_name"),
                        rs.getString("language_code"),
                        rs.getString("language_name"),
                        rs.getObject("last_section_id", Long.class),
                        rs.getString("last_section_title"),
                        rs.getObject("started_at", OffsetDateTime.class),
                        rs.getObject("last_studied_at", OffsetDateTime.class),
                        rs.getObject("completed_at", OffsetDateTime.class)));
    }

    BookmarkResponse addBookmark(Long userId, Long templateId) {
        String sql = """
                INSERT INTO template_bookmarks (user_id, template_id)
                VALUES (:userId, :templateId)
                ON CONFLICT (user_id, template_id) DO NOTHING
                """;
        jdbcTemplate.update(sql, userAndTemplateParams(userId, templateId));

        return findBookmark(userId, templateId).orElseThrow();
    }

    void removeBookmark(Long userId, Long templateId) {
        String sql = """
                DELETE FROM template_bookmarks
                WHERE user_id = :userId AND template_id = :templateId
                """;
        jdbcTemplate.update(sql, userAndTemplateParams(userId, templateId));
    }

    List<BookmarkResponse> findBookmarks(Long userId) {
        String sql = """
                SELECT b.user_id, b.template_id, t.title, t.summary, t.difficulty, b.created_at
                FROM template_bookmarks b
                JOIN templates t ON t.template_id = b.template_id
                WHERE b.user_id = :userId AND t.is_published = true
                ORDER BY b.created_at DESC, b.template_id DESC
                """;

        return jdbcTemplate.query(sql,
                new MapSqlParameterSource("userId", userId),
                (rs, rowNum) -> mapBookmark(rs));
    }

    private Optional<BookmarkResponse> findBookmark(Long userId, Long templateId) {
        String sql = """
                SELECT b.user_id, b.template_id, t.title, t.summary, t.difficulty, b.created_at
                FROM template_bookmarks b
                JOIN templates t ON t.template_id = b.template_id
                WHERE b.user_id = :userId AND b.template_id = :templateId
                """;

        return jdbcTemplate.query(sql, userAndTemplateParams(userId, templateId), (rs, rowNum) -> mapBookmark(rs))
                .stream()
                .findFirst();
    }

    private SubmissionResponse mapSubmission(ResultSet rs) throws SQLException {
        return new SubmissionResponse(
                rs.getLong("submission_id"),
                rs.getLong("user_id"),
                rs.getLong("question_id"),
                rs.getString("selected_choice_key"),
                rs.getString("answer_text"),
                rs.getString("source_code"),
                rs.getString("grading_status"),
                rs.getString("grading_method"),
                rs.getObject("score", BigDecimal.class),
                rs.getString("feedback"),
                readJson(rs.getString("criteria_results")),
                rs.getString("ai_model_version"),
                rs.getObject("submitted_at", OffsetDateTime.class),
                rs.getObject("graded_at", OffsetDateTime.class));
    }

    private ProgressResponse mapProgress(ResultSet rs) throws SQLException {
        return new ProgressResponse(
                rs.getLong("user_id"),
                rs.getLong("template_id"),
                rs.getObject("last_section_id", Long.class),
                rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("last_studied_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class));
    }

    private BookmarkResponse mapBookmark(ResultSet rs) throws SQLException {
        return new BookmarkResponse(
                rs.getLong("user_id"),
                rs.getLong("template_id"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getString("difficulty"),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    private MapSqlParameterSource userAndTemplateParams(Long userId, Long templateId) {
        return new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("templateId", templateId);
    }

    private JsonNode readJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored JSON could not be parsed.", exception);
        }
    }

    record QuestionAnswerRow(
            Long questionId,
            String questionType,
            String correctChoiceKey,
            Boolean published
    ) {
    }

    record GradingDecision(
            String status,
            String method,
            BigDecimal score,
            String feedback,
            OffsetDateTime gradedAt
    ) {
    }
}
