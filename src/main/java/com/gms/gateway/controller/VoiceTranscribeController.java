package com.gms.gateway.controller;

import com.gms.gateway.service.VoiceIntakeService;
import com.gms.gateway.service.WhisperTranscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-side speech-to-text fallback endpoint. The browser's Web Speech API
 * is the primary path (zero latency, no upload) but it isn't available on
 * every browser/machine — this endpoint accepts a recorded audio blob and
 * runs it through OpenAI Whisper.
 *
 * <p>Returns the transcript plus the same agentic-AI extraction payload as
 * the inline voice button, so the frontend can swap between the two input
 * paths without changing its post-processing code.</p>
 */
@Controller
public class VoiceTranscribeController {

    private static final Logger logger = LoggerFactory.getLogger(VoiceTranscribeController.class);

    @Autowired
    private WhisperTranscriptionService whisper;

    @Autowired
    private VoiceIntakeService voiceIntakeService;

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "User";
    }

    /**
     * Capability probe. Returns whether the server-side voice stack is
     * configured (Whisper + Claude). The frontend checks this on page load
     * so it can hide the "Speak your request" button when there's no
     * speech-to-text backend available.
     */
    @GetMapping(value = "/api/voice/available", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> availability() {
        Map<String, Object> body = new HashMap<>();
        body.put("whisperAvailable", whisper.isAvailable());
        return ResponseEntity.ok(body);
    }

    @PostMapping(value = "/api/voice/transcribe", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> transcribe(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "patientId", required = false) Long patientId,
            HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();

        if (!whisper.isAvailable()) {
            response.put("status", "unavailable");
            response.put("message", "Server-side speech-to-text is not configured. "
                    + "Please use the browser microphone or type your request instead.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        WhisperTranscriptionService.Result transcription = whisper.transcribe(audio);
        if (transcription == null || transcription.transcript == null) {
            response.put("status", "error");
            response.put("message", "Could not transcribe the audio. Please try again or type your request.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        // Run the same agentic extraction the inline button uses, so the
        // rest of the form code stays unchanged whether the transcript came
        // from Web Speech API or Whisper.
        VoiceIntakeService.ExtractionResult extraction =
                voiceIntakeService.extract(transcription.transcript, patientId, null);

        if (patientId != null) {
            extraction.fields.setPatientId(patientId);
        }
        extraction.fields.setVoiceTranscript(transcription.transcript);

        response.put("status", extraction.status.name());
        response.put("transcript", transcription.transcript);
        response.put("language", transcription.language);
        response.put("missingFields", extraction.missingFields);
        response.put("clarifyingQuestions", extraction.clarifyingQuestions);
        response.put("fields", extraction.fields);
        // Typed structured extraction — frontend uses this to populate the
        // form fields directly (doctorName, specialization, hospitalName,
        // appointmentDate, preferredTime, reasonForVisit, contactNumber).
        // Replaces the brittle pipe-delimited reasonForVisit parsing the
        // previous build had to do.
        response.put("extracted", extraction.extracted);
        response.put("fromAgenticAi", extraction.fromAgenticAi);
        response.put("username", currentUsername());
        response.put("ip", request.getRemoteAddr());
        return ResponseEntity.ok(response);
    }
}
