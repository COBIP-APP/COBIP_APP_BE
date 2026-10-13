package com.cobip.domain.content;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

record UserQuestionSaveRequest(
        @NotNull UUID clientRequestId,
        @Schema(description = "사용자가 입력한 언어·생성 주제 문자열. 숫자 분류 ID가 아니며 앞뒤 공백을 제거해 보존한다.", example = "AWS")
        @NotBlank String category,
        @NotBlank String difficulty,
        @Valid @NotNull GeneratedQuestion question
) {}

record GeneratedQuestion(
        Long id,
        @NotBlank String type,
        @NotBlank String question,
        @NotNull List<String> options,
        @NotBlank String answer,
        String explanation,
        List<String> references,
        List<String> gradingKeywords
) {}

record UserQuestionDraft(
        UUID clientRequestId,
        String languageCode,
        String generationTopic,
        String questionType,
        String prompt,
        JsonNode choiceOptions,
        String correctChoiceKey,
        String referenceAnswer,
        JsonNode gradingCriteria,
        String explanation,
        String difficulty
) {}
