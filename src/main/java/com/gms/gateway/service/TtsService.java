package com.gms.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Server-side text-to-speech proxy.
 *
 * <p>The browser's Web Speech API falls back to the default voice when the
 * user's OS doesn't ship with a Bengali voice installed — which makes
 * Bangla prompts sound like garbled English. To guarantee a real
 * Bangla pronunciation regardless of the client machine, this service
 * proxies to Google Translate's free TTS endpoint and returns MP3 audio
 * the browser can play directly via an {@code <audio>} element.</p>
 *
 * <p>The endpoint is free and key-less but unofficial. It enforces two
 * limits we work around here:</p>
 * <ul>
 *   <li>Single-request text length cap (~200 chars). We split long
 *       sentences on punctuation and concatenate the resulting MP3
 *       byte streams — MP3 is a frame-stream format so naive byte
 *       concatenation is valid.</li>
 *   <li>Must present a desktop browser User-Agent and Referer or
 *       Google rejects the request with a generic 403.</li>
 * </ul>
 *
 * <p>Supported language codes map to the {@code tl=} parameter Google
 * Translate expects: {@code bn} -> Bengali, {@code hi} -> Hindi,
 * {@code en} -> English. Unknown codes fall back to English.</p>
 */
@Service
public class TtsService {

    private static final Logger logger = LoggerFactory.getLogger(TtsService.class);

    /** Google Translate's hard cap per request; longer text gets chunked. */
    private static final int MAX_CHUNK_CHARS = 180;

    private final RestTemplate llmRestTemplate;

    public TtsService(@Qualifier("llmRestTemplate") RestTemplate llmRestTemplate) {
        this.llmRestTemplate = llmRestTemplate;
    }

    /**
     * Fetch concatenated MP3 audio for {@code text} in the given
     * {@code lang} (BCP-47 like {@code bn-BD} or short like {@code bn}).
     * Returns an empty array on failure (so the client can degrade
     * gracefully to text-only display).
     */
    public byte[] synthesize(String text, String lang) {
        if (text == null || text.isBlank()) {
            return new byte[0];
        }
        String tl = mapToGoogleLang(lang);
        List<String> chunks = splitForTts(text);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (String chunk : chunks) {
            try {
                byte[] mp3 = fetchChunk(chunk, tl);
                if (mp3 != null && mp3.length > 0) {
                    out.write(mp3, 0, mp3.length);
                }
            } catch (Exception e) {
                logger.warn("TTS chunk fetch failed for lang={} chunk='{}...': {}",
                        tl, chunk.substring(0, Math.min(30, chunk.length())), e.getMessage());
                // Skip the failing chunk but keep the rest so partial audio still plays.
            }
        }
        return out.toByteArray();
    }

    private byte[] fetchChunk(String text, String tl) {
        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String url = "https://translate.google.com/translate_tts"
                + "?ie=UTF-8"
                + "&q=" + encoded
                + "&tl=" + tl
                + "&client=tw-ob";

        HttpHeaders headers = new HttpHeaders();
        // Google rejects requests that don't look like a real browser. These
        // headers mimic a current Chrome on Windows — exact values are not
        // important as long as it's a recent desktop-browser fingerprint.
        headers.set(HttpHeaders.USER_AGENT,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/120.0.0.0 Safari/537.36");
        headers.set(HttpHeaders.REFERER, "https://translate.google.com/");
        headers.set(HttpHeaders.ACCEPT, "audio/mpeg,audio/*;q=0.9,*/*;q=0.8");
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9");

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<byte[]> resp = llmRestTemplate.exchange(
                    url, HttpMethod.GET, entity, byte[].class);
            return resp.getBody() != null ? resp.getBody() : new byte[0];
        } catch (RestClientException e) {
            logger.warn("Google TTS returned error for tl={}: {}", tl, e.getMessage());
            throw e;
        }
    }

    /**
     * Split {@code text} into <= {@link #MAX_CHUNK_CHARS} chunks at
     * sentence/word boundaries so each chunk survives Google's per-call
     * length cap. Order is preserved.
     */
    private static List<String> splitForTts(String text) {
        String trimmed = text.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= MAX_CHUNK_CHARS) {
            return List.of(trimmed);
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        // Split on sentence boundaries first; if a single sentence is still
        // too long, fall back to word boundaries.
        String[] sentences = trimmed.split("(?<=[।.!?؟])\\s+");
        for (String sentence : sentences) {
            if (sentence.length() <= MAX_CHUNK_CHARS) {
                appendIfFits(chunks, current, sentence);
                continue;
            }
            // Sentence too long: chop on word boundaries.
            String[] words = sentence.split("\\s+");
            for (String word : words) {
                if (current.length() + word.length() + 1 > MAX_CHUNK_CHARS) {
                    if (current.length() > 0) {
                        chunks.add(current.toString());
                        current.setLength(0);
                    }
                    // If a single word is still over the cap (Bengali compound
                    // words can be long), hard-split it.
                    if (word.length() > MAX_CHUNK_CHARS) {
                        for (int i = 0; i < word.length(); i += MAX_CHUNK_CHARS) {
                            chunks.add(word.substring(i, Math.min(word.length(), i + MAX_CHUNK_CHARS)));
                        }
                    } else {
                        current.append(word);
                    }
                } else {
                    if (current.length() > 0) current.append(' ');
                    current.append(word);
                }
            }
        }
        if (current.length() > 0) chunks.add(current.toString());
        return chunks;
    }

    private static void appendIfFits(List<String> chunks, StringBuilder current, String sentence) {
        if (current.length() == 0) {
            current.append(sentence);
        } else if (current.length() + sentence.length() + 1 <= MAX_CHUNK_CHARS) {
            current.append(' ').append(sentence);
        } else {
            chunks.add(current.toString());
            current.setLength(0);
            current.append(sentence);
        }
    }

    /**
     * Map a BCP-47 code like {@code bn-BD} or {@code hi-IN} to Google's
     * two-letter {@code tl=} value. Unknown languages degrade to English.
     */
    private static String mapToGoogleLang(String lang) {
        if (lang == null || lang.isBlank()) return "en";
        String lower = lang.toLowerCase(Locale.ROOT);
        if (lower.startsWith("bn")) return "bn";   // Bengali
        if (lower.startsWith("hi")) return "hi";   // Hindi
        if (lower.startsWith("ur")) return "ur";   // Urdu
        if (lower.startsWith("ar")) return "ar";   // Arabic
        if (lower.startsWith("zh")) return "zh-CN"; // Chinese
        if (lower.startsWith("es")) return "es";   // Spanish
        if (lower.startsWith("fr")) return "fr";   // French
        return "en";
    }

    /** Media type for the synthesized audio. */
    public MediaType audioMediaType() {
        return MediaType.parseMediaType("audio/mpeg");
    }
}
