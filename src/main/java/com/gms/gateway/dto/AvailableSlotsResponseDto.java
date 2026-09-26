package com.gms.gateway.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailableSlotsResponseDto {

    private Long doctorId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    // No @JsonFormat on the time fields — backend-service serializes
    // LocalTime in the default ISO-8601 form ("HH:mm" or "HH:mm:ss" depending
    // on whether the value has a zero second component). Pinning a strict
    // "HH:mm" pattern here caused deserialization to fail when the backend
    // happened to send "HH:mm:ss" — Jackson stops at index 5 ("unparsed text
    // found at index 5"). Letting the default LocalTimeDeserializer handle it
    // accepts both shapes.

    private LocalTime dayStartTime;

    private LocalTime dayEndTime;

    private int totalSlots;
    private int remainingSlots;
    private List<AppointmentSlotDto> slots;
}
