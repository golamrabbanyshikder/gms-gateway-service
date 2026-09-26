package com.gms.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiseaseDto {
    private Long diseaseId;
    private String name;
    private LocalDateTime createdAt;
}
