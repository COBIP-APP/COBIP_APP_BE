package com.cobip.domain.activity;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api")
class ActivityController {

    private final ActivityRepository activityRepository;

    ActivityController(ActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    @PostMapping("/questions/{questionId}/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    SubmissionResponse submitAnswer(
            @PathVariable Long questionId,
            @RequestBody SubmissionCreateRequest request
    ) {
        if (request.userId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required.");
        }
        if (answerCount(request) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Exactly one of selectedChoiceKey, answerText, or sourceCode is required.");
        }

        ActivityRepository.QuestionAnswerRow question = activityRepository.findQuestionAnswer(questionId)
                .filter(ActivityRepository.QuestionAnswerRow::published)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found."));

        ActivityRepository.GradingDecision grading = decideGrading(question, request);

        return activityRepository.createSubmission(
                request.userId(),
                questionId,
                blankToNull(request.selectedChoiceKey()),
                blankToNull(request.answerText()),
                blankToNull(request.sourceCode()),
                grading);
    }

    @GetMapping("/questions/{questionId}/submissions/latest")
    SubmissionResponse latestSubmission(
            @PathVariable Long questionId,
            @org.springframework.web.bind.annotation.RequestParam Long userId
    ) {
        return activityRepository.findLatestSubmission(userId, questionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found."));
    }

    @PutMapping("/users/{userId}/progress/templates/{templateId}")
    ProgressResponse updateProgress(
            @PathVariable Long userId,
            @PathVariable Long templateId,
            @RequestBody ProgressUpdateRequest request
    ) {
        return activityRepository.upsertProgress(
                userId,
                templateId,
                request.lastSectionId(),
                request.completed());
    }

    @GetMapping("/users/{userId}/progress/templates/{templateId}")
    ProgressResponse progress(
            @PathVariable Long userId,
            @PathVariable Long templateId
    ) {
        return activityRepository.findProgress(userId, templateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Progress not found."));
    }

    @GetMapping("/users/{userId}/bookmarks")
    List<BookmarkResponse> bookmarks(@PathVariable Long userId) {
        return activityRepository.findBookmarks(userId);
    }

    @PutMapping("/users/{userId}/bookmarks/templates/{templateId}")
    BookmarkResponse addBookmark(
            @PathVariable Long userId,
            @PathVariable Long templateId
    ) {
        return activityRepository.addBookmark(userId, templateId);
    }

    @DeleteMapping("/users/{userId}/bookmarks/templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeBookmark(
            @PathVariable Long userId,
            @PathVariable Long templateId
    ) {
        activityRepository.removeBookmark(userId, templateId);
    }

    private ActivityRepository.GradingDecision decideGrading(
            ActivityRepository.QuestionAnswerRow question,
            SubmissionCreateRequest request
    ) {
        if ("MULTIPLE_CHOICE".equals(question.questionType()) && question.correctChoiceKey() != null) {
            boolean correct = question.correctChoiceKey().equals(blankToNull(request.selectedChoiceKey()));
            return new ActivityRepository.GradingDecision(
                    "COMPLETED",
                    "DB",
                    correct ? BigDecimal.valueOf(100) : BigDecimal.ZERO,
                    correct ? "Correct answer." : "Incorrect answer.",
                    OffsetDateTime.now());
        }

        return new ActivityRepository.GradingDecision(
                "PENDING",
                null,
                null,
                null,
                null);
    }

    private int answerCount(SubmissionCreateRequest request) {
        int count = 0;
        if (blankToNull(request.selectedChoiceKey()) != null) {
            count++;
        }
        if (blankToNull(request.answerText()) != null) {
            count++;
        }
        if (blankToNull(request.sourceCode()) != null) {
            count++;
        }
        return count;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
