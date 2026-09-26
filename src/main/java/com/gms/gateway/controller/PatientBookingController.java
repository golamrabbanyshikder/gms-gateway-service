package com.gms.gateway.controller;

import com.gms.gateway.client.AuditClient;
import com.gms.gateway.client.BackendServiceClient;
import com.gms.gateway.dto.AppointmentDto;
import com.gms.gateway.dto.AppointmentRequestDto;
import com.gms.gateway.dto.AvailableSlotsResponseDto;
import com.gms.gateway.dto.DoctorDto;
import com.gms.gateway.dto.PatientDto;
import com.gms.gateway.entity.BookedBy;
import com.gms.gateway.entity.User;
import com.gms.gateway.repository.UserRepository;
import com.gms.gateway.service.AppointmentConfirmationService;
import com.gms.gateway.service.VoiceIntakeService;
import com.gms.gateway.service.WhisperTranscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Patient-side appointment booking inside the PATIENT-role portal. Mirror of
 * {@link BookingViewController} but every flow sets {@code bookedBy = PATIENT}
 * and resolves the patient from the logged-in user, never from a form
 * parameter — a patient can't book on someone else's behalf.
 */
@Controller
@RequestMapping("/patient/booking")
public class PatientBookingController {

    @Autowired
    private BackendServiceClient backendServiceClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditClient auditClient;

    @Autowired
    private VoiceIntakeService voiceIntakeService;

    @Autowired
    private WhisperTranscriptionService whisper;

    @Autowired
    private AppointmentConfirmationService confirmationService;

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "User";
    }

    private Optional<Long> currentPatientId() {
        return userRepository.findByUsername(currentUsername()).map(User::getPatientId);
    }

    @GetMapping
    public String landing(Model model) {
        if (currentPatientId().isEmpty()) return "redirect:/login?error";
        model.addAttribute("title", "My Appointments");
        model.addAttribute("pageTitle", "My Appointments");
        model.addAttribute("username", currentUsername());
        model.addAttribute("isPatient", true);
        model.addAttribute("view", "patient-booking/landing");
        return "layout";
    }

    @GetMapping("/new")
    public String newBookingForm(@RequestParam(required = false) Long doctorId,
                                  @RequestParam(required = false) String date,
                                  Model model) {
        Optional<Long> patientIdOpt = currentPatientId();
        if (patientIdOpt.isEmpty()) return "redirect:/login?error";

        model.addAttribute("title", "Book Appointment");
        model.addAttribute("pageTitle", "Book a New Appointment");
        model.addAttribute("username", currentUsername());
        model.addAttribute("isPatient", true);
        model.addAttribute("hospitals", backendServiceClient.getAllHospitals());
        model.addAttribute("doctors", backendServiceClient.getAllDoctors());
        model.addAttribute("preselectedDoctorId", doctorId);
        model.addAttribute("preselectedDate", date != null ? date : LocalDate.now().toString());
        model.addAttribute("view", "patient-booking/new");
        return "layout";
    }

    @PostMapping("/create")
    public String submitBooking(@RequestParam Long doctorId,
                                 @RequestParam String appointmentDate,
                                 @RequestParam(required = false) String preferredTime,
                                 @RequestParam(required = false) String reasonForVisit,
                                 @RequestParam(required = false) String voiceTranscript,
                                 HttpServletRequest request,
                                 Model model) {
        Optional<Long> patientIdOpt = currentPatientId();
        if (patientIdOpt.isEmpty()) return "redirect:/login?error";
        Long patientId = patientIdOpt.get();

        Optional<PatientDto> patientOpt = backendServiceClient.getPatientById(patientId);
        String contactNumber = patientOpt.map(PatientDto::getMobileNo).orElse(null);

        AppointmentRequestDto req = new AppointmentRequestDto();
        req.setPatientId(patientId);
        req.setDoctorId(doctorId);
        req.setAppointmentDate(LocalDate.parse(appointmentDate));
        if (preferredTime != null && !preferredTime.isBlank()) {
            req.setPreferredTime(LocalTime.parse(preferredTime));
        }
        req.setReasonForVisit(reasonForVisit);
        req.setContactNumber(contactNumber);
        req.setBookedBy(BookedBy.PATIENT);
        req.setBookedByUsername(currentUsername());
        req.setVoiceTranscript(voiceTranscript);

        Optional<AppointmentDto> booked = backendServiceClient.bookAppointment(req);
        if (booked.isPresent()) {
            auditClient.log(null, currentUsername(), "APPOINTMENT_BOOKED", "Appointment",
                    booked.get().getAppointmentId(),
                    "Patient self-booked appointment with doctor " + doctorId
                            + " on " + appointmentDate, request.getRemoteAddr());
            confirmationService.notifyConfirmation(booked.get(), currentUsername());
            return "redirect:/patient/booking/confirm/" + booked.get().getAppointmentId();
        }

        model.addAttribute("title", "Book Appointment");
        model.addAttribute("pageTitle", "Book a New Appointment");
        model.addAttribute("username", currentUsername());
        model.addAttribute("isPatient", true);
        model.addAttribute("doctors", backendServiceClient.getAllDoctors());
        model.addAttribute("preselectedDoctorId", doctorId);
        model.addAttribute("preselectedDate", appointmentDate);
        model.addAttribute("error", "Could not book the appointment. The slot may be taken, "
                + "or the doctor may not be working that day. Please pick another time.");
        model.addAttribute("view", "patient-booking/new");
        return "layout";
    }

    @GetMapping("/confirm/{id}")
    public String confirmBooking(@PathVariable Long id, Model model) {
        Optional<Long> patientIdOpt = currentPatientId();
        if (patientIdOpt.isEmpty()) return "redirect:/login?error";
        Long patientId = patientIdOpt.get();

        Optional<AppointmentDto> appointmentOpt = backendServiceClient.getAppointmentById(id);
        if (appointmentOpt.isEmpty() || !patientId.equals(appointmentOpt.get().getPatientId())) {
            return "redirect:/patient/booking";
        }

        AppointmentDto appt = appointmentOpt.get();
        model.addAttribute("title", "Appointment Confirmed");
        model.addAttribute("pageTitle", "Appointment Confirmed");
        model.addAttribute("username", currentUsername());
        model.addAttribute("isPatient", true);
        model.addAttribute("appointment", appt);
        backendServiceClient.getDoctorById(appt.getDoctorId()).ifPresent(d -> model.addAttribute("doctorName", d.getName()));
        backendServiceClient.getHospitalById(appt.getHospitalId()).ifPresent(h -> model.addAttribute("hospitalName", h.getHospitalName()));
        model.addAttribute("view", "patient-booking/confirm");
        return "layout";
    }

    @GetMapping("/slots")
    @ResponseBody
    public Map<String, Object> slotsForDoctorAndDate(@RequestParam Long doctorId,
                                                     @RequestParam String date) {
        Map<String, Object> response = new HashMap<>();
        Optional<AvailableSlotsResponseDto> slots = backendServiceClient.getAvailableSlots(doctorId, date);
        if (slots.isEmpty()) {
            response.put("available", false);
            response.put("message", "No availability data returned. The doctor may not be scheduled on this day.");
            return response;
        }
        AvailableSlotsResponseDto s = slots.get();
        response.put("available", true);
        response.put("totalSlots", s.getTotalSlots());
        response.put("remainingSlots", s.getRemainingSlots());
        response.put("dayStartTime", s.getDayStartTime());
        response.put("dayEndTime", s.getDayEndTime());
        response.put("slots", s.getSlots());
        response.put("fullyBooked", s.getRemainingSlots() == 0);
        return response;
    }

    /**
     * Calendar highlight data for the patient-side date picker — same shape
     * as the staff endpoint, only the URL prefix differs.
     */
    @GetMapping("/availability")
    @ResponseBody
    public List<com.gms.gateway.dto.DoctorAvailabilityDateDto> availability(
            @RequestParam Long doctorId,
            @RequestParam(defaultValue = "14") int days) {
        return backendServiceClient.getDoctorAvailability(doctorId, days);
    }

    @PostMapping("/voice/extract")
    @ResponseBody
    public Map<String, Object> extractFromVoice(@RequestParam String transcript,
                                                 HttpServletRequest request) {
        Optional<Long> patientIdOpt = currentPatientId();
        Long patientId = patientIdOpt.orElse(null);

        // DB-aware extraction: pass the live hospital + doctor catalog so
        // the agentic AI can match "ল্যাবিটের শামসুল হক" to real rows
        // (hospitalId + doctorId) instead of returning free-text names that
        // the frontend then has to fuzzy-match.
        List<com.gms.gateway.dto.HospitalDto> hospitals = backendServiceClient.getAllHospitals();
        List<com.gms.gateway.dto.DoctorDto> doctors = backendServiceClient.getAllDoctors();

        VoiceIntakeService.ExtractionResult result =
                voiceIntakeService.extract(transcript, patientId, null, hospitals, doctors);

        if (patientId != null) {
            result.fields.setPatientId(patientId);
            backendServiceClient.getPatientById(patientId).ifPresent(p -> {
                if (result.fields.getContactNumber() == null) {
                    result.fields.setContactNumber(p.getMobileNo());
                }
            });
        }

        // Preserve the original transcript for audit (Bangla will round-trip
        // to backend-service as-is; reviewers see what the patient said).
        result.fields.setVoiceTranscript(transcript);

        return buildExtractionResponse(result, transcript);
    }

    /**
     * Audio upload path: patient records voice in the browser, MediaRecorder
     * produces a webm/opus blob, this endpoint runs Whisper STT → Claude
     * agentic extraction → returns the structured payload. Same response
     * shape as /voice/extract so the frontend can populate the form with one
     * shared code path.
     */
    @PostMapping(value = "/voice/transcribe", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> transcribePatientAudio(
            @RequestParam("audio") MultipartFile audio,
            HttpServletRequest request) {
        Map<String, Object> body = new HashMap<>();

        if (!whisper.isAvailable()) {
            body.put("status", "unavailable");
            body.put("message", "Server-side speech-to-text is not configured. "
                    + "Please type your request instead.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }

        WhisperTranscriptionService.Result transcription = whisper.transcribe(audio);
        if (transcription == null || transcription.transcript == null) {
            body.put("status", "error");
            body.put("message", "Could not transcribe the audio. Please try again "
                    + "or type your request instead.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }

        Optional<Long> patientIdOpt = currentPatientId();
        Long patientId = patientIdOpt.orElse(null);

        // DB-aware extraction (audio path) — same catalog passed in as the
        // text path so audio and text input are interchangeable.
        List<com.gms.gateway.dto.HospitalDto> hospitals = backendServiceClient.getAllHospitals();
        List<com.gms.gateway.dto.DoctorDto> doctors = backendServiceClient.getAllDoctors();

        VoiceIntakeService.ExtractionResult result =
                voiceIntakeService.extract(transcription.transcript, patientId, null, hospitals, doctors);

        if (patientId != null) {
            result.fields.setPatientId(patientId);
            backendServiceClient.getPatientById(patientId).ifPresent(p -> {
                if (result.fields.getContactNumber() == null) {
                    result.fields.setContactNumber(p.getMobileNo());
                }
            });
        }
        result.fields.setVoiceTranscript(transcription.transcript);

        Map<String, Object> response = buildExtractionResponse(result, transcription.transcript);
        response.put("language", transcription.language);
        return ResponseEntity.ok(response);
    }

    /**
     * Shared response builder for /voice/extract and /voice/transcribe. Same
     * shape so the frontend uses one handler regardless of the input channel.
     */
    private Map<String, Object> buildExtractionResponse(
            VoiceIntakeService.ExtractionResult result, String transcript) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", result.status.name());
        response.put("missingFields", result.missingFields);
        response.put("clarifyingQuestions", result.clarifyingQuestions);
        response.put("fields", result.fields);
        // Typed structured extraction — frontend reads doctorName,
        // specialization, hospitalName, appointmentDate, preferredTime,
        // reasonForVisit directly to populate the form. No pipe parsing.
        response.put("extracted", result.extracted);
        response.put("rawTranscript", transcript);
        response.put("translated", voiceIntakeService.wasTranslated(transcript));
        response.put("username", currentUsername());
        return response;
    }

    @GetMapping("/list")
    public String listMyAppointments(Model model) {
        Optional<Long> patientIdOpt = currentPatientId();
        if (patientIdOpt.isEmpty()) return "redirect:/login?error";
        Long patientId = patientIdOpt.get();

        List<AppointmentDto> appointments = backendServiceClient.getAppointmentsByPatient(patientId);
        Map<Long, String> doctorNames = new HashMap<>();
        for (AppointmentDto a : appointments) {
            if (!doctorNames.containsKey(a.getDoctorId())) {
                doctorNames.put(a.getDoctorId(),
                        backendServiceClient.getDoctorById(a.getDoctorId())
                                .map(DoctorDto::getName).orElse("Doctor #" + a.getDoctorId()));
            }
        }

        model.addAttribute("title", "My Appointments");
        model.addAttribute("pageTitle", "My Appointments");
        model.addAttribute("username", currentUsername());
        model.addAttribute("isPatient", true);
        model.addAttribute("appointments", appointments);
        model.addAttribute("doctorNames", doctorNames);
        model.addAttribute("view", "patient-booking/list");
        return "layout";
    }

    @PostMapping("/{id}/cancel")
    public String cancelBooking(@PathVariable Long id, HttpServletRequest request) {
        Optional<Long> patientIdOpt = currentPatientId();
        if (patientIdOpt.isEmpty()) return "redirect:/login?error";
        Optional<AppointmentDto> appt = backendServiceClient.getAppointmentById(id);
        if (appt.isPresent() && patientIdOpt.get().equals(appt.get().getPatientId())) {
            backendServiceClient.cancelAppointment(id);
            auditClient.log(null, currentUsername(), "APPOINTMENT_CANCELLED", "Appointment",
                    id, "Patient cancelled their appointment", request.getRemoteAddr());
        }
        return "redirect:/patient/booking/list";
    }
}
