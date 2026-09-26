package com.gms.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdentificationResultDto {
    private Long patientId;
    private Long biometricId;
    private double confidenceScore;
    private String biometricType;
}
