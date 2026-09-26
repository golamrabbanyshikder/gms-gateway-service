package com.gms.gateway.controller;

import com.gms.gateway.client.AuditClient;
import com.gms.gateway.client.BackendServiceClient;
import com.gms.gateway.dto.SpecializationDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Admin-maintained list of doctor specializations (e.g. "Cardiologist") used
 * to populate the Specialization dropdown on Register/Edit Doctor, instead
 * of staff typing free text that drifts into inconsistent spellings. Access
 * is restricted to SUPER_ADMIN/HOSPITAL_ADMIN via SecurityConfig, same as
 * Users and API Keys.
 */
@Controller
@RequestMapping("/specializations")
public class SpecializationViewController {

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
    public String list(Model model) {
        model.addAttribute("title", "Specializations");
        model.addAttribute("pageTitle", "Doctor Specializations");
        model.addAttribute("username", currentUsername());
        model.addAttribute("specializations", backendServiceClient.getAllSpecializations());
        model.addAttribute("view", "specialization/list");
        return "layout";
    }

    @PostMapping("/create")
    public String create(@RequestParam String name, HttpServletRequest request, Model model) {
        SpecializationDto specialization = new SpecializationDto();
        specialization.setName(name);

        var created = backendServiceClient.createSpecialization(specialization);
        if (created.isPresent()) {
            auditClient.log(null, currentUsername(), "SPECIALIZATION_CREATED", "Specialization",
                    created.get().getSpecializationId(), "Added specialization " + name, request.getRemoteAddr());
        } else {
            model.addAttribute("error", "Failed to add specialization. It may already exist.");
            model.addAttribute("title", "Specializations");
            model.addAttribute("pageTitle", "Doctor Specializations");
            model.addAttribute("username", currentUsername());
            model.addAttribute("specializations", backendServiceClient.getAllSpecializations());
            model.addAttribute("view", "specialization/list");
            return "layout";
        }
        return "redirect:/specializations";
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("title", "Edit Specialization");
        model.addAttribute("pageTitle", "Edit Specialization");
        model.addAttribute("username", currentUsername());
        model.addAttribute("specializationId", id);
        backendServiceClient.getSpecializationById(id).ifPresent(s -> model.addAttribute("specialization", s));
        model.addAttribute("view", "specialization/edit");
        return "layout";
    }

    @PostMapping("/edit/{id}")
    public String submitEdit(@PathVariable Long id, @RequestParam String name, HttpServletRequest request) {
        var existing = backendServiceClient.getSpecializationById(id);
        if (existing.isEmpty()) {
            return "redirect:/specializations";
        }
        SpecializationDto specialization = existing.get();
        specialization.setName(name);

        boolean updated = backendServiceClient.updateSpecialization(id, specialization);
        if (updated) {
            auditClient.log(null, currentUsername(), "SPECIALIZATION_UPDATED", "Specialization", id,
                    "Renamed specialization to " + name, request.getRemoteAddr());
        }
        return "redirect:/specializations";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, HttpServletRequest request) {
        boolean deleted = backendServiceClient.deleteSpecialization(id);
        if (deleted) {
            auditClient.log(null, currentUsername(), "SPECIALIZATION_DELETED", "Specialization", id,
                    "Deleted specialization " + id, request.getRemoteAddr());
        }
        return "redirect:/specializations";
    }
}
