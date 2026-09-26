package com.gms.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DoctorDto {
    private Long doctorId;
    private String name;
    private String specialization;
    private Long hospitalId;
    private String licenseNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
