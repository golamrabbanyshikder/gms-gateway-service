package com.gms.gateway.controller;

import com.gms.gateway.service.TtsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;
/**
 * Server-side TTS endpoint the booking voice UI calls when the browser
 * doesn't have a native voice for the patient's language (most commonly
 * Bengali on Windows machines that never installed a bn-BD voice pack).
 *
 * <p>Returns an {@code audio/mpeg} stream the browser plays via a hidden
 * {@code <audio>} element. The endpoint is reachable to any authenticated
 * user — both staff and patients use it. Caching is per-URL and short so
 * repeated identical prompts don't re-hit the upstream TTS service.</p>
 */
@Controller
@RequestMapping("/tts")
public class TtsController {

    private static final Logger logger = LoggerFactory.getLogger(TtsController.class);

    @Autowired
    private TtsService ttsService;

    @GetMapping(value = "/speak", produces = "audio/mpeg")
    public ResponseEntity<byte[]> speak(@RequestParam("text") String text,
                                        @RequestParam(value = "lang", defaultValue = "en") String lang) {
        if (text == null || text.isBlank()) {
            return ResponseEntity.noContent().build();
        }
        // Refuse to proxy absurdly long prompts (DoS guard) — splitForTts
        // would also fail but better to fail fast.
        if (text.length() > 2000) {
            logger.warn("TTS request exceeded 2000-char limit ({} chars), lang={}", text.length(), lang);
            return ResponseEntity.badRequest().build();
        }
        byte[] audio = ttsService.synthesize(text, lang);
        if (audio.length == 0) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok()
                .contentType(ttsService.audioMediaType())
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .header("X-TTS-Lang", lang)
                .body(audio);
    }
}
