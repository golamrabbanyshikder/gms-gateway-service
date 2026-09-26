package com.gms.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportDto {
    private Long reportId;
    private Long patientId;
    private Long doctorId;
    private String reportType;
    private String fileReference;
    private String fileName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
