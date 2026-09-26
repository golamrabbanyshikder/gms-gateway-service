package com.gms.gateway.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DoctorScheduleDto {
    private Long scheduleId;
    private Long doctorId;
    private String dayOfWeek;

    // No @JsonFormat on time fields - same reasoning as the other DTOs:
    // Jackson's default LocalTime deserializer accepts whatever shape the
    // wire format carries. A strict "HH:mm" pattern rejects "HH:mm:ss".

    private LocalTime startTime;

    private LocalTime endTime;

    /** How many patients this window allows per slot. Defaults to 1. */
    private Integer capacityPerSlot;
}

