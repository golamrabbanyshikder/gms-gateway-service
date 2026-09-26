package com.gms.gateway.controller;

import com.gms.gateway.client.BackendServiceClient;
import com.gms.gateway.dto.DoctorDto;
import com.gms.gateway.dto.HospitalDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Two read-only doctor directories available to every staff role:
 *  - /contacts/medical       : doctors grouped by specialization (for medical
 *                              referral / "who do I contact for X" lookups)
 *  - /contacts/appointment   : doctors grouped by hospital with the hospital's
 *                              contact number (for booking appointments by phone)
 * No booking or schedule tables - these are contact directories over existing
 * doctor + hospital data, per the agreed scope.
 */
@Controller
@RequestMapping("/contacts")
public class ContactListViewController {

    @Autowired
    private BackendServiceClient backendServiceClient;

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "User";
    }

    private Map<Long, String> hospitalNames(List<HospitalDto> hospitals) {
        return hospitals.stream()
                .collect(Collectors.toMap(HospitalDto::getHospitalId, HospitalDto::getHospitalName, (a, b) -> a));
    }

    @GetMapping("/medical")
    public String medicalContactList(Model model) {
        List<DoctorDto> doctors = backendServiceClient.getAllDoctors().stream()
                .sorted(Comparator.comparing(DoctorDto::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<HospitalDto> hospitals = backendServiceClient.getAllHospitals();

        // Distinct specializations (sorted) drive the grouped sections in the template.
        List<String> specializations = doctors.stream()
                .map(DoctorDto::getSpecialization)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted()
                .toList();

        model.addAttribute("title", "Medical Contact List");
        model.addAttribute("pageTitle", "Medical Contact List");
        model.addAttribute("username", currentUsername());
        model.addAttribute("doctors", doctors);
        model.addAttribute("specializations", specializations);
        model.addAttribute("hospitalNames", hospitalNames(hospitals));
        model.addAttribute("view", "contact/medical");
        return "layout";
    }

    @GetMapping("/appointment")
    public String appointmentContactList(Model model) {
        List<DoctorDto> doctors = backendServiceClient.getAllDoctors().stream()
                .sorted(Comparator.comparing(DoctorDto::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<HospitalDto> hospitals = backendServiceClient.getAllHospitals().stream()
                .sorted(Comparator.comparing(HospitalDto::getHospitalName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        model.addAttribute("title", "Appointment Contact List");
        model.addAttribute("pageTitle", "Appointment Contact List");
        model.addAttribute("username", currentUsername());
        model.addAttribute("doctors", doctors);
        model.addAttribute("hospitals", hospitals);
        // Hospital ids that actually have at least one doctor - lets the template
        // show a "No doctors" row for empty hospitals without needing SpEL lambdas
        // (which Thymeleaf doesn't support).
        model.addAttribute("hospitalIdsWithDoctors",
                doctors.stream().map(DoctorDto::getHospitalId).filter(Objects::nonNull).distinct().toList());
        model.addAttribute("view", "contact/appointment");
        return "layout";
    }
}
