package com.cobip.domain.content;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api")
class ContentController {

    private final ContentRepository contentRepository;

    ContentController(ContentRepository contentRepository) {
        this.contentRepository = contentRepository;
    }

    @GetMapping("/templates")
    List<TemplateSummaryResponse> templates(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long languageId,
            @RequestParam(defaultValue = "true") boolean publishedOnly,
            @AuthenticationPrincipal Jwt jwt
    ) {
        requireAdminForDrafts(publishedOnly, jwt);
        return contentRepository.findTemplates(categoryId, languageId, publishedOnly);
    }

    @GetMapping("/templates/{templateId}")
    TemplateDetailResponse template(
            @PathVariable Long templateId,
            @RequestParam(defaultValue = "true") boolean publishedOnly,
            @AuthenticationPrincipal Jwt jwt
    ) {
        requireAdminForDrafts(publishedOnly, jwt);
        return contentRepository.findTemplate(templateId, publishedOnly)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found."));
    }

    @GetMapping("/templates/{templateId}/sections")
    List<TemplateSectionResponse> templateSections(@PathVariable Long templateId, @AuthenticationPrincipal Jwt jwt) {
        boolean publishedOnly = !"ADMIN".equals(jwt.getClaimAsString("role"));
        if (contentRepository.findTemplate(templateId, publishedOnly).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found.");
        }
        return contentRepository.findTemplateSections(templateId);
    }

    @GetMapping("/questions")
    List<QuestionResponse> questions(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long languageId,
            @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(defaultValue = "true") boolean publishedOnly,
            @AuthenticationPrincipal Jwt jwt
    ) {
        requireAdminForDrafts(publishedOnly, jwt);
        return contentRepository.findQuestions(categoryId, languageId, templateId, sectionId, publishedOnly);
    }

    @GetMapping("/questions/{questionId}")
    QuestionResponse question(
            @PathVariable Long questionId,
            @RequestParam(defaultValue = "true") boolean publishedOnly,
            @AuthenticationPrincipal Jwt jwt
    ) {
        requireAdminForDrafts(publishedOnly, jwt);
        return contentRepository.findQuestion(questionId, publishedOnly)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found."));
    }

    private void requireAdminForDrafts(boolean publishedOnly, Jwt jwt) {
        if (!publishedOnly && !"ADMIN".equals(jwt.getClaimAsString("role"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required.");
        }
    }
}
