package com.gms.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerificationResultDto {
    private boolean matched;
    private double confidenceScore;
    private Long biometricId;
    private Long patientId;
    private String message;
}
