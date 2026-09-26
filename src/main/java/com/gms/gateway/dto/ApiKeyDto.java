package com.gms.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Mirrors the ApiKey entity shape used identically by backend-service,
 * biometric-service, and file-system-service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyDto {
    private Long id;
    private String apiKey;
    private String partnerName;
    private boolean active;
    private boolean admin;
    private String permissions;
    private LocalDateTime createdAt;
}
