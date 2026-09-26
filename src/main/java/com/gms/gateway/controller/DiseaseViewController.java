package com.gms.gateway.controller;

import com.gms.gateway.client.AuditClient;
import com.gms.gateway.client.BackendServiceClient;
import com.gms.gateway.dto.DiseaseDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Admin-maintained list of diseases (e.g. "Hypertension", "Type 2 Diabetes")
 * selectable on Create/Upload Prescription. Access restricted to
 * SUPER_ADMIN/HOSPITAL_ADMIN via SecurityConfig, same as Specializations.
 */
@Controller
@RequestMapping("/diseases")
public class DiseaseViewController {

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
        model.addAttribute("title", "Diseases");
        model.addAttribute("pageTitle", "Disease Management");
        model.addAttribute("username", currentUsername());
        model.addAttribute("diseases", backendServiceClient.getAllDiseases());
        model.addAttribute("view", "disease/list");
        return "layout";
    }

    @PostMapping("/create")
    public String create(@RequestParam String name, HttpServletRequest request, Model model) {
        DiseaseDto disease = new DiseaseDto();
        disease.setName(name);

        var created = backendServiceClient.createDisease(disease);
        if (created.isPresent()) {
            auditClient.log(null, currentUsername(), "DISEASE_CREATED", "Disease",
                    created.get().getDiseaseId(), "Added disease " + name, request.getRemoteAddr());
        } else {
            model.addAttribute("error", "Failed to add disease. It may already exist.");
            model.addAttribute("title", "Diseases");
            model.addAttribute("pageTitle", "Disease Management");
            model.addAttribute("username", currentUsername());
            model.addAttribute("diseases", backendServiceClient.getAllDiseases());
            model.addAttribute("view", "disease/list");
            return "layout";
        }
        return "redirect:/diseases";
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("title", "Edit Disease");
        model.addAttribute("pageTitle", "Edit Disease");
        model.addAttribute("username", currentUsername());
        model.addAttribute("diseaseId", id);
        backendServiceClient.getDiseaseById(id).ifPresent(d -> model.addAttribute("disease", d));
        model.addAttribute("view", "disease/edit");
        return "layout";
    }

    @PostMapping("/edit/{id}")
    public String submitEdit(@PathVariable Long id, @RequestParam String name, HttpServletRequest request) {
        var existing = backendServiceClient.getDiseaseById(id);
        if (existing.isEmpty()) {
            return "redirect:/diseases";
        }
        DiseaseDto disease = existing.get();
        disease.setName(name);

        boolean updated = backendServiceClient.updateDisease(id, disease);
        if (updated) {
            auditClient.log(null, currentUsername(), "DISEASE_UPDATED", "Disease", id,
                    "Renamed disease to " + name, request.getRemoteAddr());
        }
        return "redirect:/diseases";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, HttpServletRequest request) {
        boolean deleted = backendServiceClient.deleteDisease(id);
        if (deleted) {
            auditClient.log(null, currentUsername(), "DISEASE_DELETED", "Disease", id,
                    "Deleted disease " + id, request.getRemoteAddr());
        }
        return "redirect:/diseases";
    }
}
