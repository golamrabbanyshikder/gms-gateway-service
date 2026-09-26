package com.gms.gateway.service;

import com.gms.gateway.dto.AppointmentRequestDto;
import com.gms.gateway.dto.DoctorDto;
import com.gms.gateway.dto.HospitalDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Voice-intake orchestrator. Hands the raw browser transcript to
 * {@link BanglaToEnglishTranslator}, which — when an
 * {@code ANTHROPIC_API_KEY} is configured — performs translation AND
 * structured-field extraction in a single agentic AI call.
 *
 * <p>The translator falls back to the rule-based pipeline automatically if
 * no API key is set or the LLM call fails. This service therefore only
 * maps the result onto the {@link AppointmentRequestDto} the booking
 * controller expects and assembles the {@link ExtractionResult} the
 * controller returns to the frontend.</p>
 */
@Service
public class VoiceIntakeService {

    private static final Logger logger = LoggerFactory.getLogger(VoiceIntakeService.class);

    @Autowired
    private BanglaToEnglishTranslator translator;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public enum ExtractionStatus {
        COMPLETE,            // all required fields present
        MISSING_FIELDS,      // some required fields couldn't be extracted
        AMBIGUOUS            // multiple interpretations possible
    }

    public static class ExtractionResult {
        public AppointmentRequestDto fields;
        /**
         * Typed structured extraction (doctorName, specialization, hospitalName,
         * appointmentDate, preferredTime, reasonForVisit, contactNumber,
         * contactEmail, sourceLanguage). Frontend uses this directly to
         * populate form fields without parsing pipe-delimited reasonForVisit
         * strings - the senior-fullstack REST contract.
         */
        public BanglaToEnglishTranslator.IntakeResult extracted;
        public ExtractionStatus status;
        public List<String> missingFields;
        public List<String> clarifyingQuestions;
        public String sourceLanguage;
        public boolean fromAgenticAi;

        public ExtractionResult(AppointmentRequestDto fields,
                                BanglaToEnglishTranslator.IntakeResult extracted,
                                ExtractionStatus status,
                                List<String> missingFields, List<String> clarifyingQuestions,
                                String sourceLanguage, boolean fromAgenticAi) {
            this.fields = fields;
            this.extracted = extracted;
            this.status = status;
            this.missingFields = missingFields;
            this.clarifyingQuestions = clarifyingQuestions;
            this.sourceLanguage = sourceLanguage;
            this.fromAgenticAi = fromAgenticAi;
        }
    }

    /**
     * Run the agentic (or offline) intake over the transcript and project
     * the result onto an {@link AppointmentRequestDto} for the booking
     * controller. {@code knownPatientId} is forwarded to the translator so
     * the LLM doesn't waste tokens re-asking for a name/contact that's
     * already on file.
     */
    public ExtractionResult extract(String transcript, Long knownPatientId, String knownPatientName) {
        return extract(transcript, knownPatientId, knownPatientName, List.of(), List.of());
    }

    /**
     * DB-aware extraction. Pass the current hospital and doctor catalog so the
     * agent can resolve a spoken name ("ল্যাবিটের শামসুল হক") against real
     * rows and return matched IDs. Falls back to no-context extraction when
     * either list is null or empty.
     */
    public ExtractionResult extract(String transcript, Long knownPatientId, String knownPatientName,
                                    Collection<HospitalDto> hospitals, Collection<DoctorDto> doctors) {
        if (transcript == null || transcript.isBlank()) {
            AppointmentRequestDto empty = new AppointmentRequestDto();
            BanglaToEnglishTranslator.IntakeResult emptyIntake = new BanglaToEnglishTranslator.IntakeResult();
            emptyIntake.sourceLanguage = "unknown";
            return new ExtractionResult(
                    empty,
                    emptyIntake,
                    ExtractionStatus.MISSING_FIELDS,
                    List.of("patientName", "doctorOrSpecialization", "appointmentDate", "contactNumber"),
                    List.of(
                            "I didn't catch what you said. Could you repeat your request?",
                            "You can also switch to typing the request instead."),
                    "unknown",
                    false);
        }

        BanglaToEnglishTranslator.IntakeResult intake =
                translator.translateAndExtract(transcript, knownPatientId, hospitals, doctors);

        // DB-aware post-processing: when the agentic AI path ran without the
        // catalog (no API key) or returned null IDs, resolve the extracted
        // names against the live hospital/doctor lists so the form gets
        // exact-row IDs instead of fuzzy free-text. Without this the offline
        // regex path would leave hospitalId/doctorId null even though the
        // names are present.
        resolveAgainstCatalog(intake, hospitals, doctors);

        AppointmentRequestDto fields = intake.toAppointmentRequestDto();

        if (knownPatientId != null) {
            fields.setPatientId(knownPatientId);
        }

        // Defensive: only complain about a missing phone when we have no
        // known patient — a logged-in user gets their contact pulled from
        // their patient record by the controller after this returns.
        List<String> missing = new ArrayList<>(intake.missingFields);
        List<String> questions = new ArrayList<>(intake.clarifyingQuestions);

        if (knownPatientId == null
                && (fields.getContactNumber() == null || fields.getContactNumber().isBlank())
                && !missing.contains("contactNumber")) {
            missing.add("contactNumber");
            questions.add("What's a phone number we can reach you on?");
        }

        // If the agentic AI didn't surface a doctor/specialization and the
        // caller didn't already pick one, ask.
        if ((fields.getReasonForVisit() == null || !fields.getReasonForVisit().toLowerCase().contains("specialization"))
                && (fields.getReasonForVisit() == null || !fields.getReasonForVisit().toLowerCase().contains("doctor"))
                && !missing.contains("doctorOrSpecialization")
                && intake.doctorName == null && intake.specialization == null) {
            missing.add("doctorOrSpecialization");
            questions.add("Which doctor or specialization do you need?");
        }

        ExtractionStatus status = missing.isEmpty()
                ? ExtractionStatus.COMPLETE
                : ExtractionStatus.MISSING_FIELDS;

        if (intake.fromAgenticAi) {
            logger.debug("Agentic AI extraction complete: source={}, missing={}, "
                            + "appointmentDate={}, preferredTime={}, specialization={}, doctorName={}, "
                            + "hospitalId={}, doctorId={}",
                    intake.sourceLanguage, missing,
                    intake.appointmentDate, intake.preferredTime,
                    intake.specialization, intake.doctorName,
                    intake.hospitalId, intake.doctorId);
        }

        return new ExtractionResult(fields, intake, status, missing, questions,
                intake.sourceLanguage, intake.fromAgenticAi);
    }

    /** Whether the original transcript was Bangla (used for the UI badge). */
    public boolean wasTranslated(String transcript) {
        return translator.wasTranslated(transcript);
    }

    /** Builds a friendly human-readable confirmation sentence for the success page. */
    public String formatConfirmation(AppointmentRequestDto request) {
        StringBuilder sb = new StringBuilder("Appointment booked");
        if (request.getAppointmentDate() != null) {
            sb.append(" on ").append(request.getAppointmentDate().format(DATE_FMT));
        }
        if (request.getPreferredTime() != null) {
            sb.append(" at ").append(request.getPreferredTime());
        }
        sb.append(".");
        if (request.getReasonForVisit() != null) {
            sb.append(" Reason: ").append(request.getReasonForVisit()).append(".");
        }
        return sb.toString();
    }

    // =====================================================================
    // DB-aware post-processing
    // =====================================================================

    /**
     * Resolve free-text hospital / doctor names against the live catalog so
     * the intake result carries exact-row {@code hospitalId} and
     * {@code doctorId} values — same payload shape the agentic AI path
     * produces. Runs the same matching strategy the JS uses
     * (findDoctorByName + applyHospital) but server-side, so the offline
     * regex path benefits from the DB context too.
     *
     * <p>Order matters: hospital is resolved first so a "any cardiologist
     * at Square Hospital" request narrows the doctor pool to that hospital
     * when picking by specialization.</p>
     */
    private static void resolveAgainstCatalog(BanglaToEnglishTranslator.IntakeResult intake,
                                                Collection<HospitalDto> hospitals,
                                                Collection<DoctorDto> doctors) {
        if (intake == null) return;
        boolean noCatalog = (hospitals == null || hospitals.isEmpty())
                && (doctors == null || doctors.isEmpty());
        if (noCatalog) return;

        // 1. Hospital — match by name (substring + token overlap).
        if (intake.hospitalId == null && intake.hospitalName != null && !intake.hospitalName.isBlank()) {
            Long hid = matchHospitalByName(intake.hospitalName, hospitals);
            if (hid != null) intake.hospitalId = hid;
        }

        // 2. Doctor — match by name; narrow to the matched hospital when
        // available so "Shamsul Haque" at Labaid resolves uniquely even if
        // a duplicate name exists elsewhere.
        if (intake.doctorId == null && intake.doctorName != null && !intake.doctorName.isBlank()) {
            Long did = matchDoctorByName(intake.doctorName, intake.hospitalId, doctors);
            if (did != null) intake.doctorId = did;
        }

        // 3. Specialization — last-resort: pick the first doctor at the
        // matched hospital whose specialization contains the token. Mirrors
        // the JS handler's "if no doctor by name, try specialization".
        if (intake.doctorId == null && intake.specialization != null && !intake.specialization.isBlank()) {
            Long did = matchDoctorBySpecialization(intake.specialization, intake.hospitalId, doctors);
            if (did != null) intake.doctorId = did;
        }
    }

    private static Long matchHospitalByName(String wanted, Collection<HospitalDto> hospitals) {
        if (wanted == null || hospitals == null || hospitals.isEmpty()) return null;
        String norm = wanted.toLowerCase(Locale.ROOT).trim();
        if (norm.isEmpty()) return null;

        // Pass 1: exact match.
        for (HospitalDto h : hospitals) {
            String hn = h.getHospitalName();
            if (hn != null && hn.toLowerCase(Locale.ROOT).equals(norm)) {
                return h.getHospitalId();
            }
        }

        // Pass 2: bidirectional substring — handles partial transcripts
        // like "labaid" matching "Labaid Specialized Hospital".
        for (HospitalDto h : hospitals) {
            String hn = h.getHospitalName();
            if (hn == null) continue;
            String lower = hn.toLowerCase(Locale.ROOT);
            if (lower.contains(norm) || norm.contains(lower)) {
                return h.getHospitalId();
            }
        }

        // Pass 3: token-level fuzzy match. Score = sum of matched token
        // lengths (longer tokens weighted higher). Threshold of 3 prevents
        // single-letter noise matches.
        String[] wantedTokens = norm.split("\\s+");
        Long bestId = null;
        int bestScore = 0;
        for (HospitalDto h : hospitals) {
            String hn = h.getHospitalName();
            if (hn == null) continue;
            String lower = hn.toLowerCase(Locale.ROOT);
            int score = 0;
            for (String t : wantedTokens) {
                if (t.length() >= 2 && lower.contains(t)) {
                    score += t.length();
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestId = h.getHospitalId();
            }
        }
        return bestScore >= 3 ? bestId : null;
    }

    private static Long matchDoctorByName(String wanted, Long hospitalIdFilter,
                                          Collection<DoctorDto> doctors) {
        if (wanted == null || doctors == null || doctors.isEmpty()) return null;
        String norm = wanted.toLowerCase(Locale.ROOT).trim();
        if (norm.isEmpty()) return null;

        // When a hospital filter is present, scope the candidate list to
        // that hospital first. Fall back to the full list when no match is
        // found in the filtered pool so we don't drop a true match that
        // happens to be at a different hospital.
        List<DoctorDto> filtered = filterByHospital(doctors, hospitalIdFilter);
        Long hit = matchDoctorInList(norm, filtered);
        if (hit != null) return hit;
        if (hospitalIdFilter != null) {
            return matchDoctorInList(norm, new ArrayList<>(doctors));
        }
        return null;
    }

    private static Long matchDoctorBySpecialization(String wanted, Long hospitalIdFilter,
                                                     Collection<DoctorDto> doctors) {
        if (wanted == null || doctors == null || doctors.isEmpty()) return null;
        String norm = wanted.toLowerCase(Locale.ROOT).trim();
        if (norm.isEmpty()) return null;

        List<DoctorDto> pool = filterByHospital(doctors, hospitalIdFilter);
        for (DoctorDto d : pool) {
            String spec = d.getSpecialization();
            if (spec != null && spec.toLowerCase(Locale.ROOT).contains(norm)) {
                return d.getDoctorId();
            }
        }
        return null;
    }

    private static List<DoctorDto> filterByHospital(Collection<DoctorDto> doctors, Long hospitalId) {
        if (hospitalId == null) return new ArrayList<>(doctors);
        List<DoctorDto> out = new ArrayList<>();
        for (DoctorDto d : doctors) {
            if (hospitalId.equals(d.getHospitalId())) {
                out.add(d);
            }
        }
        return out;
    }

    private static Long matchDoctorInList(String norm, List<DoctorDto> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;

        // Pass 1: full substring match — server name contained in doctor name.
        for (DoctorDto d : candidates) {
            String dn = d.getName();
            if (dn != null && dn.toLowerCase(Locale.ROOT).contains(norm)) {
                return d.getDoctorId();
            }
        }

        // Pass 2: token-level match — split both sides on whitespace and
        // score overlap. Mirrors findDoctorByName in new.html so behaviour
        // is consistent offline and agentic.
        String[] wantTokens = norm.split("\\s+");
        if (wantTokens.length == 0) return null;
        Long bestId = null;
        int bestScore = 0;
        for (DoctorDto d : candidates) {
            String dn = d.getName();
            if (dn == null) continue;
            String lower = dn.toLowerCase(Locale.ROOT);
            int score = 0;
            for (String t : wantTokens) {
                if (t.length() >= 2 && lower.contains(t)) {
                    score += t.length();
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestId = d.getDoctorId();
            }
        }
        return bestScore >= 3 ? bestId : null;
    }
}
