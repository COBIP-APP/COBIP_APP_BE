package com.cobip.domain.content;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record IdResponse(Long id) {}

record TemplateSaveRequest(
        @NotNull Long categoryId,
        Long languageId,
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 300) String summary,
        @NotBlank String description,
        @NotNull @Pattern(regexp = "EASY|NORMAL|HARD") String difficulty,
        @NotNull @Min(1) Integer displayOrder
) {}

record SectionSaveRequest(
        Long parentSectionId,
        @NotBlank @Size(max = 150) String title,
        String body,
        String exampleCode,
        @NotNull @Min(1) Integer displayOrder
) {}

record SectionUpdateRequest(
        @NotBlank @Size(max = 150) String title,
        String body,
        String exampleCode,
        @NotNull @Min(1) Integer displayOrder
) {}

record QuestionSaveRequest(
        @NotNull Long categoryId,
        Long relatedTemplateId,
        Long relatedSectionId,
        Long languageId,
        @NotNull @Pattern(regexp = "MULTIPLE_CHOICE|CODE_EXPLANATION|CODE_WRITING") String questionType,
        @NotBlank String prompt,
        String codeSnippet,
        JsonNode choiceOptions,
        @Size(max = 10) String correctChoiceKey,
        String referenceAnswer,
        JsonNode gradingCriteria,
        String explanation,
        @NotNull @Pattern(regexp = "EASY|NORMAL|HARD") String difficulty,
        @NotNull @Min(0) @Max(100) Integer passScore,
        @NotNull @Min(1) Integer displayOrder
) {}

record PublicationRequest(@NotNull Boolean published) {}

record AdminQuestionResponse(
        Long id,
        Long categoryId,
        Long relatedTemplateId,
        Long relatedSectionId,
        Long languageId,
        String questionType,
        String prompt,
        String codeSnippet,
        JsonNode choiceOptions,
        String correctChoiceKey,
        String referenceAnswer,
        JsonNode gradingCriteria,
        String explanation,
        String difficulty,
        Integer passScore,
        Integer displayOrder,
        Boolean published,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
