package com.cobip.domain.content;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ContentRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    ContentRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    List<TemplateSummaryResponse> findTemplates(Long categoryId, Long languageId, boolean publishedOnly) {
        QueryParts query = new QueryParts("""
                SELECT t.template_id, t.category_id, c.code AS category_code, c.name AS category_name,
                       t.language_id, l.code AS language_code, l.name AS language_name,
                       t.title, t.summary, t.difficulty, t.display_order, t.is_published,
                       t.created_at, t.updated_at
                FROM templates t
                JOIN categories c ON c.category_id = t.category_id
                LEFT JOIN languages l ON l.language_id = t.language_id
                WHERE 1 = 1
                """);
        query.add("AND t.category_id = :categoryId", "categoryId", categoryId);
        query.add("AND t.language_id = :languageId", "languageId", languageId);
        query.add("AND t.is_published = true", publishedOnly);
        query.orderBy("ORDER BY t.display_order, t.template_id");

        return jdbcTemplate.query(query.sql(), query.params(), (rs, rowNum) -> mapTemplateSummary(rs));
    }

    Optional<TemplateDetailResponse> findTemplate(Long templateId, boolean publishedOnly) {
        QueryParts query = new QueryParts("""
                SELECT t.template_id, t.category_id, c.code AS category_code, c.name AS category_name,
                       t.language_id, l.code AS language_code, l.name AS language_name,
                       t.title, t.summary, t.description, t.difficulty, t.display_order, t.is_published,
                       t.created_at, t.updated_at
                FROM templates t
                JOIN categories c ON c.category_id = t.category_id
                LEFT JOIN languages l ON l.language_id = t.language_id
                WHERE t.template_id = :templateId
                """);
        query.param("templateId", templateId);
        query.add("AND t.is_published = true", publishedOnly);

        List<TemplateDetailResponse> templates = jdbcTemplate.query(query.sql(), query.params(),
                (rs, rowNum) -> mapTemplateDetail(rs));
        return templates.stream().findFirst()
                .map(template -> templateWithRelations(template, findTemplateSections(template.id())));
    }

    List<TemplateSectionResponse> findTemplateSections(Long templateId) {
        String sql = """
                SELECT section_id, parent_section_id, title, body, example_code, display_order
                FROM template_sections
                WHERE template_id = :templateId
                ORDER BY parent_section_id NULLS FIRST, display_order, section_id
                """;

        List<SectionRow> rows = jdbcTemplate.query(sql,
                new MapSqlParameterSource("templateId", templateId),
                (rs, rowNum) -> new SectionRow(
                        rs.getLong("section_id"),
                        rs.getObject("parent_section_id", Long.class),
                        rs.getString("title"),
                        rs.getString("body"),
                        rs.getString("example_code"),
                        rs.getInt("display_order")));

        Map<Long, List<SectionRow>> childrenByParent = new LinkedHashMap<>();
        for (SectionRow row : rows) {
            childrenByParent.computeIfAbsent(row.parentId(), ignored -> new ArrayList<>()).add(row);
        }

        return childrenByParent.getOrDefault(null, List.of()).stream()
                .sorted(Comparator.comparing(SectionRow::displayOrder))
                .map(row -> toSectionResponse(row, childrenByParent))
                .toList();
    }

    List<QuestionResponse> findQuestions(
            Long categoryId,
            Long languageId,
            Long templateId,
            Long sectionId,
            boolean publishedOnly
    ) {
        QueryParts query = new QueryParts("""
                SELECT q.question_id, q.category_id, c.code AS category_code, c.name AS category_name,
                       q.related_template_id, q.related_section_id,
                       q.language_id, l.code AS language_code, l.name AS language_name,
                       q.question_type, q.prompt, q.code_snippet, q.choice_options,
                       q.explanation, q.difficulty, q.pass_score, q.display_order, q.is_published,
                       q.created_at, q.updated_at
                FROM questions q
                JOIN categories c ON c.category_id = q.category_id
                LEFT JOIN languages l ON l.language_id = q.language_id
                WHERE 1 = 1
                """);
        query.add("AND q.category_id = :categoryId", "categoryId", categoryId);
        query.add("AND q.language_id = :languageId", "languageId", languageId);
        query.add("AND q.related_template_id = :templateId", "templateId", templateId);
        query.add("AND q.related_section_id = :sectionId", "sectionId", sectionId);
        query.add("AND q.is_published = true", publishedOnly);
        query.orderBy("ORDER BY q.display_order, q.question_id");

        return jdbcTemplate.query(query.sql(), query.params(), (rs, rowNum) -> mapQuestion(rs));
    }

    Optional<QuestionResponse> findQuestion(Long questionId, boolean publishedOnly) {
        QueryParts query = new QueryParts("""
                SELECT q.question_id, q.category_id, c.code AS category_code, c.name AS category_name,
                       q.related_template_id, q.related_section_id,
                       q.language_id, l.code AS language_code, l.name AS language_name,
                       q.question_type, q.prompt, q.code_snippet, q.choice_options,
                       q.explanation, q.difficulty, q.pass_score, q.display_order, q.is_published,
                       q.created_at, q.updated_at
                FROM questions q
                JOIN categories c ON c.category_id = q.category_id
                LEFT JOIN languages l ON l.language_id = q.language_id
                WHERE q.question_id = :questionId
                """);
        query.param("questionId", questionId);
        query.add("AND q.is_published = true", publishedOnly);

        return jdbcTemplate.query(query.sql(), query.params(), (rs, rowNum) -> mapQuestion(rs))
                .stream()
                .findFirst();
    }

    private TemplateSummaryResponse mapTemplateSummary(ResultSet rs) throws SQLException {
        Long id = rs.getLong("template_id");
        return new TemplateSummaryResponse(
                id,
                rs.getLong("category_id"),
                rs.getString("category_code"),
                rs.getString("category_name"),
                rs.getObject("language_id", Long.class),
                rs.getString("language_code"),
                rs.getString("language_name"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getString("difficulty"),
                rs.getInt("display_order"),
                rs.getBoolean("is_published"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                findMedia("template_id", id));
    }

    private TemplateDetailResponse mapTemplateDetail(ResultSet rs) throws SQLException {
        Long id = rs.getLong("template_id");
        return new TemplateDetailResponse(
                id,
                rs.getLong("category_id"),
                rs.getString("category_code"),
                rs.getString("category_name"),
                rs.getObject("language_id", Long.class),
                rs.getString("language_code"),
                rs.getString("language_name"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getString("description"),
                rs.getString("difficulty"),
                rs.getInt("display_order"),
                rs.getBoolean("is_published"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                findMedia("template_id", id),
                List.of());
    }

    private TemplateDetailResponse templateWithRelations(
            TemplateDetailResponse template,
            List<TemplateSectionResponse> sections
    ) {
        return new TemplateDetailResponse(
                template.id(),
                template.categoryId(),
                template.categoryCode(),
                template.categoryName(),
                template.languageId(),
                template.languageCode(),
                template.languageName(),
                template.title(),
                template.summary(),
                template.description(),
                template.difficulty(),
                template.displayOrder(),
                template.published(),
                template.createdAt(),
                template.updatedAt(),
                template.media(),
                sections);
    }

    private TemplateSectionResponse toSectionResponse(
            SectionRow row,
            Map<Long, List<SectionRow>> childrenByParent
    ) {
        List<TemplateSectionResponse> children = childrenByParent.getOrDefault(row.id(), List.of()).stream()
                .sorted(Comparator.comparing(SectionRow::displayOrder))
                .map(child -> toSectionResponse(child, childrenByParent))
                .toList();

        return new TemplateSectionResponse(
                row.id(),
                row.parentId(),
                row.title(),
                row.body(),
                row.exampleCode(),
                row.displayOrder(),
                findMedia("section_id", row.id()),
                children);
    }

    private QuestionResponse mapQuestion(ResultSet rs) throws SQLException {
        Long id = rs.getLong("question_id");
        return new QuestionResponse(
                id,
                rs.getLong("category_id"),
                rs.getString("category_code"),
                rs.getString("category_name"),
                rs.getObject("related_template_id", Long.class),
                rs.getObject("related_section_id", Long.class),
                rs.getObject("language_id", Long.class),
                rs.getString("language_code"),
                rs.getString("language_name"),
                rs.getString("question_type"),
                rs.getString("prompt"),
                rs.getString("code_snippet"),
                readJson(rs.getString("choice_options")),
                rs.getString("explanation"),
                rs.getString("difficulty"),
                rs.getInt("pass_score"),
                rs.getInt("display_order"),
                rs.getBoolean("is_published"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                findMedia("question_id", id));
    }

    private List<MediaResponse> findMedia(String targetColumn, Long targetId) {
        String sql = """
                SELECT media_id, media_type, object_key, original_name, content_type,
                       file_size, alt_text, display_order, created_at
                FROM media_files
                WHERE %s = :targetId
                ORDER BY display_order, media_id
                """.formatted(targetColumn);

        return jdbcTemplate.query(sql,
                new MapSqlParameterSource("targetId", targetId),
                (rs, rowNum) -> new MediaResponse(
                        rs.getLong("media_id"),
                        rs.getString("media_type"),
                        rs.getString("object_key"),
                        rs.getString("original_name"),
                        rs.getString("content_type"),
                        rs.getLong("file_size"),
                        rs.getString("alt_text"),
                        rs.getInt("display_order"),
                        rs.getObject("created_at", OffsetDateTime.class)));
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

    private record SectionRow(
            Long id,
            Long parentId,
            String title,
            String body,
            String exampleCode,
            Integer displayOrder
    ) {
    }

    private static final class QueryParts {
        private final StringBuilder sql;
        private final MapSqlParameterSource params = new MapSqlParameterSource();

        private QueryParts(String sql) {
            this.sql = new StringBuilder(sql);
        }

        private void add(String clause, boolean enabled) {
            if (enabled) {
                sql.append('\n').append(clause);
            }
        }

        private void add(String clause, String name, Object value) {
            if (value != null) {
                sql.append('\n').append(clause);
                params.addValue(name, value);
            }
        }

        private void param(String name, Object value) {
            params.addValue(name, value);
        }

        private void orderBy(String clause) {
            sql.append('\n').append(clause);
        }

        private String sql() {
            return sql.toString();
        }

        private MapSqlParameterSource params() {
            return params;
        }
    }
}
