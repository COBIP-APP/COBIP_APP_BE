package com.cobip.domain.activity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.JsonNode;

record SubmissionCreateRequest(
        Long userId,
        String selectedChoiceKey,
        String answerText,
        String sourceCode
) {
}

record SubmissionResponse(
        Long id,
        Long userId,
        Long questionId,
        String selectedChoiceKey,
        String answerText,
        String sourceCode,
        String gradingStatus,
        String gradingMethod,
        BigDecimal score,
        String feedback,
        JsonNode criteriaResults,
        String aiModelVersion,
        OffsetDateTime submittedAt,
        OffsetDateTime gradedAt
) {
}

record ProgressUpdateRequest(
        Long lastSectionId,
        Boolean completed
) {
}

record ProgressResponse(
        Long userId,
        Long templateId,
        Long lastSectionId,
        OffsetDateTime startedAt,
        OffsetDateTime lastStudiedAt,
        OffsetDateTime completedAt
) {
}

record BookmarkResponse(
        Long userId,
        Long templateId,
        String title,
        String summary,
        String difficulty,
        OffsetDateTime createdAt
) {
}
