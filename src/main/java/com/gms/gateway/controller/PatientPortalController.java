package com.gms.gateway.controller;

import com.gms.gateway.client.BackendServiceClient;
import com.gms.gateway.client.FileServiceClient;
import com.gms.gateway.entity.User;
import com.gms.gateway.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Optional;

/**
 * Self-service portal for PATIENT-role logins. Deliberately separate from
 * the staff-facing /patients (plural) controller so URL-based security
 * rules can restrict patients to only this subtree.
 */
@Controller
@RequestMapping("/patient")
public class PatientPortalController {

    @Autowired
    private BackendServiceClient backendServiceClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileServiceClient fileServiceClient;

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "User";
    }

    /**
     * Resolves the patientId linked to the currently logged-in user.
     * Returns empty if the user can't be found or has no linked patientId
     * (a misconfiguration - e.g. a PATIENT-role account created without
     * ever being linked to a backend patient record).
     */
    private Optional<Long> currentPatientId() {
        return userRepository.findByUsername(currentUsername())
                .map(User::getPatientId);
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Optional<Long> patientIdOpt = currentPatientId();
        if (patientIdOpt.isEmpty()) {
            return "redirect:/login?error";
        }
        Long patientId = patientIdOpt.get();
        model.addAttribute("title", "My Dashboard");
        model.addAttribute("pageTitle", "My Dashboard");
        model.addAttribute("username", currentUsername());
        model.addAttribute("isPatient", true);
        backendServiceClient.getPatientById(patientId).ifPresent(p -> model.addAttribute("patient", p));
        model.addAttribute("view", "patient-portal/dashboard");
        return "layout";
    }

    @GetMapping("/history")
    public String history(Model model) {
        Optional<Long> patientIdOpt = currentPatientId();
        if (patientIdOpt.isEmpty()) {
            return "redirect:/login?error";
        }
        Long patientId = patientIdOpt.get();
        model.addAttribute("title", "My Medical History");
        model.addAttribute("pageTitle", "My Medical History");
        model.addAttribute("username", currentUsername());
        model.addAttribute("isPatient", true);

        var historyOpt = backendServiceClient.getPatientHistory(patientId);
        if (historyOpt.isPresent()) {
            model.addAttribute("history", historyOpt.get());
        } else {
            // Friendly "no history yet" state rather than erroring.
            backendServiceClient.getPatientById(patientId).ifPresent(p -> model.addAttribute("patient", p));
        }
        model.addAttribute("view", "patient-portal/history");
        return "layout";
    }

    // Deliberately separate from ReportViewController/PrescriptionViewController's
    // staff preview endpoints (/reports/preview, /prescriptions/preview) rather
    // than reusing them - those are gated to staff roles only and have no
    // per-patient ownership check, since any staff member can view any
    // patient's records. A PATIENT must only ever be able to preview their
    // OWN records, so this checks patientId ownership explicitly before
    // serving anything.

    @GetMapping("/prescriptions/preview/{id}")
    public String previewPrescription(@PathVariable Long id, Model model) {
        Optional<Long> patientIdOpt = currentPatientId();
        if (patientIdOpt.isEmpty()) {
            return "redirect:/login?error";
        }

        var prescriptionOpt = backendServiceClient.getPrescriptionById(id);
        boolean owned = prescriptionOpt.isPresent() && patientIdOpt.get().equals(prescriptionOpt.get().getPatientId());
        if (owned) {
            var prescription = prescriptionOpt.get();
            model.addAttribute("prescription", prescription);
            backendServiceClient.getDoctorById(prescription.getDoctorId()).ifPresent(d -> model.addAttribute("doctorName", d.getName()));
            backendServiceClient.getHospitalById(prescription.getHospitalId()).ifPresent(h -> model.addAttribute("hospitalName", h.getHospitalName()));
            backendServiceClient.getPatientById(prescription.getPatientId()).ifPresent(pt -> model.addAttribute("patientName", pt.getFullName()));
        } else {
            model.addAttribute("prescription", null);
        }
        return "prescription/preview";
    }

    @GetMapping("/reports/preview/{id}")
    public ResponseEntity<byte[]> previewReport(@PathVariable Long id) {
        Optional<Long> patientIdOpt = currentPatientId();
        if (patientIdOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        var reportOpt = backendServiceClient.getReportById(id);
        if (reportOpt.isEmpty() || reportOpt.get().getFileReference() == null
                || !patientIdOpt.get().equals(reportOpt.get().getPatientId())) {
            return ResponseEntity.notFound().build();
        }

        Long fileId;
        try {
            fileId = Long.valueOf(reportOpt.get().getFileReference());
        } catch (NumberFormatException e) {
            return ResponseEntity.notFound().build();
        }

        var downloadOpt = fileServiceClient.download(fileId);
        if (downloadOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var download = downloadOpt.get();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + download.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(download.getContentType()))
                .body(download.getData());
    }
}
