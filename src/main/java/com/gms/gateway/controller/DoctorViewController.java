package com.gms.gateway.controller;

import com.gms.gateway.client.AuditClient;
import com.gms.gateway.client.BackendServiceClient;
import com.gms.gateway.dto.DoctorDto;
import com.gms.gateway.dto.SpecializationDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/doctors")
public class DoctorViewController {

    @Autowired
    private BackendServiceClient backendServiceClient;

    @Autowired
    private AuditClient auditClient;

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "User";
    }

    @GetMapping
    public String doctorList(Model model) {
        model.addAttribute("title", "Doctors");
        model.addAttribute("pageTitle", "Doctor Management");
        model.addAttribute("username", currentUsername());
        model.addAttribute("doctors", backendServiceClient.getAllDoctors());

        java.util.Map<Long, String> hospitalNames = backendServiceClient.getAllHospitals().stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.gms.gateway.dto.HospitalDto::getHospitalId,
                        com.gms.gateway.dto.HospitalDto::getHospitalName,
                        (a, b) -> a));
        model.addAttribute("hospitalNames", hospitalNames);

        model.addAttribute("view", "doctor/list");
        return "layout";
    }

    // Backs the cascading Hospital -> Specialization -> Doctor selectors used
    // by prescription/report creation forms - the doctor select narrows as
    // soon as a hospital is picked, instead of staff having to know IDs.
    @GetMapping("/by-hospital/{hospitalId}")
    @ResponseBody
    public List<DoctorDto> doctorsByHospital(@PathVariable Long hospitalId) {
        return backendServiceClient.getDoctorsByHospital(hospitalId);
    }

    @GetMapping("/create")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN')")
    public String createDoctor(Model model) {
        model.addAttribute("title", "Register Doctor");
        model.addAttribute("pageTitle", "Register New Doctor");
        model.addAttribute("username", currentUsername());
        model.addAttribute("hospitals", backendServiceClient.getAllHospitals());
        model.addAttribute("specializations", backendServiceClient.getAllSpecializations());
        model.addAttribute("view", "doctor/create");
        return "layout";
    }

    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN')")
    public String submitCreateDoctor(@RequestParam String name,
                                      @RequestParam String specialization,
                                      @RequestParam Long hospitalId,
                                      @RequestParam String licenseNumber,
                                      HttpServletRequest request,
                                      Model model) {
        DoctorDto doctor = new DoctorDto();
        doctor.setName(name);
        doctor.setSpecialization(specialization);
        doctor.setHospitalId(hospitalId);
        doctor.setLicenseNumber(licenseNumber);

        var created = backendServiceClient.registerDoctor(doctor);
        if (created.isPresent()) {
            auditClient.log(null, currentUsername(), "DOCTOR_REGISTERED", "Doctor",
                    created.get().getDoctorId(), "Registered doctor " + name, request.getRemoteAddr());
            return "redirect:/doctors";
        }
        model.addAttribute("title", "Register Doctor");
        model.addAttribute("pageTitle", "Register New Doctor");
        model.addAttribute("username", currentUsername());
        model.addAttribute("hospitals", backendServiceClient.getAllHospitals());
        model.addAttribute("specializations", backendServiceClient.getAllSpecializations());
        model.addAttribute("error", "Failed to register doctor. Please try again.");
        model.addAttribute("view", "doctor/create");
        return "layout";
    }

    @GetMapping("/edit/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN')")
    public String editDoctor(@PathVariable Long id, Model model) {
        model.addAttribute("title", "Edit Doctor");
        model.addAttribute("pageTitle", "Edit Doctor");
        model.addAttribute("username", currentUsername());
        model.addAttribute("doctorId", id);

        List<SpecializationDto> specializations = new ArrayList<>(backendServiceClient.getAllSpecializations());
        backendServiceClient.getDoctorById(id).ifPresent(d -> {
            model.addAttribute("doctor", d);
            // Doctor may have been registered before the specialization list existed,
            // or with a value since deleted from it - keep their current value
            // selectable instead of silently blanking it out on this page.
            boolean alreadyListed = specializations.stream()
                    .anyMatch(s -> s.getName().equalsIgnoreCase(d.getSpecialization()));
            if (!alreadyListed && d.getSpecialization() != null && !d.getSpecialization().isBlank()) {
                SpecializationDto legacy = new SpecializationDto();
                legacy.setName(d.getSpecialization());
                specializations.add(legacy);
            }
        });
        model.addAttribute("specializations", specializations);

        model.addAttribute("view", "doctor/edit");
        return "layout";
    }

    @PostMapping("/edit/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HOSPITAL_ADMIN')")
    public String submitEditDoctor(@PathVariable Long id,
                                    @RequestParam String name,
                                    @RequestParam String specialization,
                                    @RequestParam String licenseNumber,
                                    HttpServletRequest request) {
        var existing = backendServiceClient.getDoctorById(id);
        if (existing.isEmpty()) {
            return "redirect:/doctors";
        }
        DoctorDto doctor = existing.get();
        doctor.setName(name);
        doctor.setSpecialization(specialization);
        doctor.setLicenseNumber(licenseNumber);

        boolean updated = backendServiceClient.updateDoctor(id, doctor);
        if (updated) {
            auditClient.log(null, currentUsername(), "DOCTOR_UPDATED", "Doctor", id,
                    "Updated doctor " + name, request.getRemoteAddr());
        }
        return "redirect:/doctors";
    }

    @GetMapping("/view/{id}")
    public String viewDoctor(@PathVariable Long id, Model model) {
        model.addAttribute("title", "Doctor Profile");
        model.addAttribute("pageTitle", "Doctor Profile");
        model.addAttribute("username", currentUsername());
        model.addAttribute("doctorId", id);
        backendServiceClient.getDoctorById(id).ifPresent(d -> {
            model.addAttribute("doctor", d);
            backendServiceClient.getHospitalById(d.getHospitalId()).ifPresent(h -> model.addAttribute("hospitalName", h.getHospitalName()));
        });
        model.addAttribute("view", "doctor/view");
        return "layout";
    }

    @GetMapping("/performance")
    public String doctorPerformance(Model model) {
        model.addAttribute("title", "Doctor Performance");
        model.addAttribute("pageTitle", "Doctor Performance");
        model.addAttribute("username", currentUsername());
        model.addAttribute("view", "doctor/performance");
        return "layout";
    }
}
