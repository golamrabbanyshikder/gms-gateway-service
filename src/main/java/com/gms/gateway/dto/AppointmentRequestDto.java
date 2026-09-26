package com.gms.gateway.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.gms.gateway.entity.BookedBy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Inbound booking payload that the gateway posts to backend-service. Field
 * names match backend-service's {@code AppointmentRequest} so Jackson can
 * deserialize the proxied JSON without custom converters.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentRequestDto {

    private Long patientId;
    private Long doctorId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate appointmentDate;

    // No @JsonFormat on preferredTime - same reasoning as the response DTOs:
    // let Jackson's default LocalTime deserializer accept whatever shape the
    // wire format happens to carry ("HH:mm" from the form, "HH:mm:ss" from
    // backend round-trips, etc.).

    private LocalTime preferredTime;

    private String reasonForVisit;
    private String contactNumber;
    private String contactEmail;
    private BookedBy bookedBy;
    private String bookedByUsername;
    private String voiceTranscript;
}
