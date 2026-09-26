package com.gms.gateway.controller;

import com.gms.gateway.client.AuditClient;
import com.gms.gateway.client.BackendServiceClient;
import com.gms.gateway.dto.DoctorDto;
import com.gms.gateway.dto.SpecializationDto;
import com.gms.gateway.entity.User;
import com.gms.gateway.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Every staff role's own account page - previously a fully static mock
 * (hardcoded "Loading...", a password form that only showed a JS alert,
 * fake settings checkboxes). This is what a DOCTOR uses to update their own
 * professional details (specialization/license) since /doctors/** (Doctor
 * Management, i.e. browsing/editing OTHER doctors) is blocked for them.
 */
@Controller
@RequestMapping("/profile")
public class ProfileViewController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BackendServiceClient backendServiceClient;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditClient auditClient;

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "User";
    }

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
    public String viewProfile(Model model) {
        model.addAttribute("title", "My Profile");
        model.addAttribute("pageTitle", "My Profile");
        model.addAttribute("username", currentUsername());

        Optional<User> userOpt = userRepository.findByUsername(currentUsername());
        userOpt.ifPresent(user -> model.addAttribute("user", user));

        if (hasRole("DOCTOR") && userOpt.isPresent() && userOpt.get().getDoctorId() != null) {
            Long doctorId = userOpt.get().getDoctorId();
            List<SpecializationDto> specializations = new ArrayList<>(backendServiceClient.getAllSpecializations());

            backendServiceClient.getDoctorById(doctorId).ifPresent(d -> {
                model.addAttribute("doctor", d);
                backendServiceClient.getHospitalById(d.getHospitalId())
                        .ifPresent(h -> model.addAttribute("hospitalName", h.getHospitalName()));

                // Doctor may have a specialization value since deleted from the
                // managed list - keep it selectable instead of silently blanking it.
                boolean alreadyListed = specializations.stream()
                        .anyMatch(s -> s.getName().equalsIgnoreCase(d.getSpecialization()));
                if (!alreadyListed && d.getSpecialization() != null && !d.getSpecialization().isBlank()) {
                    SpecializationDto legacy = new SpecializationDto();
                    legacy.setName(d.getSpecialization());
                    specializations.add(legacy);
                }
            });

            model.addAttribute("specializations", specializations);
        }

        model.addAttribute("view", "profile/view");
        return "layout";
    }

    @PostMapping("/edit")
    public String submitEditProfile(@RequestParam String fullName,
                                     @RequestParam(required = false) String email,
                                     @RequestParam(required = false) String specialization,
                                     @RequestParam(required = false) String licenseNumber,
                                     HttpServletRequest request,
                                     Model model) {
        Optional<User> userOpt = userRepository.findByUsername(currentUsername());
        if (userOpt.isEmpty()) {
            return "redirect:/profile";
        }
        User user = userOpt.get();
        user.setFullName(fullName);
        user.setEmail(email);
        userRepository.save(user);

        if (hasRole("DOCTOR") && user.getDoctorId() != null
                && specialization != null && licenseNumber != null) {
            backendServiceClient.getDoctorById(user.getDoctorId()).ifPresent(doctor -> {
                doctor.setSpecialization(specialization);
                doctor.setLicenseNumber(licenseNumber);
                backendServiceClient.updateDoctor(user.getDoctorId(), doctor);
            });
        }

        auditClient.log(user.getUserId(), currentUsername(), "PROFILE_UPDATED", "User",
                user.getUserId(), "Updated own profile", request.getRemoteAddr());

        model.addAttribute("success", "Profile updated.");
        return viewProfile(model);
    }

    @GetMapping("/change-password")
    public String changePassword(Model model) {
        model.addAttribute("title", "Change Password");
        model.addAttribute("pageTitle", "Change Password");
        model.addAttribute("username", currentUsername());
        model.addAttribute("view", "profile/change-password");
        return "layout";
    }

    @PostMapping("/change-password")
    public String submitChangePassword(@RequestParam String currentPassword,
                                        @RequestParam String newPassword,
                                        @RequestParam String confirmPassword,
                                        HttpServletRequest request,
                                        Model model) {
        model.addAttribute("title", "Change Password");
        model.addAttribute("pageTitle", "Change Password");
        model.addAttribute("username", currentUsername());

        Optional<User> userOpt = userRepository.findByUsername(currentUsername());
        if (userOpt.isEmpty()) {
            model.addAttribute("error", "Could not find your account.");
            model.addAttribute("view", "profile/change-password");
            return "layout";
        }
        User user = userOpt.get();

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            model.addAttribute("error", "Current password is incorrect.");
            model.addAttribute("view", "profile/change-password");
            return "layout";
        }
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "New password and confirmation do not match.");
            model.addAttribute("view", "profile/change-password");
            return "layout";
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        auditClient.log(user.getUserId(), currentUsername(), "PASSWORD_CHANGED", "User",
                user.getUserId(), "Changed own password", request.getRemoteAddr());

        model.addAttribute("success", "Password changed successfully.");
        model.addAttribute("view", "profile/change-password");
        return "layout";
    }

    @GetMapping("/settings")
    public String notificationSettings(Model model) {
        model.addAttribute("title", "Notification Settings");
        model.addAttribute("pageTitle", "Notification Settings");
        model.addAttribute("username", currentUsername());
        model.addAttribute("view", "profile/settings");
        return "layout";
    }
}
