package com.gms.gateway.controller;

import com.gms.gateway.client.AuditClient;
import com.gms.gateway.client.BackendServiceClient;
import com.gms.gateway.dto.HospitalDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/hospitals")
public class HospitalViewController {

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
    public String hospitalList(Model model) {
        model.addAttribute("title", "Hospitals");
        model.addAttribute("pageTitle", "Hospital Management");
        model.addAttribute("username", currentUsername());
        model.addAttribute("hospitals", backendServiceClient.getAllHospitals());
        model.addAttribute("view", "hospital/list");
        return "layout";
    }

    @GetMapping("/create")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String createHospital(Model model) {
        model.addAttribute("title", "Register Hospital");
        model.addAttribute("pageTitle", "Register New Hospital");
        model.addAttribute("username", currentUsername());
        model.addAttribute("view", "hospital/create");
        return "layout";
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String submitCreateHospital(@RequestParam String hospitalName,
                                        @RequestParam String address,
                                        @RequestParam String city,
                                        @RequestParam String state,
                                        @RequestParam String zipCode,
                                        @RequestParam String contactNumber,
                                        HttpServletRequest request,
                                        Model model) {
        HospitalDto hospital = new HospitalDto();
        hospital.setHospitalName(hospitalName);
        hospital.setAddress(address);
        hospital.setCity(city);
        hospital.setState(state);
        hospital.setZipCode(zipCode);
        hospital.setContactNumber(contactNumber);

        var created = backendServiceClient.registerHospital(hospital);
        if (created.isPresent()) {
            auditClient.log(null, currentUsername(), "HOSPITAL_REGISTERED", "Hospital",
                    created.get().getHospitalId(), "Registered hospital " + hospitalName, request.getRemoteAddr());
            return "redirect:/hospitals";
        }
        model.addAttribute("title", "Register Hospital");
        model.addAttribute("pageTitle", "Register New Hospital");
        model.addAttribute("username", currentUsername());
        model.addAttribute("error", "Failed to register hospital. Please try again.");
        model.addAttribute("view", "hospital/create");
        return "layout";
    }

    @GetMapping("/edit/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String editHospital(@PathVariable Long id, Model model) {
        model.addAttribute("title", "Edit Hospital");
        model.addAttribute("pageTitle", "Edit Hospital");
        model.addAttribute("username", currentUsername());
        model.addAttribute("hospitalId", id);
        backendServiceClient.getHospitalById(id).ifPresent(h -> model.addAttribute("hospital", h));
        model.addAttribute("view", "hospital/edit");
        return "layout";
    }

    @PostMapping("/edit/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String submitEditHospital(@PathVariable Long id,
                                      @RequestParam String hospitalName,
                                      @RequestParam String address,
                                      @RequestParam String city,
                                      @RequestParam String contactNumber,
                                      HttpServletRequest request) {
        var existing = backendServiceClient.getHospitalById(id);
        if (existing.isEmpty()) {
            return "redirect:/hospitals";
        }
        HospitalDto hospital = existing.get();
        hospital.setHospitalName(hospitalName);
        hospital.setAddress(address);
        hospital.setCity(city);
        hospital.setContactNumber(contactNumber);

        boolean updated = backendServiceClient.updateHospital(id, hospital);
        if (updated) {
            auditClient.log(null, currentUsername(), "HOSPITAL_UPDATED", "Hospital", id,
                    "Updated hospital " + hospitalName, request.getRemoteAddr());
        }
        return "redirect:/hospitals";
    }

    @GetMapping("/view/{id}")
    public String viewHospital(@PathVariable Long id, Model model) {
        model.addAttribute("title", "Hospital Details");
        model.addAttribute("pageTitle", "Hospital Details");
        model.addAttribute("username", currentUsername());
        model.addAttribute("hospitalId", id);
        backendServiceClient.getHospitalById(id).ifPresent(h -> model.addAttribute("hospital", h));
        model.addAttribute("view", "hospital/view");
        return "layout";
    }

    @GetMapping("/branches")
    public String hospitalBranches(Model model) {
        model.addAttribute("title", "Hospital Branches");
        model.addAttribute("pageTitle", "Hospital Branches");
        model.addAttribute("username", currentUsername());
        model.addAttribute("view", "hospital/branches");
        return "layout";
    }

    @GetMapping("/departments")
    public String hospitalDepartments(Model model) {
        model.addAttribute("title", "Hospital Departments");
        model.addAttribute("pageTitle", "Hospital Departments");
        model.addAttribute("username", currentUsername());
        model.addAttribute("view", "hospital/departments");
        return "layout";
    }
}
