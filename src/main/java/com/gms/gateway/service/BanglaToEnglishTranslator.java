package com.gms.gateway.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gms.gateway.dto.AppointmentRequestDto;
import com.gms.gateway.dto.DoctorDto;
import com.gms.gateway.dto.HospitalDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agentic AI voice-intake front-end.
 *
 * <p>Given a transcript that may be in Bangla, English, or any other language,
 * this service does TWO things in one Claude API call (live):</p>
 * <ol>
 *   <li>Detects the source language and, if it isn't English, translates the
 *       transcript to English (preserving named entities like doctor names).</li>
 *   <li>Extracts the structured booking fields the patient mentioned — date,
 *       time, doctor name or specialization, reason for visit, contact number
 *       — and returns them as a typed {@link IntakeResult}.</li>
 * </ol>
 *
 * <p>The single-shot agentic approach (translation + extraction in one pass)
 * is what the feature prompt calls out as the agentic-AI contract for the
 * voice booking flow. The LLM is asked for a strict JSON schema and the
 * gateway parses the response directly into a {@link IntakeResult} that
 * {@link VoiceIntakeService} can hand to the booking controller.</p>
 *
 * <p><b>Fallback path:</b> when {@code anthropic.api-key} is blank (no
 * {@code ANTHROPIC_API_KEY} env var set), the service degrades to the
 * deterministic rule-based translator and regex extractor that existed
 * before. That keeps the wiring working end-to-end in offline / CI
 * environments where no LLM key is available.</p>
 */
@Service
public class BanglaToEnglishTranslator {

    private static final Logger logger = LoggerFactory.getLogger(BanglaToEnglishTranslator.class);

    private static final Pattern BANGLA_DIGIT = Pattern.compile("[০-৯]");

    @Value("${anthropic.api-key:}")
    private String apiKey;

    @Value("${anthropic.model:claude-haiku-4-5-20251001}")
    private String model;

    @Value("${anthropic.api-url:https://api.anthropic.com}")
    private String apiUrl;


    private final RestTemplate llmRestTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public BanglaToEnglishTranslator(@Qualifier("llmRestTemplate") RestTemplate llmRestTemplate) {
        this.llmRestTemplate = llmRestTemplate;
    }

    // =====================================================================
    // Public API used by VoiceIntakeService
    // =====================================================================

    /**
     * Translate (if needed) AND extract structured booking fields in one
     * agentic call. Returns both the English text and the structured payload
     * the booking form needs.
     *
     * <p>If {@code anthropic.api-key} is blank or the API call fails, falls
     * back to the rule-based translator + regex extractor so the booking flow
     * still produces a (lower-quality) result.</p>
     *
     * @param transcript      raw transcript from the browser Web Speech API
     *                        (Bangla / English / mixed)
     * @param knownPatientId  patient ID when the caller already knows who
     *                        they're booking for (logged-in patient or
     *                        receptionist selecting from the patient list).
     *                        {@code null} means "ask the patient".
     */
    public IntakeResult translateAndExtract(String transcript, Long knownPatientId) {
        return translateAndExtract(transcript, knownPatientId, List.of(), List.of());
    }

    /**
     * Agentic extraction with DB context. Pass the current hospital and doctor
     * catalog so Claude can resolve a spoken name (e.g. "ল্যাবিটের শামসুল হক")
     * against real rows and return matched IDs directly. No JS fuzzy match
     * needed downstream — the frontend just sets the dropdowns by ID.
     *
     * <p>Either list may be null or empty — Claude then degrades to free-text
     * extraction (same behaviour as the no-context overload). The offline
     * fallback ignores the lists entirely.</p>
     *
     * @param hospitals  full hospital list from backend-service (id + name)
     * @param doctors    full doctor list (id + name + specialization + hospitalId)
     */
    public IntakeResult translateAndExtract(String transcript, Long knownPatientId,
                                            Collection<HospitalDto> hospitals,
                                            Collection<DoctorDto> doctors) {
        if (transcript == null || transcript.isBlank()) {
            return emptyResult();
        }

        if (apiKey == null || apiKey.isBlank()) {
            logger.info("No ANTHROPIC_API_KEY configured; using offline rule-based extraction.");
            return offlineExtraction(transcript, knownPatientId);
        }

        try {
            return callClaudeApi(transcript, knownPatientId, LocalDate.now(), hospitals, doctors);
        } catch (HttpStatusCodeException e) {
            logger.warn("Claude API returned {}: {}. Falling back to rule-based extraction.",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return offlineExtraction(transcript, knownPatientId);
        } catch (RestClientException e) {
            logger.warn("Claude API call failed ({}). Falling back to rule-based extraction.",
                    e.getMessage());
            return offlineExtraction(transcript, knownPatientId);
        } catch (Exception e) {
            logger.warn("Unexpected error during agentic extraction; falling back to rule-based.", e);
            return offlineExtraction(transcript, knownPatientId);
        }
    }

    /**
     * Whether the original transcript was in Bangla — kept here for the
     * controller's "Bangla → English" UI badge.
     */
    public boolean wasTranslated(String transcript) {
        return isBangla(transcript);
    }

    /** Whether the input contains any Bengali-script character. */
    public boolean isBangla(String text) {
        return text != null && containsBangla(text);
    }

    // =====================================================================
    // Result DTO
    // =====================================================================

    /**
     * Structured intake result that comes out of either the agentic AI path
     * or the offline fallback. Carries both the (possibly) translated
     * English text and the extracted booking fields in one bag so the
     * controller doesn't have to call two services.
     */
    public static class IntakeResult {
        /** English translation if the input was non-English; null otherwise. */
        public String translatedText;

        /** Source language detected: "bn" | "en" | "other". */
        public String sourceLanguage;

        /** Extracted doctor full name if the patient mentioned one (e.g. "Smith" or "Rahim"). */
        public String doctorName;

        /** Extracted medical specialization (e.g. "cardiology"). */
        public String specialization;

        /** Extracted hospital/clinic name if the patient mentioned one. */
        public String hospitalName;

        /** Concrete appointment date — relative references resolved to LocalDate. */
        public LocalDate appointmentDate;

        /** Preferred appointment time, normalised to HH:mm 24-hour. */
        public LocalTime preferredTime;

        /** Short symptom/complaint description in English. */
        public String reasonForVisit;

        /** Phone number extracted from the transcript (digits only). */
        public String contactNumber;

        /** Email if the patient dictated one. */
        public String contactEmail;

        /** Field names that the AI couldn't determine (e.g. "appointmentDate"). */
        public List<String> missingFields = new ArrayList<>();

        /** Friendly questions to surface to the user to fill in the gaps. */
        public List<String> clarifyingQuestions = new ArrayList<>();

        /** True if the structured fields came from the Claude API; false for offline fallback. */
        public boolean fromAgenticAi;

        /**
         * DB-matched hospital ID when the agentic AI resolved the spoken
         * hospital name against the live catalog passed into
         * {@link #translateAndExtract(String, Long, java.util.Collection, java.util.Collection)}.
         * Null when no match or no catalog given. Frontend uses this directly
         * to set the hospital dropdown — no fuzzy substring match needed.
         */
        public Long hospitalId;

        /**
         * DB-matched doctor ID when the agentic AI resolved the spoken name
         * (or "any cardiologist at Square") against the live catalog. Null
         * otherwise. Frontend uses this to set the doctor dropdown.
         */
        public Long doctorId;

        /**
         * Best-effort reason-for-visit string that the form can drop into
         * its reason textarea AND that the controller can scan for a
         * "Specialization: X | ..." / "Doctor: X | ..." / "Hospital: Y | ..."
         * prefix used by the existing JS to auto-pick the doctor dropdown.
         */
        public String compositReasonForVisit() {
            StringBuilder sb = new StringBuilder();
            if (specialization != null && !specialization.isBlank()) {
                sb.append("Specialization: ").append(specialization);
            }
            if (doctorName != null && !doctorName.isBlank()) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append("Doctor: ").append(doctorName);
            }
            if (hospitalName != null && !hospitalName.isBlank()) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append("Hospital: ").append(hospitalName);
            }
            if (reasonForVisit != null && !reasonForVisit.isBlank()) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append(reasonForVisit);
            }
            return sb.length() == 0 ? null : sb.toString();
        }

        /**
         * Project this intake result onto the appointment request DTO the
         * backend-service understands. Does not set patientId — the caller
         * (controller) does that because it knows the logged-in user.
         */
        public AppointmentRequestDto toAppointmentRequestDto() {
            AppointmentRequestDto dto = new AppointmentRequestDto();
            // Prefer the DB-matched doctor ID when the agentic AI resolved
            // the spoken name against the live catalog. Falls back to null
            // (controller doesn't set doctorId either) so the form's existing
            // fuzzy-match fallback still runs in the rare unresolved case.
            if (doctorId != null) {
                dto.setDoctorId(doctorId);
            }
            dto.setAppointmentDate(appointmentDate);
            dto.setPreferredTime(preferredTime);
            dto.setContactNumber(contactNumber);
            dto.setContactEmail(contactEmail);
            dto.setReasonForVisit(compositReasonForVisit());
            return dto;
        }
    }

    // =====================================================================
    // Agentic AI path: Claude API
    // =====================================================================

    private IntakeResult callClaudeApi(String transcript, Long knownPatientId, LocalDate today,
                                       Collection<HospitalDto> hospitals,
                                       Collection<DoctorDto> doctors) {
        String systemPrompt = buildSystemPrompt(today, hospitals, doctors);
        String userPrompt = buildUserPrompt(transcript, knownPatientId, hospitals, doctors);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 1024);
        body.put("system", systemPrompt);
        body.put("messages", List.of(
                Map.of("role", "user", "content", userPrompt)
        ));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        String url = apiUrl + "/v1/messages";
        @SuppressWarnings("unchecked")
        Map<String, Object> response = llmRestTemplate.postForObject(url, request, Map.class);

        if (response == null) {
            throw new RestClientException("Empty response body from Claude API");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        if (content == null || content.isEmpty()) {
            throw new RestClientException("No content blocks in Claude API response");
        }

        Object textObj = content.get(0).get("text");
        if (!(textObj instanceof String) || ((String) textObj).isBlank()) {
            throw new RestClientException("Empty text in Claude API response content");
        }
        String text = (String) textObj;

        // Strip markdown code fences in case the model added them despite the
        // instructions. Be permissive — handle ```json, ``` and stray whitespace.
        String json = text.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }

        LlmIntakeShape parsed;
        try {
            parsed = objectMapper.readValue(json, LlmIntakeShape.class);
        } catch (Exception parseEx) {
            logger.warn("Claude returned non-JSON content. Raw text: '{}'", truncate(text, 200));
            throw new RestClientException("Could not parse Claude response as JSON", parseEx);
        }

        return parsed.toIntakeResult();
    }

    private static String buildSystemPrompt(LocalDate today) {
        return buildSystemPrompt(today, List.of(), List.of());
    }

    private static String buildSystemPrompt(LocalDate today,
                                            Collection<HospitalDto> hospitals,
                                            Collection<DoctorDto> doctors) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a voice-intake assistant for a hospital appointment-booking service. ")
          .append("A patient (possibly speaking Bangla, English, Hindi, or Urdu) just dictated ")
          .append("their booking request into a microphone and a speech-recognition engine ")
          .append("transcribed it for you. Transcripts may have spelling errors and noise.\n\n")
          .append("Detect the source language, translate to English if needed, fix obvious ")
          .append("transcript errors, and extract the booking fields. Match any spoken hospital or ")
          .append("doctor name against the catalog below — return the matching ID when you're ")
          .append("confident, and only fall back to the name when nothing in the catalog fits.\n\n")
          .append("Respond with ONLY a JSON object matching this schema:\n")
          .append("{\n")
          .append("  \"sourceLanguage\": \"bn\" | \"en\" | \"hi\" | \"ur\" | \"other\",\n")
          .append("  \"translatedText\": \"<English translation, or null if already English>\",\n")
          .append("  \"hospitalId\": <integer from the catalog, or null>,\n")
          .append("  \"hospitalName\": \"<catalog name if matched, else free-text name, or null>\",\n")
          .append("  \"doctorId\": <integer from the catalog, or null>,\n")
          .append("  \"doctorName\": \"<catalog name if matched, else free-text name, or null>\",\n")
          .append("  \"specialization\": \"<a medical field like cardiology / orthopedics / etc., or null>\",\n")
          .append("  \"appointmentDate\": \"<YYYY-MM-DD relative to today=").append(today).append(", or null>\",\n")
          .append("  \"preferredTime\": \"<HH:mm 24-hour format, or null>\",\n")
          .append("  \"reasonForVisit\": \"<symptoms / purpose in plain English>\",\n")
          .append("  \"contactNumber\": \"<phone digits only, or null>\",\n")
          .append("  \"contactEmail\": \"<email address, or null>\",\n")
          .append("  \"missingFields\": [\"<field_name>\", ...],\n")
          .append("  \"clarifyingQuestions\": [\"<English question>\", ...]\n")
          .append("}\n\n");

        boolean hasCatalog = (hospitals != null && !hospitals.isEmpty())
                || (doctors != null && !doctors.isEmpty());
        if (hasCatalog) {
            sb.append("=== CATALOG (match spoken names against these rows) ===\n\n");
            if (hospitals != null && !hospitals.isEmpty()) {
                sb.append("Hospitals (id|name):\n");
                for (HospitalDto h : hospitals) {
                    sb.append("- ").append(h.getHospitalId()).append('|').append(safe(h.getHospitalName()));
                    if (h.getCity() != null && !h.getCity().isBlank()) {
                        sb.append(" (").append(safe(h.getCity())).append(')');
                    }
                    sb.append('\n');
                }
                sb.append('\n');
            }
            if (doctors != null && !doctors.isEmpty()) {
                sb.append("Doctors (id|name|specialization|hospitalId):\n");
                for (DoctorDto d : doctors) {
                    sb.append("- ").append(d.getDoctorId()).append('|').append(safe(d.getName()))
                      .append('|').append(safe(d.getSpecialization()))
                      .append('|').append(d.getHospitalId() == null ? "null" : d.getHospitalId().toString())
                      .append('\n');
                }
                sb.append('\n');
            }
            sb.append("Matching rules:\n")
              .append("  - For hospitalName/doctorName, prefer the catalog's exact name when matched.\n")
              .append("  - Transliterate Bangla names mentally (ল্যাবিট -> Labaid, শামসুল হক -> Shamsul Haque).\n")
              .append("  - If the patient said \"any cardiologist at Square\", pick the first cardiologist at that hospital.\n")
              .append("  - If a doctor name has a partial match (token overlap), pick the highest-overlap row.\n")
              .append("  - Only leave hospitalId/doctorId null when NOTHING in the catalog plausibly matches.\n\n");
        }

        sb.append("Required for a valid booking: (doctorId OR specialization), and appointmentDate. ")
          .append("List missing fields in missingFields and add a short clarifying question for each. ")
          .append("Return ONLY the JSON object. No prose.");
        return sb.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String buildUserPrompt(String transcript, Long knownPatientId) {
        return buildUserPrompt(transcript, knownPatientId, List.of(), List.of());
    }

    private static String buildUserPrompt(String transcript, Long knownPatientId,
                                           Collection<HospitalDto> hospitals,
                                           Collection<DoctorDto> doctors) {
        StringBuilder sb = new StringBuilder();
        sb.append("Patient voice transcript: \"").append(transcript).append("\"\n\n");
        if (knownPatientId != null) {
            sb.append("Note: this patient is already identified in our system "
                    + "(patient ID ").append(knownPatientId)
                    .append("). You don't need to extract their name or contact number from the transcript.\n\n");
        }
        if (hospitals != null && !hospitals.isEmpty()) {
            sb.append("Match hospital/doctor names against the ").append(hospitals.size())
              .append(" hospital and ").append(doctors == null ? 0 : doctors.size())
              .append(" doctor rows in the system prompt. Return matched IDs when confident.\n\n");
        }
        sb.append("Return the JSON.");
        return sb.toString();
    }

    /** Wire-format DTO for the JSON we expect Claude to emit. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class LlmIntakeShape {
        public String sourceLanguage;
        public String translatedText;
        public Long hospitalId;
        public String hospitalName;
        public Long doctorId;
        public String doctorName;
        public String specialization;
        public String appointmentDate;
        public String preferredTime;
        public String reasonForVisit;
        public String contactNumber;
        public String contactEmail;
        public List<String> missingFields;
        public List<String> clarifyingQuestions;

        public IntakeResult toIntakeResult() {
            IntakeResult out = new IntakeResult();
            out.sourceLanguage = sourceLanguage;
            out.translatedText = translatedText;
            out.hospitalId = hospitalId;
            out.hospitalName = hospitalName;
            out.doctorId = doctorId;
            out.doctorName = doctorName;
            out.specialization = specialization;
            out.reasonForVisit = reasonForVisit;
            out.contactNumber = contactNumber;
            out.contactEmail = contactEmail;
            out.missingFields = missingFields != null ? missingFields : new ArrayList<>();
            out.clarifyingQuestions = clarifyingQuestions != null
                    ? clarifyingQuestions : new ArrayList<>();
            out.fromAgenticAi = true;

            if (appointmentDate != null && !appointmentDate.isBlank()) {
                try {
                    out.appointmentDate = LocalDate.parse(appointmentDate.trim());
                } catch (Exception e) {
                    logger.warn("Could not parse appointmentDate '{}' from Claude", appointmentDate);
                }
            }
            if (preferredTime != null && !preferredTime.isBlank()) {
                try {
                    String t = preferredTime.trim();
                    // Accept HH:mm or HH:mm:ss — LocalTime.parse is happy with both.
                    out.preferredTime = LocalTime.parse(t.length() >= 5 ? t.substring(0, 5) : t);
                } catch (Exception e) {
                    logger.warn("Could not parse preferredTime '{}' from Claude", preferredTime);
                }
            }
            return out;
        }
    }

    // =====================================================================
    // Offline fallback (no API key) — keep the deterministic translator so
    // the booking flow still works in environments without an LLM key.
    // =====================================================================

    /**
     * Bengali (Bangla) → Latin transliteration map. Code-point keys (not
     * {@code char}) because some Bengali letters like "ড়" (DDA + nukta)
     * are two code units, not one. Character-by-character, good enough for
     * proper-name matching — the JS does token-level fuzzy matching
     * against doctor names, so "Shamsul" matching against "Dr. Md.
     * Shamsul Haque" is the goal, not perfect phonetics.
     */
    private static final Map<Integer, String> BANGLA_TO_LATIN = new LinkedHashMap<>();
    static {
        // Vowels (independent)
        put(0x0985, "o");   // অ
        put(0x0986, "a");   // আ
        put(0x0987, "i");   // ই
        put(0x0988, "ee");  // ঈ
        put(0x0989, "u");   // উ
        put(0x098A, "oo");  // ঊ
        put(0x098B, "ri");  // ঋ
        put(0x098F, "e");   // এ
        put(0x0990, "oi");  // ঐ
        put(0x0993, "o");   // ও
        put(0x0994, "ou");  // ঔ

        // Consonants
        put(0x0995, "k");   // ক
        put(0x0996, "kh");  // খ
        put(0x0997, "g");   // গ
        put(0x0998, "gh");  // ঘ
        put(0x0999, "ng");  // ঙ
        put(0x099A, "ch");  // চ
        put(0x099B, "chh"); // ছ
        put(0x099C, "j");   // জ
        put(0x099D, "jh");  // ঝ
        put(0x099E, "ny");  // ঞ
        put(0x099F, "t");   // ট
        put(0x09A0, "th");  // ঠ
        put(0x09A1, "d");   // ড
        put(0x09A2, "dh");  // ঢ
        put(0x09A3, "n");   // ণ
        put(0x09A4, "t");   // ত
        put(0x09A5, "th");  // থ
        put(0x09A6, "d");   // দ
        put(0x09A7, "dh");  // ধ
        put(0x09A8, "n");   // ন
        put(0x09AA, "p");   // প
        put(0x09AB, "ph");  // ফ
        put(0x09AC, "b");   // ব
        put(0x09AD, "bh");  // ভ
        put(0x09AE, "m");   // ম
        put(0x09AF, "j");   // য
        put(0x09B0, "r");   // র
        put(0x09B2, "l");   // ল
        put(0x09B6, "sh");  // শ
        put(0x09B7, "sh");  // ষ
        put(0x09B8, "s");   // স
        put(0x09B9, "h");   // হ
        put(0x09DC, "r");   // ড় (DDA + nukta, but the nukta is just a diacritic)
        put(0x09DD, "rh");  // ঢ়
        put(0x09DF, "y");   // য়
        put(0x09CE, "t");   // ৎ khanda-ta

        // Vowel signs (dependent vowels)
        put(0x09BE, "a");   // া
        put(0x09BF, "i");   // ি
        put(0x09C0, "ee");  // ী
        put(0x09C1, "u");   // ু
        put(0x09C2, "oo");  // ূ
        put(0x09C3, "ri");  // ৃ
        put(0x09C7, "e");   // ে
        put(0x09C8, "oi");  // ৈ
        put(0x09CB, "o");   // ো
        put(0x09CC, "ou");  // ৌ

        // Diacritics
        put(0x0982, "ng");  // ং
        put(0x0983, "h");   // ঃ
        put(0x0981, "");    // ঁ chandrabindu
        put(0x09CD, "");    // ্ virama / hasanta — kills the inherent vowel

        // Numerals
        for (int i = 0; i < 10; i++) {
            put(0x09E6 + i, String.valueOf(i));
        }

        // Punctuation often seen in transcripts
        put(0x0964, ".");   // । Bengali full stop
        put(0x0965, "..");  // ॥
    }

    private static void put(int codePoint, String latin) {
        BANGLA_TO_LATIN.put(codePoint, latin);
    }

    /**
     * Transliterate a Bengali (or mixed-script) string to Latin characters
     * using {@link #BANGLA_TO_LATIN}. Any character not in the map is
     * preserved (ASCII whitespace, punctuation, digits stay as-is). The
     * output is lowercased so the JS can do case-insensitive substring
     * matching against doctor names.
     */
    public static String transliterate(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(s.length() * 2);
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int charCount = Character.charCount(cp);
            if (cp < 0x80) {
                // ASCII — keep as-is (whitespace, punctuation, digits).
                out.append(s, i, i + charCount);
            } else {
                String mapped = BANGLA_TO_LATIN.get(cp);
                if (mapped != null) {
                    out.append(mapped);
                }
                // Unknown non-ASCII — drop silently rather than emit garbage.
            }
            i += charCount;
        }
        return out.toString().toLowerCase().replaceAll("\\s+", " ").trim();
    }

    /** Pattern: "Dr. <Name>" / "doctor <Name>" / "ডাক্তার <Name>" — for the offline fallback.
     *  Bengali names use the U+0980-U+09FF range; we capture up to two words. */
    private static final Pattern DOCTOR_NAME = Pattern.compile(
            "\\b(?:dr\\.?|doctor|ডাক্তার)\\s+((?:[A-Z][a-zA-Z'\\-]+|[ঀ-৿]{2,})(?:\\s+(?:[A-Z][a-zA-Z'\\-]+|[ঀ-৿]{2,}))?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern REASON = Pattern.compile(
            "\\b(?:for|because|regarding|about|complaint)\\s+([^.!?\\n]{2,80})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DOW = Pattern.compile(
            "\\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun|today|tomorrow)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TIME = Pattern.compile(
            "\\b(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?|am|pm|o'?clock)?\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PHONE = Pattern.compile("(\\+?\\d[\\d\\s\\-]{6,18}\\d)");

    private static final String[] OFFLINE_SPECIALIZATIONS = {
            "cardiology", "dermatology", "pediatrics", "neurology",
            "orthopedics", "gynecology", "oncology", "psychiatry",
            "ophthalmology", "ent", "general medicine", "general practice"
    };

    private IntakeResult offlineExtraction(String transcript, Long knownPatientId) {
        String translated = translate(transcript);
        boolean bangla = isBangla(transcript);
        String lower = translated.toLowerCase(Locale.ROOT);

        IntakeResult out = new IntakeResult();
        out.sourceLanguage = bangla ? "bn" : "en";
        out.translatedText = bangla ? translated : null;
        out.fromAgenticAi = false;

        // Time
        Matcher mTime = TIME.matcher(lower);
        if (mTime.find()) {
            try {
                int hour = Integer.parseInt(mTime.group(1));
                int minute = mTime.group(2) != null ? Integer.parseInt(mTime.group(2)) : 0;
                String suffix = mTime.group(3) != null
                        ? mTime.group(3).toLowerCase(Locale.ROOT).replace(".", "") : "";
                if (suffix.startsWith("p") && hour < 12) hour += 12;
                if (suffix.startsWith("a") && hour == 12) hour = 0;
                if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                    out.preferredTime = LocalTime.of(hour, minute);
                }
            } catch (NumberFormatException ignore) { /* fall through */ }
        }

        // Date
        Matcher mDow = DOW.matcher(lower);
        if (mDow.find()) {
            String token = mDow.group(1).toLowerCase(Locale.ROOT);
            LocalDate today = LocalDate.now();
            if ("today".equals(token)) {
                out.appointmentDate = today;
            } else if ("tomorrow".equals(token)) {
                out.appointmentDate = today.plusDays(1);
            } else {
                String[] names = {"sunday", "monday", "tuesday", "wednesday",
                        "thursday", "friday", "saturday"};
                int targetIdx = -1;
                for (int i = 0; i < names.length; i++) {
                    if (names[i].startsWith(token) || token.startsWith(names[i].substring(0, 3))) {
                        targetIdx = i;
                        break;
                    }
                }
                if (targetIdx >= 0) {
                    int todayIdx = today.getDayOfWeek().getValue() % 7;
                    int delta = (targetIdx - todayIdx + 7) % 7;
                    if (delta == 0) delta = 7;
                    out.appointmentDate = today.plusDays(delta);
                }
            }
        }

        // Specialization
        for (String spec : OFFLINE_SPECIALIZATIONS) {
            if (lower.contains(spec)) {
                out.specialization = spec;
                break;
            }
        }

        // Doctor name — look at the ORIGINAL transcript so "Dr. Smith" survives even if the
        // Bangla token map didn't surface it. Transliterate Bangla names so the
        // JS can do a substring match against the Latin-script doctor list.
        Matcher mDoc = DOCTOR_NAME.matcher(transcript);
        if (mDoc.find()) {
            String captured = mDoc.group(1).trim();
            // If the captured name is in Bengali script, transliterate it to
            // Latin so the JS substring-matcher can find "Shamsul" inside
            // "Dr. Md. Shamsul Haque". Already-Latin names pass through.
            out.doctorName = containsBangla(captured)
                    ? transliterate(captured)
                    : captured;
        }

        // Hospital name — scan the translated text for any phrase that
        // looks like a hospital (anchors like "Hospital", "Clinic",
        // "Medical Center"). Captures 1-3 words before the anchor so
        // "Labaid Specialized Hospital" survives intact. The DB-aware
        // matcher in VoiceIntakeService will resolve this to a hospitalId.
        out.hospitalName = extractHospitalName(translated);

        // Reason
        Matcher mReason = REASON.matcher(transcript);
        if (mReason.find()) {
            out.reasonForVisit = mReason.group(1).trim();
        }

        // Phone
        Matcher mPhone = PHONE.matcher(transcript);
        if (mPhone.find()) {
            out.contactNumber = mPhone.group(1).replaceAll("[\\s\\-]", "").trim();
        }

        // Missing-field logic — mirrors the agentic path so the UI behaves identically.
        if (out.appointmentDate == null) {
            out.missingFields.add("appointmentDate");
            out.clarifyingQuestions.add("Which day would you like the appointment? "
                    + "For example: today, tomorrow, or a specific date.");
        }
        if (out.preferredTime == null) {
            out.clarifyingQuestions.add("What time of day would you prefer? "
                    + "(Optional — leave blank and we'll auto-assign the next open slot.)");
        }
        if (out.contactNumber == null && knownPatientId == null) {
            out.missingFields.add("contactNumber");
            out.clarifyingQuestions.add("What's a phone number we can reach you on?");
        }
        if (out.doctorName == null && out.specialization == null) {
            out.missingFields.add("doctorOrSpecialization");
            out.clarifyingQuestions.add("Which doctor or specialization do you need?");
        }

        return out;
    }

    /**
     * Phrase -> English (offline fallback only). Order matters — longer
     * phrases first so "আগামীকাল সকালে" matches "tomorrow morning" before
     * stopping at "আগামীকাল" -> "tomorrow".
     */
    private static final Map<String, String> PHRASES = new LinkedHashMap<>();
    static {
        // Intent
        PHRASES.put("আমার একটা অ্যাপয়েন্টমেন্ট দরকার", "I need an appointment");
        PHRASES.put("আমার একটি অ্যাপয়েন্টমেন্ট দরকার", "I need an appointment");
        PHRASES.put("আমি অ্যাপয়েন্টমেন্ট নিতে চাই", "I want to book an appointment");
        PHRASES.put("আমি একটা অ্যাপয়েন্টমেন্ট বুক করতে চাই", "I want to book an appointment");
        PHRASES.put("আমাকে একটা অ্যাপয়েন্টমেন্ট দিন", "please give me an appointment");
        PHRASES.put("আমি ডাক্তারের সাথে দেখা করতে চাই", "I want to see a doctor");
        PHRASES.put("আমি ডাক্তার দেখাতে চাই", "I want to see a doctor");

        // Time-of-day
        PHRASES.put("আজ সকালে", "this morning");
        PHRASES.put("আজ বিকালে", "this afternoon");
        PHRASES.put("আজ রাতে", "tonight");
        PHRASES.put("আগামীকাল সকালে", "tomorrow morning");
        PHRASES.put("আগামীকাল বিকালে", "tomorrow afternoon");
        PHRASES.put("আগামীকাল", "tomorrow");
        PHRASES.put("আজ", "today");
        PHRASES.put("পরশু", "day after tomorrow");

        // Days of week
        PHRASES.put("সোমবার", "monday");
        PHRASES.put("মঙ্গলবার", "tuesday");
        PHRASES.put("বুধবার", "wednesday");
        PHRASES.put("বৃহস্পতিবার", "thursday");
        PHRASES.put("শুক্রবার", "friday");
        PHRASES.put("শনিবার", "saturday");
        PHRASES.put("রবিবার", "sunday");

        // Common time-of-day buckets
        PHRASES.put("সকালে", "in the morning");
        PHRASES.put("বিকালে", "in the afternoon");
        PHRASES.put("সন্ধ্যায়", "in the evening");
        PHRASES.put("রাতে", "at night");

        // Specializations (English in parentheses — both the noun and its "specialist" form)
        PHRASES.put("হৃদরোগ বিশেষজ্ঞ", "cardiology");
        PHRASES.put("হৃদরোগ", "cardiology");
        PHRASES.put("হার্টের ডাক্তার", "cardiology");
        PHRASES.put("চর্মরোগ বিশেষজ্ঞ", "dermatology");
        PHRASES.put("চর্মরোগ", "dermatology");
        PHRASES.put("চামড়ার ডাক্তার", "dermatology");
        PHRASES.put("শিশু রোগ বিশেষজ্ঞ", "pediatrics");
        PHRASES.put("শিশু রোগ", "pediatrics");
        PHRASES.put("বাচ্চাদের ডাক্তার", "pediatrics");
        PHRASES.put("স্নায়ুরোগ বিশেষজ্ঞ", "neurology");
        PHRASES.put("স্নায়ুরোগ", "neurology");
        PHRASES.put("নিউরোলজিস্ট", "neurology");
        PHRASES.put("নিউরো মেডিসিন", "neurology");
        PHRASES.put("ব্রেইন টিম", "neurology");
        PHRASES.put("ব্রেইন ডাক্তার", "neurology");
        PHRASES.put("মস্তিষ্কের ডাক্তার", "neurology");
        PHRASES.put("অর্থোপেডিক", "orthopedics");
        PHRASES.put("হাড়ের ডাক্তার", "orthopedics");
        PHRASES.put("গাইনোকোলজিস্ট", "gynecology");
        PHRASES.put("নারীরোগ বিশেষজ্ঞ", "gynecology");
        PHRASES.put("মহিলা ডাক্তার", "gynecology");
        PHRASES.put("ক্যান্সার বিশেষজ্ঞ", "oncology");
        PHRASES.put("ক্যান্সারের ডাক্তার", "oncology");
        PHRASES.put("মানসিক রোগ বিশেষজ্ঞ", "psychiatry");
        PHRASES.put("মানসিক স্বাস্থ্য", "psychiatry");
        PHRASES.put("চক্ষু বিশেষজ্ঞ", "ophthalmology");
        PHRASES.put("চোখের ডাক্তার", "ophthalmology");
        PHRASES.put("নাক কান গলা", "ent");
        PHRASES.put("ই এন টি", "ent");
        PHRASES.put("নাকের ডাক্তার", "ent");
        PHRASES.put("কানের ডাক্তার", "ent");
        PHRASES.put("সাধারণ চিকিৎসক", "general medicine");
        PHRASES.put("সাধারণ ডাক্তার", "general medicine");
        PHRASES.put("কিডনির ডাক্তার", "nephrology");
        PHRASES.put("কিডনি বিশেষজ্ঞ", "nephrology");
        PHRASES.put("লিভারের ডাক্তার", "hepatology");
        PHRASES.put("লিভার বিশেষজ্ঞ", "hepatology");
        PHRASES.put("ডেন্টিস্ট", "dentistry");
        PHRASES.put("দাঁতের ডাক্তার", "dentistry");
        PHRASES.put("ইউরোলজিস্ট", "urology");
        PHRASES.put("ই এন টি বিশেষজ্ঞ", "ent");

        // Hospital name anchors — keep them in the translated text so the JS
        // substring matcher can match against server-side hospital records.
        PHRASES.put("হাসপাতালের", "Hospital");
        PHRASES.put("হাসপাতাল", "Hospital");
        PHRASES.put("ক্লিনিক", "Clinic");
        PHRASES.put("মেডিকেল সেন্টার", "Medical Center");
        PHRASES.put("মেডিসিন সেন্টার", "Medical Center");
        PHRASES.put("জেনারেল হাসপাতাল", "General Hospital");
        PHRASES.put("মেডিকেল কলেজ", "Medical College");
        PHRASES.put("মেডিকেল কলেজ হাসপাতাল", "Medical College Hospital");
        PHRASES.put("সিটি জেনারেল হাসপাতাল", "City General Hospital");
        PHRASES.put("ঢাকা মেডিকেল", "Dhaka Medical");
        PHRASES.put("বারডেম", "BIRDEM");
        PHRASES.put("স্কয়ার হাসপাতাল", "Square Hospital");
        PHRASES.put("এপোলো হাসপাতাল", "Apollo Hospital");
        PHRASES.put("ইউনাইটেড হাসপাতাল", "United Hospital");

        // Hospital name spelling variants - Bangla transliteration is loose
        // so the same hospital name comes back written many ways. Add the
        // common spellings so voice input maps to a real row in the DB.
        PHRASES.put("ল্যাবিড", "Labaid");
        PHRASES.put("ল্যাবেইড", "Labaid");
        PHRASES.put("ল্যাবাইড", "Labaid");
        PHRASES.put("ল্যাবেড", "Labaid");
        PHRASES.put("ল্যাব এইড", "Labaid");
        PHRASES.put("ল্যাব এইড হাসপাতাল", "Labaid Specialized Hospital");
        PHRASES.put("ল্যাবিড স্পেশালাইজড হাসপাতাল", "Labaid Specialized Hospital");
        PHRASES.put("ল্যাবিড হাসপাতাল", "Labaid Specialized Hospital");
        PHRASES.put("এভারকেয়ার", "Evercare");
        PHRASES.put("পপুলার ডায়াগনস্টিক", "Popular Diagnostic");
        PHRASES.put("পপুলার ডায়াগনস্টিক সেন্টার", "Popular Diagnostic Center");
        PHRASES.put("বারডেম জেনারেল হাসপাতাল", "BIRDEM General Hospital");
        PHRASES.put("ঢাকা মেডিকেল কলেজ", "Dhaka Medical College");
        PHRASES.put("ঢাকা মেডিকেল কলেজ হাসপাতাল", "Dhaka Medical College Hospital");
        PHRASES.put("ইব্রাহিম কার্ডিয়াক", "Ibrahim Cardiac");
        PHRASES.put("ন্যাশনাল হার্ট ফাউন্ডেশন", "National Heart Foundation");
        PHRASES.put("জাতীয় হৃদরোগ ইনস্টিটিউট", "National Heart Foundation");
        PHRASES.put("চট্টগ্রাম মেডিকেল", "Chittagong Medical");
        PHRASES.put("রাজশাহী মেডিকেল", "Rajshahi Medical");
        PHRASES.put("খুলনা মেডিকেল", "Khulna Medical");
        PHRASES.put("সিলেট এম এ জি ওসমানী মেডিকেল", "Sylhet MAG Osmani Medical College");
        PHRASES.put("রংপুর মেডিকেল", "Rangpur Medical");
        PHRASES.put("বরিশাল শের-ই-বাংলা মেডিকেল", "Barishal Sher-e-Bangla Medical College");
        PHRASES.put("ময়মনসিংহ মেডিকেল", "Mymensingh Medical");
        PHRASES.put("বঙ্গবন্ধু শেখ মুজিব মেডিকেল", "BSMMU");

        // Reasons / complaints
        PHRASES.put("জ্বর", "fever");
        PHRASES.put("কাশি", "cough");
        PHRASES.put("মাথা ব্যথা", "headache");
        PHRASES.put("পেট ব্যথা", "stomach pain");
        PHRASES.put("বুকে ব্যথা", "chest pain");
        PHRASES.put("শ্বাসকষ্ট", "breathing difficulty");
        PHRASES.put("ডায়রিয়া", "diarrhea");
        PHRASES.put("এলার্জি", "allergy");
        PHRASES.put("চুলকানি", "itching");
        PHRASES.put("ফলো আপ", "follow-up");
        PHRASES.put("চেকআপ", "checkup");
        PHRASES.put("টিকা", "vaccination");
        PHRASES.put("ভ্যাকসিন", "vaccination");

        // Connecting words
        PHRASES.put("আমার জন্য", "for me");
        PHRASES.put("তার জন্য", "for him");
        PHRASES.put("দয়া করে", "please");
        PHRASES.put("একটু", "a little");
        PHRASES.put("সম্ভব হলে", "if possible");
    }

    /** Single-token mapping. Lower-cased Bengali word -> English. */
    private static final Map<String, String> WORDS = new LinkedHashMap<>();
    static {
        WORDS.put("ডাক্তার", "doctor");
        WORDS.put("ডাক্তারের", "doctor");
        WORDS.put("ডাক্তারকে", "doctor");
        WORDS.put("স্যার", "doctor");
        WORDS.put("ম্যাডাম", "doctor");

        // Body parts / specializations as standalone tokens - so "ব্রেইন"
        // alone (without "টিম") still maps to neurology.
        WORDS.put("ব্রেইন", "neurology");
        WORDS.put("মস্তিষ্ক", "neurology");
        WORDS.put("হার্ট", "cardiology");
        WORDS.put("হৃদয়", "cardiology");
        WORDS.put("কিডনি", "nephrology");
        WORDS.put("লিভার", "hepatology");
        WORDS.put("যকৃত", "hepatology");
        WORDS.put("চোখ", "ophthalmology");
        WORDS.put("কান", "ent");
        WORDS.put("নাক", "ent");
        WORDS.put("গলা", "ent");
        WORDS.put("দাঁত", "dentistry");
        WORDS.put("হাড়", "orthopedics");
        WORDS.put("চামড়া", "dermatology");
        WORDS.put("ত্বক", "dermatology");
        WORDS.put("মহিলা", "gynecology");
        WORDS.put("গর্ভবতী", "gynecology");
        WORDS.put("শিশু", "pediatrics");
        WORDS.put("বাচ্চা", "pediatrics");
        WORDS.put("বৃদ্ধ", "general medicine");
        WORDS.put("মানসিক", "psychiatry");

        // Hospital name tokens — when a hospital name is split into
        // multiple words (e.g. "City General Hospital"), each word ends up
        // as its own token; surfacing these in English lets the JS
        // substring matcher pick up the right row.
        WORDS.put("জেনারেল", "General");
        WORDS.put("সিটি", "City");
        WORDS.put("মেডিকেল", "Medical");
        WORDS.put("হাসপাতাল", "Hospital");
        WORDS.put("ক্লিনিক", "Clinic");
        WORDS.put("সেন্টার", "Center");
        WORDS.put("কলেজ", "College");
        WORDS.put("ইনস্টিটিউট", "Institute");

        WORDS.put("আমি", "I");
        WORDS.put("আমার", "my");
        WORDS.put("আমাকে", "me");

        WORDS.put("দেখা", "see");
        WORDS.put("দেখাতে", "see");
        WORDS.put("করতে", "want to");
        WORDS.put("চাই", "want");
        WORDS.put("বুক", "book");
        WORDS.put("নিতে", "book");
        WORDS.put("দরকার", "need");
        WORDS.put("প্রয়োজন", "need");
        WORDS.put("এবং", "and");
        WORDS.put("এর", "of");
        WORDS.put("জন্য", "for");
        WORDS.put("সাথে", "with");
        WORDS.put("সঙ্গে", "with");
        WORDS.put("থেকে", "from");
        WORDS.put("এ", "this");
        WORDS.put("ও", "and");
        WORDS.put("না", "no");
        WORDS.put("হ্যাঁ", "yes");

        WORDS.put("এখন", "now");
        WORDS.put("পরে", "later");
        WORDS.put("আগে", "before");
        WORDS.put("সকাল", "morning");
        WORDS.put("দুপুর", "noon");
        WORDS.put("বিকেল", "afternoon");
        WORDS.put("সন্ধ্যা", "evening");
        WORDS.put("রাত", "night");

        WORDS.put("অ্যাপয়েন্টমেন্ট", "appointment");
        WORDS.put("ভিজিট", "visit");
    }

    /**
     * Translate a Bengali transcript to English (rule-based, offline).
     * Kept for the offline fallback path; the agentic AI path doesn't call this.
     */
    public String translate(String banglaOrMixed) {
        if (banglaOrMixed == null || banglaOrMixed.isBlank()) {
            return "";
        }
        String trimmed = banglaOrMixed.trim();

        if (!containsBangla(trimmed)) {
            return trimmed;
        }

        String lower = trimmed.toLowerCase(Locale.ROOT);
        String result = lower;
        for (Map.Entry<String, String> e : PHRASES.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }

        StringBuilder sb = new StringBuilder();
        for (String token : result.split("(?<=\\s)|(?=\\s)")) {
            if (containsBangla(token)) {
                sb.append(translateToken(token));
            } else {
                sb.append(token);
            }
        }
        String out = sb.toString().replaceAll("\\s+", " ").trim();

        // Final pass: transliterate any remaining Bengali characters that
        // survived phrase + token translation (proper nouns, names, the
        // parts of hospital names not in the phrase map). This is what
        // lets "ল্যাবিট" become "labaid" when "ল্যাবিড" wasn't in the
        // phrase dictionary, and lets the JS substring-match the doctor's
        // Latin name.
        if (containsBangla(out)) {
            out = transliterate(out);
        }
        return out;
    }

    /**
     * Pull a hospital-style phrase out of the (already-translated) text.
     * Anchors on English hospital nouns ("Hospital", "Clinic", "Medical
     * Center", "Diagnostic Center") and returns the 1-3 words before it.
     * Used by the offline path so {@link IntakeResult#hospitalName} is
     * populated even when no LLM is available — the VoiceIntakeService
     * post-processing then resolves it to a real {@code hospitalId}.
     */
    private static final Pattern HOSPITAL_ANCHOR = Pattern.compile(
            "\\b([A-Za-z][A-Za-z'\\-]+(?:\\s+[A-Za-z][A-Za-z'\\-]+){0,3})"
                    + "\\s+(Hospital|Clinic|Medical\\s+Center|Diagnostic\\s+Center|Medical\\s+College"
                    + "|General\\s+Hospital|Specialized\\s+Hospital|Cardiac\\s+Center)\\b",
            Pattern.CASE_INSENSITIVE);

    private static String extractHospitalName(String translated) {
        if (translated == null || translated.isBlank()) return null;
        Matcher m = HOSPITAL_ANCHOR.matcher(translated);
        if (m.find()) {
            // Capture group 1 holds the 1-3 leading words; group 2 holds
            // the anchor itself ("Hospital", "Medical Center", ...). Stitch
            // them back together so the full canonical name is preserved.
            return (m.group(1) + " " + m.group(2)).replaceAll("\\s+", " ").trim();
        }
        return null;
    }

    private String translateToken(String token) {
        String t = BANGLA_DIGIT.matcher(token).replaceAll(m -> {
            char c = m.group().charAt(0);
            return String.valueOf((char) ('0' + (c - '০')));
        });
        String stripped = t.replaceAll("[^\\p{InBengali}]", "").trim();
        if (stripped.isEmpty()) {
            return t;
        }
        String mapped = WORDS.get(stripped.toLowerCase(Locale.ROOT));
        if (mapped != null) {
            return t.replace(stripped, mapped);
        }
        return t;
    }

    private static boolean containsBangla(String s) {
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (cp >= 0x0980 && cp <= 0x09FF) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static IntakeResult emptyResult() {
        IntakeResult r = new IntakeResult();
        r.missingFields.add("transcript");
        r.clarifyingQuestions.add("I didn't catch what you said. Could you repeat your request?");
        return r;
    }
}
