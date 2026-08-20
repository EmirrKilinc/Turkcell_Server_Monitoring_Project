package com.monitoring.poc.aiops;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * Thin client for a locally-running Ollama instance
 * (http://127.0.0.1:11434/api/generate). No streaming - AIOpsService always
 * wants one complete answer back, so {@code stream=false} keeps the response
 * a single JSON object instead of a chunked line stream.
 */
@Service
public class OllamaService {

    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);

    private final RestClient restClient;
    private final String model;
    private final String baseUrl;

    public OllamaService(@Value("${app.ollama.base-url:http://127.0.0.1:11434}") String baseUrl,
                          @Value("${app.ollama.model:qwen2.5-coder:1.5b}") String model,
                          @Value("${app.ollama.connect-timeout-seconds:5}") int connectTimeoutSeconds,
                          @Value("${app.ollama.read-timeout-seconds:45}") int readTimeoutSeconds) {
        this.model = model;
        this.baseUrl = baseUrl;

        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .withReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();

        log.info("[OLLAMA INIT] OllamaService baslatildi. BaseUrl={}, Model={}, ConnectTimeout={}s, ReadTimeout={}s",
                baseUrl, model, connectTimeoutSeconds, readTimeoutSeconds);
    }

    /**
     * @param userPrompt    the actual question/instruction for the model
     * @param systemContext dynamic "System Context" block (current metric
     *                      snapshot, recent failures, etc) prepended ahead of
     *                      the user prompt so the model always answers with
     *                      the live system state in view
     * @return the model's trimmed text response
     * @throws OllamaUnavailableException on connection failure, timeout, or
     *                                    an empty/malformed response
     */
    public String generate(String userPrompt, String systemContext) {
        String fullPrompt = "System Context:\n" + (systemContext == null ? "" : systemContext)
                + "\n\nUser:\n" + userPrompt;

        OllamaGenerateRequest request = new OllamaGenerateRequest(model, fullPrompt, false);

        long startedAt = System.currentTimeMillis();
        try {
            OllamaGenerateResponse response = restClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(OllamaGenerateResponse.class);

            long durationMs = System.currentTimeMillis() - startedAt;

            if (response == null || response.response() == null || response.response().isBlank()) {
                log.warn("[OLLAMA EMPTY] Ollama bos yanit dondurdu ({} ms sonra)", durationMs);
                throw new OllamaUnavailableException("Ollama bos yanit dondurdu");
            }

            log.info("[OLLAMA SUCCESS] Yanit alindi ({} ms, {} karakter)", durationMs, response.response().length());
            return response.response().trim();

        } catch (ResourceAccessException e) {
            long durationMs = System.currentTimeMillis() - startedAt;
            log.warn("[OLLAMA TIMEOUT/CONN] Baglanti hatasi veya zaman asimi ({} ms sonra): {}", durationMs, e.getMessage());
            throw new OllamaUnavailableException("Ollama servisine ulasilamadi (" + baseUrl + ")", e);
        } catch (RestClientException e) {
            long durationMs = System.currentTimeMillis() - startedAt;
            log.warn("[OLLAMA ERROR] Istek basarisiz ({} ms sonra): {}", durationMs, e.getMessage());
            throw new OllamaUnavailableException("Ollama istegi basarisiz oldu", e);
        }
    }

    private record OllamaGenerateRequest(String model, String prompt, boolean stream) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OllamaGenerateResponse(String model, String response, Boolean done) {
    }
}
