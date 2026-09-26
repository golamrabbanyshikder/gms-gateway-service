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
 * Staff-facing booking screens. Same engine as the patient portal — only the
 * BookedBy tag and the entry point (receptionist typing into a form, or
 * dictating to a voice mic on a desk phone) differ.
 */
@Controller
@RequestMapping("/booking")
public class BookingViewController {

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

    @GetMapping
    public String landing(Model model) {
        model.addAttribute("title", "Appointments");
        model.addAttribute("pageTitle", "Appointment Booking");
        model.addAttribute("username", currentUsername());
        model.addAttribute("view", "booking/landing");
        return "layout";
    }

    @GetMapping("/new")
    public String newBookingForm(@RequestParam(required = false) Long patientId,
                                 @RequestParam(required = false) Long doctorId,
                                 @RequestParam(required = false) String date,
                                 Model model) {
        model.addAttribute("title", "New Appointment");
        model.addAttribute("pageTitle", "Book New Appointment");
        model.addAttribute("username", currentUsername());
        model.addAttribute("view", "booking/new");
        model.addAttribute("doctors", backendServiceClient.getAllDoctors());
        model.addAttribute("patients", backendServiceClient.getAllPatients());
        model.addAttribute("preselectedPatientId", patientId);
        model.addAttribute("preselectedDoctorId", doctorId);
        model.addAttribute("preselectedDate", date != null ? date : LocalDate.now().toString());
        return "layout";
    }

    @PostMapping("/create")
    public String submitBooking(@RequestParam Long patientId,
                                 @RequestParam Long doctorId,
                                 @RequestParam String appointmentDate,
                                 @RequestParam(required = false) String preferredTime,
                                 @RequestParam(required = false) String reasonForVisit,
                                 @RequestParam(required = false) String contactNumber,
                                 @RequestParam(required = false) String contactEmail,
                                 @RequestParam(required = false) String voiceTranscript,
                                 HttpServletRequest request,
                                 Model model) {
        AppointmentRequestDto req = new AppointmentRequestDto();
        req.setPatientId(patientId);
        req.setDoctorId(doctorId);
        req.setAppointmentDate(LocalDate.parse(appointmentDate));
        if (preferredTime != null && !preferredTime.isBlank()) {
            req.setPreferredTime(LocalTime.parse(preferredTime));
        }
        req.setReasonForVisit(reasonForVisit);
        req.setContactNumber(contactNumber);
        req.setContactEmail(contactEmail);
        req.setBookedBy(BookedBy.RECEPTIONIST);
        req.setBookedByUsername(currentUsername());
        req.setVoiceTranscript(voiceTranscript);

        Optional<AppointmentDto> booked = backendServiceClient.bookAppointment(req);
        if (booked.isPresent()) {
            auditClient.log(null, currentUsername(), "APPOINTMENT_BOOKED", "Appointment",
                    booked.get().getAppointmentId(),
                    "Booked appointment for patient " + patientId + " with doctor " + doctorId
                            + " on " + appointmentDate, request.getRemoteAddr());
            confirmationService.notifyConfirmation(booked.get(), currentUsername());
            return "redirect:/booking/confirm/" + booked.get().getAppointmentId();
        }

        // Backend reachable but the booking itself was rejected (slot taken,
        // outside working hours, etc.). Backend-service returns 409 in that
        // case and our RestTemplate client degrades to Optional.empty() — the
        // user sees the form again with a generic error. The real message is
        // on the backend-service logs.
        model.addAttribute("title", "New Appointment");
        model.addAttribute("pageTitle", "Book New Appointment");
        model.addAttribute("username", currentUsername());
        model.addAttribute("view", "booking/new");
        model.addAttribute("doctors", backendServiceClient.getAllDoctors());
        model.addAttribute("patients", backendServiceClient.getAllPatients());
        model.addAttribute("preselectedPatientId", patientId);
        model.addAttribute("preselectedDoctorId", doctorId);
        model.addAttribute("preselectedDate", appointmentDate);
        model.addAttribute("error", "Could not book the appointment. The slot may be taken, "
                + "or the doctor may not be working that day. Please pick another time.");
        return "layout";
    }

    @GetMapping("/confirm/{id}")
    public String confirmBooking(@PathVariable Long id, Model model) {
        Optional<AppointmentDto> appointment = backendServiceClient.getAppointmentById(id);
        if (appointment.isEmpty()) {
            return "redirect:/booking";
        }
        AppointmentDto appt = appointment.get();
        model.addAttribute("title", "Appointment Confirmed");
        model.addAttribute("pageTitle", "Appointment Confirmed");
        model.addAttribute("username", currentUsername());
        model.addAttribute("appointment", appt);
        backendServiceClient.getPatientById(appt.getPatientId()).ifPresent(p -> model.addAttribute("patientName", p.getFullName()));
        backendServiceClient.getDoctorById(appt.getDoctorId()).ifPresent(d -> model.addAttribute("doctorName", d.getName()));
        backendServiceClient.getHospitalById(appt.getHospitalId()).ifPresent(h -> model.addAttribute("hospitalName", h.getHospitalName()));
        model.addAttribute("view", "booking/confirm");
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
     * Calendar highlight data — returns the next N dates (default 14, max 60)
     * for the given doctor with per-date slot counts and a fully-booked flag.
     * Drives the date picker on the booking form.
     */
    @GetMapping("/availability")
    @ResponseBody
    public List<com.gms.gateway.dto.DoctorAvailabilityDateDto> availability(
            @RequestParam Long doctorId,
            @RequestParam(defaultValue = "14") int days) {
        return backendServiceClient.getDoctorAvailability(doctorId, days);
    }

    /**
     * Admin-only — update a doctor's schedule slot capacity. Body: {@code capacity=N}.
     */
    @PostMapping("/schedule/{scheduleId}/capacity")
    @ResponseBody
    public Map<String, Object> updateScheduleCapacity(@PathVariable Long scheduleId,
                                                      @RequestParam int capacity,
                                                      HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        boolean ok = backendServiceClient.updateScheduleCapacity(scheduleId, capacity);
        response.put("success", ok);
        if (ok) {
            auditClient.log(null, currentUsername(), "SCHEDULE_CAPACITY_CHANGED",
                    "DoctorSchedule", scheduleId,
                    "Set capacity to " + capacity, request.getRemoteAddr());
        } else {
            response.put("message", "Could not update schedule capacity. Check the value and try again.");
        }
        return response;
    }

    /**
     * Endpoint that the inline voice button on /booking/new calls with the
     * patient's spoken transcript (Bengali or English). Returns the extracted
     * structured fields plus any clarifying questions so the JS on the booking
     * form can populate patient/doctor/date/time/reason inputs in place and
     * prompt for anything still missing.
     */
    @PostMapping("/voice/extract")
    @ResponseBody
    public Map<String, Object> extractFromVoice(@RequestParam String transcript,
                                                 @RequestParam(required = false) Long patientId,
                                                 HttpServletRequest request) {
        // DB-aware extraction: pass the live hospital + doctor catalog so
        // the agentic AI can match spoken names against real rows and
        // return hospitalId/doctorId. Receptionists dictating into the
        // form get exact-row resolution instead of fuzzy substring matches.
        List<com.gms.gateway.dto.HospitalDto> hospitals = backendServiceClient.getAllHospitals();
        List<com.gms.gateway.dto.DoctorDto> doctors = backendServiceClient.getAllDoctors();

        VoiceIntakeService.ExtractionResult result =
                voiceIntakeService.extract(transcript, patientId, null, hospitals, doctors);

        // If a patientId was given, attach it to the request so the eventual
        // booking POST doesn't need to ask again.
        if (patientId != null) {
            result.fields.setPatientId(patientId);
            backendServiceClient.getPatientById(patientId).ifPresent(p -> {
                if (result.fields.getContactNumber() == null) {
                    result.fields.setContactNumber(p.getMobileNo());
                }
            });
        }

        // Preserve the original (possibly Bangla) transcript on the booking
        // for audit so reviewers can see what the caller actually said.
        result.fields.setVoiceTranscript(transcript);

        Map<String, Object> response = buildExtractionResponse(result, transcript);
        response.put("ip", request.getRemoteAddr());
        return response;
    }

    /**
     * Staff-side audio upload. Runs Whisper STT on the recorded audio, then
     * the same agentic extraction as the text endpoint. The frontend uses
     * one shared handler for both input channels.
     */
    @PostMapping(value = "/voice/transcribe", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> transcribeStaffAudio(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "patientId", required = false) Long patientId,
            HttpServletRequest request) {
        Map<String, Object> body = new HashMap<>();

        if (!whisper.isAvailable()) {
            body.put("status", "unavailable");
            body.put("message", "Server-side speech-to-text is not configured. "
                    + "Please type the request instead.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }

        WhisperTranscriptionService.Result transcription = whisper.transcribe(audio);
        if (transcription == null || transcription.transcript == null) {
            body.put("status", "error");
            body.put("message", "Could not transcribe the audio. Please try again "
                    + "or type the request instead.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }

        // DB-aware extraction (audio path) — same catalog as the text path.
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
        response.put("ip", request.getRemoteAddr());
        return ResponseEntity.ok(response);
    }

    /**
     * Shared response builder for /voice/extract and /voice/transcribe. Same
     * payload shape so the frontend uses one handler for both input paths.
     */
    private Map<String, Object> buildExtractionResponse(
            VoiceIntakeService.ExtractionResult result, String transcript) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", result.status.name());
        response.put("missingFields", result.missingFields);
        response.put("clarifyingQuestions", result.clarifyingQuestions);
        response.put("fields", result.fields);
        // Typed structured extraction (doctorName, specialization, hospitalName,
        // appointmentDate, preferredTime, reasonForVisit). Frontend reads these
        // directly to populate the form — no pipe-delimited string parsing.
        response.put("extracted", result.extracted);
        response.put("rawTranscript", transcript);
        response.put("translated", voiceIntakeService.wasTranslated(transcript));
        response.put("username", currentUsername());
        return response;
    }

    @GetMapping("/list")
    public String listBookings(@RequestParam(required = false) String filter,
                                Model model) {
        List<AppointmentDto> appointments;
        String title;
        if ("doctor".equals(filter)) {
            Long doctorId = userRepository.findByUsername(currentUsername())
                    .map(User::getDoctorId).orElse(null);
            appointments = doctorId != null
                    ? backendServiceClient.getAppointmentsByDoctor(doctorId)
                    : List.of();
            title = "My Appointments";
        } else {
            // Aggregate across all patients the receptionist has access to.
            // Receptionists don't have a patient list of their own, so show
            // the union of appointments tied to recent patients - simplest
            // approximation is to call /api/appointment once filtered by
            // each patient. For now, show all booked appointments by hitting
            // a doctor-by-doctor fetch (small N).
            appointments = List.of();
            title = "All Appointments";
            for (DoctorDto doctor : backendServiceClient.getAllDoctors()) {
                appointments.addAll(backendServiceClient.getAppointmentsByDoctor(doctor.getDoctorId()));
            }
            appointments.sort((a, b) -> {
                int byDate = b.getAppointmentDate().compareTo(a.getAppointmentDate());
                return byDate != 0 ? byDate : b.getStartTime().compareTo(a.getStartTime());
            });
        }

        Map<Long, String> doctorNames = new HashMap<>();
        Map<Long, String> patientNames = new HashMap<>();
        for (AppointmentDto a : appointments) {
            if (!doctorNames.containsKey(a.getDoctorId())) {
                doctorNames.put(a.getDoctorId(),
                        backendServiceClient.getDoctorById(a.getDoctorId())
                                .map(DoctorDto::getName).orElse("Doctor #" + a.getDoctorId()));
            }
            if (!patientNames.containsKey(a.getPatientId())) {
                patientNames.put(a.getPatientId(),
                        backendServiceClient.getPatientById(a.getPatientId())
                                .map(PatientDto::getFullName).orElse("Patient #" + a.getPatientId()));
            }
        }

        model.addAttribute("title", title);
        model.addAttribute("pageTitle", title);
        model.addAttribute("username", currentUsername());
        model.addAttribute("appointments", appointments);
        model.addAttribute("doctorNames", doctorNames);
        model.addAttribute("patientNames", patientNames);
        model.addAttribute("filter", filter);
        model.addAttribute("view", "booking/list");
        return "layout";
    }

    @PostMapping("/{id}/cancel")
    public String cancelBooking(@PathVariable Long id, HttpServletRequest request) {
        backendServiceClient.cancelAppointment(id);
        auditClient.log(null, currentUsername(), "APPOINTMENT_CANCELLED", "Appointment",
                id, "Cancelled appointment " + id, request.getRemoteAddr());
        return "redirect:/booking/list";
    }

    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id,
                                @RequestParam String status,
                                HttpServletRequest request) {
        backendServiceClient.updateAppointmentStatus(id, status);
        auditClient.log(null, currentUsername(), "APPOINTMENT_STATUS_CHANGED", "Appointment",
                id, "Set status to " + status, request.getRemoteAddr());
        return "redirect:/booking/list";
    }
}
