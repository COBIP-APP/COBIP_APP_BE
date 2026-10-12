package com.cobip.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class AiClient {
    private final RestClient client;

    AiClient(@Value("${app.ai.base-url}") String baseUrl) {
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(5));
        requests.setReadTimeout(Duration.ofSeconds(210));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
    }

    public JsonNode post(String path, Object request) {
        try {
            JsonNode response = client.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                    .body(request).retrieve().body(JsonNode.class);
            if (response == null || !response.path("success").asBoolean(false)
                    || !response.path("data").isObject()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI server returned an invalid response.");
            }
            return response.path("data");
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI server rejected the request.", exception);
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "AI server is unavailable or timed out.", exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI server response could not be read.", exception);
        }
    }
}
