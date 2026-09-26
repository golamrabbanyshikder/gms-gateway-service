package com.gms.gateway.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrescriptionDto {
    private Long prescriptionId;
    private Long patientId;
    private Long doctorId;
    private Long hospitalId;
    private String medicineList;
    private String instructions;
    private String fileReference;
    private String diseases;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate followUpDate;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
