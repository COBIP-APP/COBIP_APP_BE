package com.cobip.domain.content;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

record MediaResponse(
        Long id,
        String mediaType,
        String objectKey,
        String originalName,
        String contentType,
        Long fileSize,
        String altText,
        Integer displayOrder,
        OffsetDateTime createdAt
) {
}

record TemplateSummaryResponse(
        Long id,
        Long categoryId,
        String categoryCode,
        String categoryName,
        Long languageId,
        String languageCode,
        String languageName,
        String title,
        String summary,
        String difficulty,
        Integer displayOrder,
        Boolean published,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<MediaResponse> media
) {
}

record TemplateDetailResponse(
        Long id,
        Long categoryId,
        String categoryCode,
        String categoryName,
        Long languageId,
        String languageCode,
        String languageName,
        String title,
        String summary,
        String description,
        String difficulty,
        Integer displayOrder,
        Boolean published,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<MediaResponse> media,
        List<TemplateSectionResponse> sections
) {
}

record TemplateSectionResponse(
        Long id,
        Long parentId,
        String title,
        String body,
        String exampleCode,
        Integer displayOrder,
        List<MediaResponse> media,
        List<TemplateSectionResponse> children
) {
}

record QuestionResponse(
        Long id,
        Long categoryId,
        String categoryCode,
        String categoryName,
        Long relatedTemplateId,
        Long relatedSectionId,
        Long languageId,
        String languageCode,
        String languageName,
        String questionType,
        String prompt,
        String codeSnippet,
        JsonNode choiceOptions,
        String explanation,
        String difficulty,
        Integer passScore,
        Integer displayOrder,
        Boolean published,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<MediaResponse> media
) {
}
