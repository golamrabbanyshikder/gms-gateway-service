package com.gms.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientHistoryResponseDto {
    private PatientDto patient;
    private int visitCount;
    private List<PatientHistoryItemDto> items;
}
