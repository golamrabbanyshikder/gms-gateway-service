package com.gms.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Mirrors biometric-service's Biometric entity JSON shape. byte[] fields are
 * serialized/deserialized by Jackson as base64 strings automatically.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BiometricDto {
    private Long biometricId;
    private Long patientId;
    private byte[] biometricTemplate;
    private String biometricType;
    private LocalDateTime enrollmentDate;
    private LocalDateTime createdAt;
}
