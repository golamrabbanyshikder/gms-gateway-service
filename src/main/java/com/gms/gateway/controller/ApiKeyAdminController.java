package com.gms.gateway.controller;

import com.gms.gateway.client.AuditClient;
import com.gms.gateway.client.BackendServiceClient;
import com.gms.gateway.client.BiometricServiceClient;
import com.gms.gateway.client.FileServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.util.List;

/**
 * Lets an admin issue a single API key that a medical partner can use to
 * call backend-service, biometric-service, and file-system-service
 * directly. The same key value is registered identically in all 3 services
 * so one shared key works uniformly across the whole API surface; revoking
 * it removes access from all 3 at once. backend-service is treated as the
 * canonical list for display since gateway always creates/revokes in sync
 * across all 3.
 */
@Controller
@RequestMapping("/api-keys")
public class ApiKeyAdminController {

    @Autowired
    private BackendServiceClient backendServiceClient;

    @Autowired
    private BiometricServiceClient biometricServiceClient;

    @Autowired
    private FileServiceClient fileServiceClient;

    @Autowired
    private AuditClient auditClient;

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "User";
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("title", "API Keys");
        model.addAttribute("pageTitle", "API Key Management");
        model.addAttribute("username", currentUsername());
        model.addAttribute("apiKeys", backendServiceClient.getApiKeys());
        model.addAttribute("view", "apikey/list");
        return "layout";
    }

    @PostMapping("/create")
    public String create(@RequestParam String partnerName,
                          @RequestParam(required = false) List<String> backendPermissions,
                          @RequestParam(required = false) List<String> biometricPermissions,
                          @RequestParam(required = false) List<String> fileSystemPermissions,
                          HttpServletRequest request, Model model) {
        String newKey = generateApiKey();

        boolean backendOk = backendServiceClient.createApiKey(newKey, partnerName, join(backendPermissions)).isPresent();
        boolean biometricOk = biometricServiceClient.createApiKey(newKey, partnerName, join(biometricPermissions)).isPresent();
        boolean fileSystemOk = fileServiceClient.createApiKey(newKey, partnerName, join(fileSystemPermissions)).isPresent();

        if (backendOk && biometricOk && fileSystemOk) {
            auditClient.log(null, currentUsername(), "API_KEY_CREATED", "ApiKey", null,
                    "Issued API key for partner " + partnerName, request.getRemoteAddr());
        } else {
            model.addAttribute("error", "Key was not registered in all 3 services - it may not work everywhere. Revoke and reissue.");
        }

        model.addAttribute("title", "API Keys");
        model.addAttribute("pageTitle", "API Key Management");
        model.addAttribute("username", currentUsername());
        model.addAttribute("apiKeys", backendServiceClient.getApiKeys());
        model.addAttribute("newlyIssuedKey", newKey);
        model.addAttribute("view", "apikey/list");
        return "layout";
    }

    @PostMapping("/revoke")
    public String revoke(@RequestParam String apiKey, HttpServletRequest request) {
        backendServiceClient.revokeApiKey(apiKey);
        biometricServiceClient.revokeApiKey(apiKey);
        fileServiceClient.revokeApiKey(apiKey);
        auditClient.log(null, currentUsername(), "API_KEY_REVOKED", "ApiKey", null,
                "Revoked an API key", request.getRemoteAddr());
        return "redirect:/api-keys";
    }

    private String join(List<String> permissions) {
        return permissions != null ? String.join(",", permissions) : "";
    }

    private String generateApiKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder("gms_");
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
