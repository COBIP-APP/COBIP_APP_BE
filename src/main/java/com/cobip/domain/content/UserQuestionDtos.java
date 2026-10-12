package com.cobip.domain.content;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

record UserQuestionSaveRequest(
        @NotNull UUID clientRequestId,
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
        String questionType,
        String prompt,
        JsonNode choiceOptions,
        String correctChoiceKey,
        String referenceAnswer,
        JsonNode gradingCriteria,
        String explanation,
        String difficulty
) {}
