package com.gms.gateway.controller;

import com.gms.gateway.client.AuditClient;
import com.gms.gateway.client.BackendServiceClient;
import com.gms.gateway.client.BiometricServiceClient;
import com.gms.gateway.dto.BiometricDto;
import com.gms.gateway.dto.PatientDto;
import com.gms.gateway.dto.PrescriptionDto;
import com.gms.gateway.dto.ReportDto;
import com.gms.gateway.entity.Role;
import com.gms.gateway.entity.User;
import com.gms.gateway.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/patients")
public class PatientViewController {

    @Autowired
    private BackendServiceClient backendServiceClient;

    @Autowired
    private BiometricServiceClient biometricServiceClient;

    @Autowired
    private AuditClient auditClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "User";
    }

    // Mobile keyboards/IMEs (notably ones used for Bengali/Arabic input) can
    // silently insert invisible bidi marks or other stray characters around
    // digits a user types, which look identical to plain digits on screen but
    // fail backend-service's mobileNo @Pattern check. Stripping anything
    // outside the characters that pattern actually allows fixes this at the
    // source regardless of which invisible character caused it.
    private String sanitizeMobileNo(String mobileNo) {
        return mobileNo == null ? null : mobileNo.replaceAll("[^0-9+\\-() ]", "").trim();
    }

    /**
     * Reads the caller's role straight off the authorities populated by
     * JwtAuthenticationFilter (ROLE_<role>), consistent with how
     * SecurityConfig/@PreAuthorize already gate access elsewhere.
     */
    private boolean hasRole(String roleName) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        String target = "ROLE_" + roleName;
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (target.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    @GetMapping
    public String patientList(@RequestParam(required = false) String query, Model model) {
        model.addAttribute("title", "Patients");
        model.addAttribute("pageTitle", "Patient Management");
        model.addAttribute("username", currentUsername());

        model.addAttribute("isMyPatientsView", false);

        boolean fullAccess = hasRole("SUPER_ADMIN") || hasRole("HOSPITAL_ADMIN");
        if (fullAccess) {
            model.addAttribute("patients", backendServiceClient.getAllPatients());
            model.addAttribute("searchOnly", false);
            model.addAttribute("view", "patient/list");
            return "layout";
        }

        // DOCTOR / RECEPTIONIST: no full browsable list of every patient in the
        // system, but a search box to look up a specific patient by ID is always
        // available. DOCTOR additionally gets a default list of their own
        // patients (anyone they've prescribed to or filed a report for) instead
        // of an empty page when no search has been entered.
        model.addAttribute("searchOnly", true);
        model.addAttribute("query", query);

        if (query != null && !query.isBlank()) {
            String trimmed = query.trim();
            List<PatientDto> result;
            var byNationalId = backendServiceClient.getPatientByNationalId(trimmed);
            if (byNationalId.isPresent()) {
                result = List.of(byNationalId.get());
            } else {
                Long id = null;
                try {
                    id = Long.parseLong(trimmed);
                } catch (NumberFormatException ignored) {
                    // not a numeric id, leave id null
                }
                if (id != null) {
                    var byId = backendServiceClient.getPatientById(id);
                    result = byId.map(List::of).orElseGet(Collections::emptyList);
                } else {
                    result = Collections.emptyList();
                }
            }

            if (result.isEmpty()) {
                model.addAttribute("notFound", true);
            }
            model.addAttribute("patients", result);
            model.addAttribute("view", "patient/list");
            return "layout";
        }

        if (hasRole("DOCTOR")) {
            Long doctorId = userRepository.findByUsername(currentUsername())
                    .map(User::getDoctorId)
                    .orElse(null);
            if (doctorId != null) {
                model.addAttribute("patients", backendServiceClient.getPatientsByDoctorId(doctorId));
                model.addAttribute("isMyPatientsView", true);
                model.addAttribute("view", "patient/list");
                return "layout";
            }
        }

        model.addAttribute("patients", Collections.emptyList());
        model.addAttribute("view", "patient/list");
        return "layout";
    }

    @GetMapping("/create")
    public String createPatient(Model model) {
        model.addAttribute("title", "Register Patient");
        model.addAttribute("pageTitle", "Register New Patient");
        model.addAttribute("username", currentUsername());
        model.addAttribute("view", "patient/create");
        return "layout";
    }

    @PostMapping("/create")
    public String submitCreatePatient(@RequestParam String fullName,
                                       @RequestParam String nationalId,
                                       @RequestParam String dateOfBirth,
                                       @RequestParam String gender,
                                       @RequestParam String mobileNo,
                                       @RequestParam String bloodGroup,
                                       @RequestParam String address,
                                       @RequestParam(required = false) String portalPassword,
                                       @RequestParam(required = false) String biometricType,
                                       @RequestParam(value = "biometricFile", required = false) MultipartFile biometricFile,
                                       HttpServletRequest request,
                                       Model model) throws IOException {
        if (biometricFile == null || biometricFile.isEmpty()) {
            model.addAttribute("title", "Register Patient");
            model.addAttribute("pageTitle", "Register New Patient");
            model.addAttribute("username", currentUsername());
            model.addAttribute("error", "Face verification is required. Please capture the patient's face via camera before saving.");
            model.addAttribute("view", "patient/create");
            return "layout";
        }

        PatientDto patient = new PatientDto();
        patient.setFullName(fullName);
        patient.setNationalId(nationalId);
        patient.setDateOfBirth(LocalDate.parse(dateOfBirth));
        patient.setGender(gender);
        patient.setMobileNo(sanitizeMobileNo(mobileNo));
        patient.setBloodGroup(bloodGroup);
        patient.setAddress(address);

        var created = backendServiceClient.registerPatient(patient);
        if (created.isPresent()) {
            Long patientId = created.get().getPatientId();
            auditClient.log(null, currentUsername(), "PATIENT_REGISTERED", "Patient",
                    patientId, "Registered patient " + fullName, request.getRemoteAddr());

            if (portalPassword != null && !portalPassword.isBlank()
                    && userRepository.findByUsername(nationalId).isEmpty()) {
                User portalUser = new User();
                portalUser.setUsername(nationalId);
                portalUser.setPasswordHash(passwordEncoder.encode(portalPassword));
                portalUser.setFullName(fullName);
                portalUser.setRole(Role.PATIENT);
                portalUser.setPatientId(patientId);
                portalUser.setHospitalId(null);
                portalUser.setEnabled(true);
                userRepository.save(portalUser);
            }

            // Face capture is validated as required above, before the patient was
            // created. Enrollment-call failure here must not roll back the patient
            // record that already exists - staff can re-enroll from /biometric/register.
            BiometricDto biometric = new BiometricDto();
            biometric.setPatientId(patientId);
            biometric.setBiometricType(biometricType != null && !biometricType.isBlank() ? biometricType : "FACE");
            biometric.setBiometricTemplate(biometricFile.getBytes());

            var enrolled = biometricServiceClient.enroll(biometric);
            if (enrolled.isPresent()) {
                auditClient.log(null, currentUsername(), "BIOMETRIC_REGISTERED", "Biometric",
                        enrolled.get().getBiometricId(),
                        "Enrolled FACE biometric for patient " + patientId, request.getRemoteAddr());
            }

            return "redirect:/patients";
        }
        model.addAttribute("title", "Register Patient");
        model.addAttribute("pageTitle", "Register New Patient");
        model.addAttribute("username", currentUsername());
        model.addAttribute("error", "Failed to register patient. Please try again.");
        model.addAttribute("view", "patient/create");
        return "layout";
    }

    @GetMapping("/edit/{id}")
    public String editPatient(@PathVariable Long id, Model model) {
        model.addAttribute("title", "Edit Patient");
        model.addAttribute("pageTitle", "Edit Patient");
        model.addAttribute("username", currentUsername());
        model.addAttribute("patientId", id);
        backendServiceClient.getPatientById(id).ifPresent(p -> model.addAttribute("patient", p));
        model.addAttribute("view", "patient/edit");
        return "layout";
    }

    @PostMapping("/edit/{id}")
    public String submitEditPatient(@PathVariable Long id,
                                     @RequestParam String fullName,
                                     @RequestParam String nationalId,
                                     @RequestParam String dateOfBirth,
                                     @RequestParam String gender,
                                     @RequestParam String mobileNo,
                                     @RequestParam String bloodGroup,
                                     @RequestParam String address,
                                     HttpServletRequest request,
                                     Model model) {
        var existing = backendServiceClient.getPatientById(id);
        if (existing.isEmpty()) {
            model.addAttribute("error", "Patient not found");
            return "redirect:/patients";
        }
        PatientDto patient = existing.get();
        String previousNationalId = patient.getNationalId();
        patient.setFullName(fullName);
        patient.setNationalId(nationalId);
        patient.setDateOfBirth(LocalDate.parse(dateOfBirth));
        patient.setGender(gender);
        patient.setMobileNo(sanitizeMobileNo(mobileNo));
        patient.setBloodGroup(bloodGroup);
        patient.setAddress(address);

        boolean updated = backendServiceClient.updatePatient(id, patient);
        if (updated) {
            // The patient portal login username is the patient's national ID
            // (see patient registration / bulk-seeded accounts) - keep it in
            // sync so changing it here doesn't silently lock the patient out.
            if (!nationalId.equals(previousNationalId)) {
                userRepository.findByPatientId(id).ifPresent(portalUser -> {
                    portalUser.setUsername(nationalId);
                    userRepository.save(portalUser);
                });
            }
            auditClient.log(null, currentUsername(), "PATIENT_UPDATED", "Patient", id,
                    "Updated patient " + fullName, request.getRemoteAddr());
        }
        return "redirect:/patients";
    }

    @GetMapping("/view/{id}")
    public String viewPatient(@PathVariable Long id, Model model) {
        model.addAttribute("title", "Patient Details");
        model.addAttribute("pageTitle", "Patient Details");
        model.addAttribute("username", currentUsername());
        model.addAttribute("patientId", id);
        backendServiceClient.getPatientById(id).ifPresent(p -> model.addAttribute("patient", p));
        model.addAttribute("view", "patient/view");
        return "layout";
    }

    @GetMapping("/history/{id}")
    public String patientHistory(@PathVariable Long id, Model model) {
        model.addAttribute("title", "Medical Timeline");
        model.addAttribute("pageTitle", "Medical Timeline");
        model.addAttribute("username", currentUsername());
        model.addAttribute("patientId", id);
        java.util.List<PrescriptionDto> prescriptions = backendServiceClient.getPrescriptionsByPatient(id);
        java.util.List<ReportDto> reports = backendServiceClient.getReportsByPatient(id);
        model.addAttribute("prescriptions", prescriptions);
        model.addAttribute("reports", reports);

        // One bulk doctor fetch instead of looking a name up per row - this
        // page only ever shows one patient's handful of visits, so a single
        // getAllDoctors() call is cheaper than N+1 per-doctor lookups.
        java.util.Map<Long, String> doctorNames = backendServiceClient.getAllDoctors().stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.gms.gateway.dto.DoctorDto::getDoctorId,
                        com.gms.gateway.dto.DoctorDto::getName,
                        (a, b) -> a));
        model.addAttribute("doctorNames", doctorNames);

        model.addAttribute("view", "patient/history");
        return "layout";
    }
}
