package com.cobip.domain.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

/** Runs only against an explicitly supplied disposable PostgreSQL database. */
@EnabledIfEnvironmentVariable(named = "QUESTION_TEST_DB_URL", matches = ".+")
class UserQuestionTopicIntegrationTest {
    @Test
    void persistsTopicsAcrossReadsAndPreservesOwnershipAndDeletion() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                System.getenv("QUESTION_TEST_DB_URL"),
                System.getenv().getOrDefault("QUESTION_TEST_DB_USER", "topic_test"),
                System.getenv().getOrDefault("QUESTION_TEST_DB_PASSWORD", ""));
        String schema = "topic_test_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate admin = new JdbcTemplate(dataSource);
        admin.execute("CREATE SCHEMA " + schema);
        dataSource.setConnectionProperties(properties(schema));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).target("5").load().migrate();
            jdbc.update("""
                    INSERT INTO users (user_id, email, nickname, password_hash,
                                       service_terms_agreed_at, privacy_terms_agreed_at)
                    VALUES (1, 'owner@test.invalid', 'owner', 'unused', now(), now()),
                           (2, 'other@test.invalid', 'other', 'unused', now(), now())
                    """);
            Long legacyId = jdbc.queryForObject("""
                    INSERT INTO questions (category_id, created_by, question_type, prompt, difficulty,
                                           pass_score, display_order, is_published)
                    VALUES ((SELECT category_id FROM categories WHERE code = 'CODING_PROBLEM'),
                            1, 'OX', 'Legacy AWS question', 'NORMAL', 70, 1, true)
                    RETURNING question_id
                    """, Long.class);
            Flyway.configure().dataSource(dataSource).schemas(schema).load().migrate();
            assertNull(jdbc.queryForObject("SELECT generation_topic FROM questions WHERE question_id = ?",
                    String.class, legacyId));
            jdbc.update("INSERT INTO languages (code, name, display_order) VALUES ('KOTLIN', '코틀린', 4)");

            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            ContentRepository repository = new ContentRepository(new NamedParameterJdbcTemplate(dataSource), mapper);
            UserQuestionController controller = new UserQuestionController(repository, mapper);
            Jwt owner = jwt(1);
            UserQuestionSaveRequest awsRequest = request("  AWS  ");
            QuestionResponse aws = controller.save(owner, awsRequest).getBody();
            assertNotNull(aws);
            for (String topic : List.of("Docker", "Java", "aws", "js", "kotlin", "코틀린", "pYtHoN")) {
                var saved = controller.save(owner, request(topic));
                assertEquals(HttpStatus.CREATED, saved.getStatusCode());
                QuestionResponse question = saved.getBody();
                assertNotNull(question);
                assertEquals(topic, question.generationTopic());
                if (List.of("Docker", "aws").contains(topic)) {
                    assertNull(question.languageId());
                    assertNull(question.languageCode());
                    assertNull(question.languageName());
                } else {
                    assertNotNull(question.languageId());
                    assertEquals(switch (topic) {
                        case "Java" -> "JAVA";
                        case "js" -> "JAVASCRIPT";
                        case "pYtHoN" -> "PYTHON";
                        default -> "KOTLIN";
                    }, question.languageCode());
                }
            }
            assertEquals("AWS", aws.generationTopic());
            assertNull(aws.languageId());
            assertNull(aws.languageName());
            assertEquals("CODING_PROBLEM", aws.categoryCode());
            assertEquals(1L, aws.createdBy());
            assertFalse(aws.published());
            assertNull(aws.relatedTemplateId());
            assertNull(aws.relatedSectionId());
            assertEquals("O", jdbc.queryForObject(
                    "SELECT correct_choice_key FROM questions WHERE question_id = ?", String.class, aws.id()));
            assertEquals("AWS", jdbc.queryForObject(
                    "SELECT generation_topic FROM questions WHERE question_id = ?", String.class, aws.id()));

            // Recreate repository/controller and JWT to read only persisted data, as on a new session.
            repository = new ContentRepository(new NamedParameterJdbcTemplate(dataSource), mapper);
            controller = new UserQuestionController(repository, mapper);
            owner = jwt(1);
            var retry = controller.save(owner, new UserQuestionSaveRequest(awsRequest.clientRequestId(),
                    "changed topic", awsRequest.difficulty(), awsRequest.question()));
            assertEquals(HttpStatus.OK, retry.getStatusCode());
            assertEquals(aws.id(), retry.getBody().id());
            assertEquals("AWS", retry.getBody().generationTopic());
            assertEquals(8, controller.mine(owner).size());
            for (QuestionResponse question : controller.mine(owner)) {
                assertEquals(question.generationTopic(), repository.findQuestion(question.id(), true, 1L)
                        .orElseThrow().generationTopic());
                assertTrue(mapper.readTree(mapper.writeValueAsString(question)).has("generationTopic"));
            }
            ContentController content = new ContentController(repository);
            assertEquals("AWS", content.question(aws.id(), true, owner).generationTopic());
            assertNull(content.question(aws.id(), true, owner).explanation());
            assertEquals(List.of(legacyId), content.questions(null, null, null, null, true, owner)
                    .stream().map(QuestionResponse::id).toList());
            assertNull(content.questions(null, null, null, null, true, owner).get(0).generationTopic());
            assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                    () -> content.question(aws.id(), true, jwt(2))).getStatusCode());
            UserQuestionController deletionController = controller;
            assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                    () -> deletionController.delete(jwt(2), aws.id())).getStatusCode());

            // Published reads also expose the stored topic while chapter questions stay in the common API.
            Long dockerId = controller.mine(owner).stream().filter(q -> "Docker".equals(q.generationTopic()))
                    .findFirst().orElseThrow().id();
            jdbc.update("UPDATE questions SET is_published = true WHERE question_id = ?", dockerId);
            assertEquals("Docker", content.questions(null, null, null, null, true, owner).stream()
                    .filter(q -> q.id().equals(dockerId)).findFirst().orElseThrow().generationTopic());
            Long templateId = jdbc.queryForObject("""
                    INSERT INTO templates (category_id, created_by, language_id, title, summary, description,
                                           difficulty, display_order)
                    VALUES ((SELECT category_id FROM categories WHERE code = 'GRAMMAR'), 1,
                            (SELECT language_id FROM languages WHERE code = 'JAVA'),
                            'Chapter', 'Summary', 'Description', 'NORMAL', 1)
                    RETURNING template_id
                    """, Long.class);
            jdbc.update("UPDATE questions SET related_template_id = ? WHERE question_id = ?", templateId, legacyId);
            assertTrue(content.questions(null, null, null, null, true, owner).stream()
                    .anyMatch(q -> q.id().equals(legacyId) && templateId.equals(q.relatedTemplateId())));

            jdbc.update("INSERT INTO submissions (user_id, question_id, selected_choice_key) VALUES (1, ?, 'O')",
                    aws.id());
            controller.delete(owner, aws.id());
            assertTrue(repository.findQuestion(aws.id(), false, 1L).isEmpty());
            assertFalse(controller.mine(owner).stream().anyMatch(q -> q.id().equals(aws.id())));
            assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM questions WHERE question_id = ?",
                    java.time.OffsetDateTime.class, aws.id()));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM submissions WHERE question_id = ?",
                    Integer.class, aws.id()));
            assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                    () -> deletionController.save(jwt(1), awsRequest)).getStatusCode());
            assertEquals(HttpStatus.CREATED, controller.save(owner, request("AWS")).getStatusCode());
            try (var factory = Validation.buildDefaultValidatorFactory()) {
                assertFalse(factory.getValidator().validate(request("   ")).isEmpty());
            }
        } finally {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private java.util.Properties properties(String schema) {
        var properties = new java.util.Properties();
        properties.setProperty("currentSchema", schema);
        return properties;
    }

    private UserQuestionSaveRequest request(String topic) {
        return new UserQuestionSaveRequest(UUID.randomUUID(), topic, "중급",
                new GeneratedQuestion(1L, "ox", "테스트 문제", List.of("O", "X"), "1",
                        "테스트 해설", List.of(), List.of()));
    }

    private Jwt jwt(long userId) {
        return Jwt.withTokenValue("integration-test").header("alg", "none")
                .subject(Long.toString(userId)).claim("role", "USER").build();
    }
}
