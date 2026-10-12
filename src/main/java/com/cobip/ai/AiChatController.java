package com.cobip.ai;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ai")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "AI 챗봇", description = "로그인 사용자의 일회성 학습 질문")
class AiChatController {
    private final AiClient aiClient;

    AiChatController(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    @PostMapping("/chat")
    ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", request.message());
        body.put("useRag", request.useRag());
        JsonNode data = aiClient.post("/ai/chat", body);
        String answer = data.path("answer").asText();
        String source = data.path("source").asText();
        if (answer.isBlank() || !java.util.List.of("ollama", "rag", "fallback").contains(source)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI chat response is incomplete.");
        }
        return new ChatResponse(answer, source, data.path("ragUsed").asBoolean(false),
                data.path("references"), data.path("intent").asText(null));
    }
}

record ChatRequest(@NotBlank @Size(max = 4000) String message, Boolean useRag) {}

record ChatResponse(String answer, String source, boolean ragUsed, JsonNode references, String intent) {}
