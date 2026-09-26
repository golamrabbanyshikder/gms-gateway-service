package com.gms.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientHistoryItemDto {
    private String recordType;
    private Long recordId;
    private String category;
    private String summary;
    private Long doctorId;
    private Long hospitalId;
    private LocalDateTime date;
}
