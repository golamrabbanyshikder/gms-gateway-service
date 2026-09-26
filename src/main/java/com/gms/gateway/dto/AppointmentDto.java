package com.gms.gateway.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.gms.gateway.entity.BookedBy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentDto {
    private Long appointmentId;
    private Long patientId;
    private Long doctorId;
    private Long hospitalId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate appointmentDate;

    // No @JsonFormat on time fields - let Jackson's default LocalTime /
    // LocalDateTime deserializers handle the ISO-8601 wire format the
    // backend-service emits (with or without seconds, with or without
    // fractional seconds). Strict "HH:mm" / "yyyy-MM-dd HH:mm" patterns
    // reject any payload with the optional components present.

    private LocalTime startTime;

    private LocalTime endTime;

    private String status;
    private String reasonForVisit;
    private String contactNumber;
    private String contactEmail;
    private BookedBy bookedBy;
    private String bookedByUsername;
    private String voiceTranscript;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
