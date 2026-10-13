package com.cobip.domain.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users/me/questions")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "내 문제", description = "AI 생성 문제 저장·조회·삭제")
class UserQuestionController {
    private final ContentRepository repository;
    private final ObjectMapper objectMapper;

    UserQuestionController(ContentRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    ResponseEntity<QuestionResponse> save(@AuthenticationPrincipal Jwt jwt,
                                          @Valid @RequestBody UserQuestionSaveRequest request) {
        Long userId = Long.valueOf(jwt.getSubject());
        UserQuestionDraft draft = toDraft(request);
        ContentRepository.SavedQuestion saved = repository.saveOwnedQuestion(userId, draft);
        QuestionResponse response = repository.findQuestion(saved.id(), false, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "A deleted question cannot be saved again with the same request ID."));
        return ResponseEntity.status(saved.created() ? HttpStatus.CREATED : HttpStatus.OK).body(response);
    }

    @GetMapping
    List<QuestionResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return repository.findOwnedQuestions(Long.valueOf(jwt.getSubject()));
    }

    @DeleteMapping("/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long questionId) {
        if (!repository.deleteOwnedQuestion(Long.valueOf(jwt.getSubject()), questionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found.");
        }
    }

    private UserQuestionDraft toDraft(UserQuestionSaveRequest request) {
        GeneratedQuestion question = request.question();
        String type = switch (question.type().trim().toLowerCase(Locale.ROOT)) {
            case "multiple_choice" -> "MULTIPLE_CHOICE";
            case "ox" -> "OX";
            case "short_answer" -> "SHORT_ANSWER";
            case "blank" -> "FILL_BLANK";
            case "descriptive" -> "DESCRIPTIVE";
            default -> throw invalid("Unsupported question type.");
        };
        String difficulty = switch (request.difficulty().trim().toLowerCase(Locale.ROOT)) {
            case "초급", "easy", "beginner" -> "EASY";
            case "중급", "normal", "intermediate", "medium" -> "NORMAL";
            case "고급", "hard", "advanced" -> "HARD";
            default -> throw invalid("Unsupported difficulty.");
        };
        String generationTopic = request.category().trim();
        String languageCode = "JS".equalsIgnoreCase(generationTopic) ? "JAVASCRIPT" : generationTopic;
        JsonNode choices = null;
        String correctChoiceKey = null;
        String referenceAnswer = null;
        if ("MULTIPLE_CHOICE".equals(type)) {
            if (question.options().size() < 2 || question.options().size() > 10) {
                throw invalid("Multiple choice requires 2 to 10 options.");
            }
            List<Map<String, String>> normalized = new ArrayList<>();
            for (int index = 0; index < question.options().size(); index++) {
                String option = question.options().get(index);
                if (option == null || option.isBlank()) throw invalid("Options must have text.");
                String text = option.replaceFirst("^\\s*\\d+[.)]\\s*", "").trim();
                if (text.isBlank()) throw invalid("Options must have text.");
                normalized.add(Map.of("key", Integer.toString(index + 1), "text", text));
            }
            correctChoiceKey = question.answer().trim();
            if (!correctChoiceKey.matches("[1-9]|10")
                    || Integer.parseInt(correctChoiceKey) > normalized.size()) {
                throw invalid("Answer must be an option number.");
            }
            choices = objectMapper.valueToTree(normalized);
        } else if ("OX".equals(type)) {
            String oxAnswer = switch (question.answer().trim().toUpperCase(Locale.ROOT)) {
                case "1", "O" -> "O";
                case "2", "X" -> "X";
                default -> throw invalid("OX answer must be 1, 2, O or X.");
            };
            if (!question.options().isEmpty() && question.options().stream()
                    .noneMatch(option -> option != null && oxAnswer.equals(
                            option.replaceFirst("^\\s*\\d+[.)]\\s*", "").trim().toUpperCase(Locale.ROOT)))) {
                throw invalid("OX answer must match an option.");
            }
            correctChoiceKey = oxAnswer;
            choices = objectMapper.valueToTree(List.of(
                    Map.of("key", "O", "text", "O"), Map.of("key", "X", "text", "X")));
        } else {
            referenceAnswer = question.answer().trim();
        }
        if (question.gradingKeywords() != null && question.gradingKeywords().stream()
                .anyMatch(keyword -> keyword == null || keyword.isBlank())) {
            throw invalid("Grading keywords must have text.");
        }
        JsonNode criteria = question.gradingKeywords() == null || question.gradingKeywords().isEmpty()
                ? null : objectMapper.valueToTree(question.gradingKeywords());
        return new UserQuestionDraft(request.clientRequestId(), languageCode, generationTopic, type,
                question.question().trim(), choices, correctChoiceKey, referenceAnswer,
                criteria, question.explanation(), difficulty);
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
