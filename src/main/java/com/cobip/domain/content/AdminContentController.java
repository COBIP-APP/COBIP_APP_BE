package com.cobip.domain.content;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "관리자 콘텐츠", description = "관리자 웹에서 학습 템플릿·목차·문제 초안을 저장하고 공개합니다.")
class AdminContentController {
    private final AdminContentRepository repository;

    AdminContentController(AdminContentRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    IdResponse createTemplate(@AuthenticationPrincipal Jwt jwt,
                              @Valid @RequestBody TemplateSaveRequest request) {
        requireAdmin(jwt);
        return new IdResponse(repository.createTemplate(request, Long.valueOf(jwt.getSubject())));
    }

    @PutMapping("/templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateTemplate(@AuthenticationPrincipal Jwt jwt, @PathVariable Long templateId,
                        @Valid @RequestBody TemplateSaveRequest request) {
        requireAdmin(jwt);
        if (!repository.updateTemplate(templateId, request)) notFound("Template");
    }

    @PatchMapping("/templates/{templateId}/publication")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void publishTemplate(@AuthenticationPrincipal Jwt jwt, @PathVariable Long templateId,
                         @Valid @RequestBody PublicationRequest request) {
        requireAdmin(jwt);
        if (!repository.setTemplatePublished(templateId, request.published())) notFound("Template");
    }

    @PostMapping("/templates/{templateId}/sections")
    @ResponseStatus(HttpStatus.CREATED)
    IdResponse createSection(@AuthenticationPrincipal Jwt jwt, @PathVariable Long templateId,
                             @Valid @RequestBody SectionSaveRequest request) {
        requireAdmin(jwt);
        return new IdResponse(repository.createSection(templateId, request));
    }

    @PutMapping("/templates/{templateId}/sections/{sectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateSection(@AuthenticationPrincipal Jwt jwt, @PathVariable Long templateId,
                       @PathVariable Long sectionId, @Valid @RequestBody SectionUpdateRequest request) {
        requireAdmin(jwt);
        if (!repository.updateSection(templateId, sectionId, request)) notFound("Section");
    }

    @PostMapping("/questions")
    @ResponseStatus(HttpStatus.CREATED)
    IdResponse createQuestion(@AuthenticationPrincipal Jwt jwt,
                              @Valid @RequestBody QuestionSaveRequest request) {
        requireAdmin(jwt);
        validateQuestion(request);
        return new IdResponse(repository.createQuestion(request, Long.valueOf(jwt.getSubject())));
    }

    @GetMapping("/questions/{questionId}")
    AdminQuestionResponse question(@AuthenticationPrincipal Jwt jwt, @PathVariable Long questionId) {
        requireAdmin(jwt);
        return repository.findQuestion(questionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found."));
    }

    @PutMapping("/questions/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateQuestion(@AuthenticationPrincipal Jwt jwt, @PathVariable Long questionId,
                        @Valid @RequestBody QuestionSaveRequest request) {
        requireAdmin(jwt);
        validateQuestion(request);
        if (!repository.updateQuestion(questionId, request)) notFound("Question");
    }

    @PatchMapping("/questions/{questionId}/publication")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void publishQuestion(@AuthenticationPrincipal Jwt jwt, @PathVariable Long questionId,
                         @Valid @RequestBody PublicationRequest request) {
        requireAdmin(jwt);
        if (!repository.setQuestionPublished(questionId, request.published())) notFound("Question");
    }

    private void requireAdmin(Jwt jwt) {
        if (!"ADMIN".equals(jwt.getClaimAsString("role"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required.");
        }
    }

    private void validateQuestion(QuestionSaveRequest request) {
        if (request.relatedSectionId() != null && request.relatedTemplateId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A section requires a related template.");
        }
        if (request.choiceOptions() != null && !request.choiceOptions().isArray()
                || request.gradingCriteria() != null && !request.gradingCriteria().isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choices and criteria must be arrays.");
        }
        if ("MULTIPLE_CHOICE".equals(request.questionType())
                && (request.choiceOptions() == null || request.choiceOptions().isEmpty()
                    || request.correctChoiceKey() == null || request.correctChoiceKey().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Multiple choice requires choices and an answer key.");
        }
    }

    private void notFound(String resource) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, resource + " not found.");
    }
}
