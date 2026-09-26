package com.gms.gateway.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentSlotDto {

    // No @JsonFormat on the time fields — same reasoning as
    // AvailableSlotsResponseDto: the backend serializes "HH:mm" or "HH:mm:ss"
    // depending on the second component. A strict pattern rejects the
    // seconds variant. Let Jackson's default LocalTimeDeserializer accept both.

    private LocalTime startTime;

    private LocalTime endTime;

    private boolean available;
}
