package com.gms.gateway.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Injects role-derived flags into every view controller's model without
 * requiring each one to set them individually - sidebar visibility (e.g.
 * hiding admin-only links from a DOCTOR) needs this on every page that uses
 * layout.html, not just the few that happened to set it before.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    @ModelAttribute
    public void addRoleFlags(Model model) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdminRole = false;
        boolean isDoctorRole = false;
        boolean isPatient = false;
        if (authentication != null) {
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                String role = authority.getAuthority();
                if ("ROLE_SUPER_ADMIN".equals(role) || "ROLE_HOSPITAL_ADMIN".equals(role)) {
                    isAdminRole = true;
                }
                if ("ROLE_DOCTOR".equals(role)) {
                    isDoctorRole = true;
                }
                if ("ROLE_PATIENT".equals(role)) {
                    isPatient = true;
                }
            }
        }
        model.addAttribute("isAdminRole", isAdminRole);
        model.addAttribute("isDoctorRole", isDoctorRole);
        model.addAttribute("isPatient", isPatient);
    }
}
