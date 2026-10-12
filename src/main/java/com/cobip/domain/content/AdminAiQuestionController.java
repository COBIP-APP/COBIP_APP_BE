package com.cobip.domain.content;

import com.cobip.ai.AiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/ai")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "관리자 AI 문제 생성", description = "AI 객관식 한 문제를 비공개 초안으로 저장합니다.")
class AdminAiQuestionController {
    private final AiClient aiClient;
    private final AdminContentRepository repository;
    private final ObjectMapper objectMapper;

    AdminAiQuestionController(AiClient aiClient, AdminContentRepository repository, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/questions/generate")
    @ResponseStatus(HttpStatus.CREATED)
    IdResponse generate(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AiQuestionGenerationRequest request) {
        if (!"ADMIN".equals(jwt.getClaimAsString("role"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required.");
        }
        if (request.relatedSectionId() != null && request.relatedTemplateId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A section requires a related template.");
        }
        JsonNode data = aiClient.post("/ai/quiz/generate", Map.of(
                "user_id", jwt.getSubject(),
                "category", request.aiCategory(),
                "domain", request.domain(),
                "quiz_types", List.of("MULTIPLE_CHOICE"),
                "count_per_type", 1));
        JsonNode quizzes = data.path("quizzes");
        if (!quizzes.isArray() || quizzes.size() != 1) invalidResult();
        JsonNode quiz = quizzes.get(0);
        JsonNode options = quiz.path("options");
        String answer = quiz.path("answer").asText();
        if (!"multiple_choice".equals(quiz.path("type").asText())
                || quiz.path("question").asText().isBlank() || !options.isArray()
                || options.size() < 2 || options.size() > 10
                || !answer.matches("[1-9]|10") || Integer.parseInt(answer) > options.size()) {
            invalidResult();
        }
        List<Map<String, String>> choices = new ArrayList<>();
        for (int index = 0; index < options.size(); index++) {
            JsonNode option = options.get(index);
            if (!option.isTextual() || option.asText().isBlank()) invalidResult();
            choices.add(Map.of("key", Integer.toString(index + 1),
                    "text", option.asText().replaceFirst("^\\d+\\.\\s*", "")));
        }
        String difficulty = switch (data.path("difficulty").asText().toLowerCase()) {
            case "hard", "advanced" -> "HARD";
            case "normal", "intermediate", "medium" -> "NORMAL";
            default -> "EASY";
        };
        QuestionSaveRequest draft = new QuestionSaveRequest(
                request.categoryId(), request.relatedTemplateId(), request.relatedSectionId(),
                request.languageId(), "MULTIPLE_CHOICE", quiz.path("question").asText(), null,
                objectMapper.valueToTree(choices), answer, null, null,
                quiz.path("explanation").asText(null), difficulty, 70, request.displayOrder());
        return new IdResponse(repository.createQuestion(draft, Long.valueOf(jwt.getSubject())));
    }

    private void invalidResult() {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI generated an invalid multiple-choice question.");
    }
}

record AiQuestionGenerationRequest(
        @NotNull Long categoryId,
        @NotBlank String aiCategory,
        @NotBlank String domain,
        Long languageId,
        Long relatedTemplateId,
        Long relatedSectionId,
        @NotNull @Min(1) Integer displayOrder
) {}
