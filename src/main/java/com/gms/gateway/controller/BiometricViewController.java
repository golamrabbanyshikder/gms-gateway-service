package com.gms.gateway.controller;

import com.gms.gateway.client.AuditClient;
import com.gms.gateway.client.BackendServiceClient;
import com.gms.gateway.client.BiometricServiceClient;
import com.gms.gateway.dto.BiometricDto;
import com.gms.gateway.dto.IdentificationResultDto;
import com.gms.gateway.dto.PatientDto;
import com.gms.gateway.dto.VerificationResultDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@Controller
@RequestMapping("/biometric")
public class BiometricViewController {

    @Autowired
    private BiometricServiceClient biometricServiceClient;

    @Autowired
    private BackendServiceClient backendServiceClient;

    @Autowired
    private AuditClient auditClient;

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "User";
    }

    @GetMapping("/register")
    public String registerBiometric(Model model) {
        model.addAttribute("title", "Biometric Registration");
        model.addAttribute("pageTitle", "Biometric Registration");
        model.addAttribute("username", currentUsername());
        model.addAttribute("patients", backendServiceClient.getAllPatients());
        model.addAttribute("view", "biometric/register");
        return "layout";
    }

    @PostMapping("/register")
    public String submitRegisterBiometric(@RequestParam Long patientId,
                                           @RequestParam String biometricType,
                                           @RequestParam("biometricFile") MultipartFile biometricFile,
                                           HttpServletRequest request,
                                           Model model) throws IOException {
        BiometricDto dto = new BiometricDto();
        dto.setPatientId(patientId);
        dto.setBiometricType(biometricType);
        dto.setBiometricTemplate(biometricFile.getBytes());

        var result = biometricServiceClient.enroll(dto);
        model.addAttribute("title", "Biometric Registration");
        model.addAttribute("pageTitle", "Biometric Registration");
        model.addAttribute("username", currentUsername());
        model.addAttribute("patients", backendServiceClient.getAllPatients());
        if (result.isPresent()) {
            auditClient.log(null, currentUsername(), "BIOMETRIC_REGISTERED", "Biometric",
                    result.get().getBiometricId(), "Enrolled biometric for patient " + patientId, request.getRemoteAddr());
            model.addAttribute("message", "Biometric enrolled successfully for patient " + patientId);
        } else {
            model.addAttribute("error", "Failed to enroll biometric. Please try again.");
        }
        model.addAttribute("view", "biometric/register");
        return "layout";
    }

    @GetMapping("/verify")
    public String verifyBiometric(Model model) {
        model.addAttribute("title", "Biometric Verification");
        model.addAttribute("pageTitle", "Biometric Verification");
        model.addAttribute("username", currentUsername());
        model.addAttribute("view", "biometric/verify");
        return "layout";
    }

    @PostMapping("/verify")
    public String submitVerifyBiometric(@RequestParam Long biometricId,
                                         @RequestParam("biometricFile") MultipartFile biometricFile,
                                         HttpServletRequest request,
                                         Model model) throws IOException {
        var result = biometricServiceClient.verify(biometricId, biometricFile.getBytes());

        model.addAttribute("title", "Biometric Verification");
        model.addAttribute("pageTitle", "Biometric Verification");
        model.addAttribute("username", currentUsername());

        VerificationResultDto verification = result.orElse(null);
        model.addAttribute("result", verification);
        if (verification == null) {
            model.addAttribute("error", "No match found or biometric record does not exist.");
        }
        auditClient.log(null, currentUsername(), "BIOMETRIC_VERIFY", "Biometric", biometricId,
                "Verification attempt, matched=" + (verification != null && verification.isMatched()), request.getRemoteAddr());
        model.addAttribute("view", "biometric/verify");
        return "layout";
    }

    @GetMapping("/emergency-identification")
    public String emergencyIdentification(Model model) {
        model.addAttribute("title", "Emergency Identification");
        model.addAttribute("pageTitle", "Emergency Patient Identification");
        model.addAttribute("username", currentUsername());
        model.addAttribute("view", "biometric/emergency-identification");
        return "layout";
    }

    @PostMapping("/emergency-identification")
    public String submitEmergencyIdentification(@RequestParam("biometricFile") MultipartFile biometricFile,
                                                  @RequestParam(required = false) String biometricType,
                                                  HttpServletRequest request,
                                                  Model model) throws IOException {
        model.addAttribute("title", "Emergency Identification");
        model.addAttribute("pageTitle", "Emergency Patient Identification");
        model.addAttribute("username", currentUsername());

        var identificationOpt = biometricServiceClient.identify(biometricFile.getBytes(), biometricType);

        IdentificationResultDto identification = identificationOpt.orElse(null);
        model.addAttribute("identification", identification);

        if (identification != null) {
            backendServiceClient.getPatientById(identification.getPatientId())
                    .ifPresentOrElse(
                            (PatientDto patient) -> model.addAttribute("patient", patient),
                            () -> model.addAttribute("error", "Patient matched but record could not be retrieved.")
                    );
            auditClient.log(null, currentUsername(), "BIOMETRIC_IDENTIFY", "Biometric", identification.getBiometricId(),
                    "Emergency identification matched patient " + identification.getPatientId(), request.getRemoteAddr());
        } else {
            model.addAttribute("error", "No matching patient found for this biometric sample.");
            auditClient.log(null, currentUsername(), "BIOMETRIC_IDENTIFY", "Biometric", null,
                    "Emergency identification found no match", request.getRemoteAddr());
        }

        model.addAttribute("view", "biometric/emergency-identification");
        return "layout";
    }

    @GetMapping("/history")
    public String biometricHistory(Model model) {
        model.addAttribute("title", "Biometric History");
        model.addAttribute("pageTitle", "Biometric History");
        model.addAttribute("username", currentUsername());
        model.addAttribute("view", "biometric/history");
        return "layout";
    }
}
