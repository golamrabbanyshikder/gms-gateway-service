package com.gms.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HospitalDto {
    private Long hospitalId;
    private String hospitalName;
    private String address;
    private String city;
    private String state;
    private String zipCode;
    private String contactNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
