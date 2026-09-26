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
public class PatientDto {
    private Long patientId;
    private String nationalId;
    private String fullName;
    private String gender;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;

    private String bloodGroup;
    private String mobileNo;
    private String address;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
