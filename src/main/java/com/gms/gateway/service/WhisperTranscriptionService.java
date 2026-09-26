package com.gms.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-side speech-to-text via OpenAI Whisper. Used as a fallback when the
 * browser's Web Speech API isn't available (older browsers, Safari users who
 * deny microphone access for the JS engine, or kiosk machines where only a
 * recorded audio file is available). When no API key is configured the
 * service reports unavailability so the controller can return a clear 503.
 *
 * <p>The model is configurable via {@code openai.whisper.model} so the user
 * can swap to whisper-1 / gpt-4o-transcribe without code changes.</p>
 */
@Service
public class WhisperTranscriptionService {

    private static final Logger logger = LoggerFactory.getLogger(WhisperTranscriptionService.class);

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.whisper.model:whisper-1}")
    private String model;

    @Value("${openai.api-url:https://api.openai.com}")
    private String apiUrl;

    private final RestTemplate llmRestTemplate;

    public WhisperTranscriptionService(@Qualifier("llmRestTemplate") RestTemplate llmRestTemplate) {
        this.llmRestTemplate = llmRestTemplate;
    }

    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Transcribe a recorded audio file. Returns a result with {@code transcript}
     * and {@code language}, or {@code null} on failure (the caller decides how
     * to surface that — usually a 503 with an "audio transcription unavailable"
     * message so the patient can keep typing).
     */
    public Result transcribe(MultipartFile audio) {
        if (!isAvailable()) {
            logger.info("Whisper transcription requested but no OPENAI_API_KEY configured; returning unavailability.");
            return null;
        }
        if (audio == null || audio.isEmpty()) {
            return null;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(apiKey);

            // OpenAI's audio/transcriptions endpoint requires multipart/form-data
            // with a 'file' part, a 'model' field, and an optional 'language' hint.
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new org.springframework.core.io.ByteArrayResource(audio.getBytes()) {
                @Override public String getFilename() {
                    // OpenAI requires a filename with an audio extension.
                    String original = audio.getOriginalFilename();
                    return (original != null && !original.isBlank()) ? original : "audio.webm";
                }
            });
            body.add("model", model);
            // Hint bn-BD when the audio likely is Bangla — speeds up detection
            // and improves accuracy on noisy mobile recordings. Falls back to
            // auto-detect if the hint is wrong.
            body.add("language", "bn");

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = llmRestTemplate.postForObject(
                    apiUrl + "/v1/audio/transcriptions", request, Map.class);

            if (response == null) {
                return null;
            }
            Object text = response.get("text");
            if (!(text instanceof String) || ((String) text).isBlank()) {
                return null;
            }
            Result r = new Result();
            r.transcript = ((String) text).trim();
            Object lang = response.get("language");
            r.language = (lang instanceof String) ? (String) lang : "bn";
            return r;
        } catch (HttpStatusCodeException e) {
            logger.warn("Whisper API returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (RestClientException e) {
            logger.warn("Whisper API call failed: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            logger.warn("Unexpected error during Whisper transcription", e);
            return null;
        }
    }

    public static class Result {
        public String transcript;
        public String language;
    }
}
