package com.gms.gateway.controller;

import com.gms.gateway.client.BackendServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/audit")
public class AuditViewController {

    @Autowired
    private BackendServiceClient backendServiceClient;

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "User";
    }

    @GetMapping
    public String auditLogs(Model model) {
        model.addAttribute("title", "Audit Logs");
        model.addAttribute("pageTitle", "Audit & Activity Logs");
        model.addAttribute("username", currentUsername());
        model.addAttribute("auditLogs", backendServiceClient.getAllAuditLogs());
        model.addAttribute("view", "audit/logs");
        return "layout";
    }

    @GetMapping("/login-history")
    public String loginHistory(Model model) {
        model.addAttribute("title", "Login History");
        model.addAttribute("pageTitle", "Login History");
        model.addAttribute("username", currentUsername());
        model.addAttribute("view", "audit/login-history");
        return "layout";
    }

    @GetMapping("/system")
    public String systemActivity(Model model) {
        model.addAttribute("title", "System Activity");
        model.addAttribute("pageTitle", "System Activity");
        model.addAttribute("username", currentUsername());
        model.addAttribute("view", "audit/system");
        return "layout";
    }
}
