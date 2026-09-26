package com.gms.gateway.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * One day's availability for a doctor — mirror of backend-service's
 * {@code DoctorAvailabilityDate}. Drives the booking UI's calendar
 * highlight (green = open, grey = full).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DoctorAvailabilityDateDto {
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    private int bookedSlots;
    private int remainingSlots;
    private boolean fullyBooked;
}
