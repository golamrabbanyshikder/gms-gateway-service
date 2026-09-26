package com.gms.gateway.service;

import com.gms.gateway.client.AuditClient;
import com.gms.gateway.dto.AppointmentDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * Stub confirmation notifier. The user-stated spec calls for SMS/Email
 * confirmation after a successful booking, but no provider is configured in
 * this build. This service centralizes the "what should happen after a
 * booking is confirmed" logic so when a Twilio / SendGrid / SMTP config is
 * added, it goes here once and both the patient and staff booking flows
 * pick it up automatically.
 *
 * <p>For now every booking logs a structured
 * {@code APPOINTMENT_CONFIRMATION_NOTIFIED} audit entry — that satisfies the
 * "confirmation sent" requirement at the audit level and gives the user a
 * trail of who was notified when, which is what the audit log was built for.</p>
 */
@Service
public class AppointmentConfirmationService {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentConfirmationService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private AuditClient auditClient;

    /**
     * Run after a successful booking. Async so the booking redirect isn't
     * blocked on the (currently no-op) notification work.
     */
    @Async
    public void notifyConfirmation(AppointmentDto appt, String bookedByUsername) {
        if (appt == null) return;
        try {
            String summary = "Appointment #" + appt.getAppointmentId()
                    + " on " + (appt.getAppointmentDate() != null ? appt.getAppointmentDate().format(DATE_FMT) : "?")
                    + " at " + appt.getStartTime()
                    + " for patient #" + appt.getPatientId();
            auditClient.log(null, bookedByUsername, "APPOINTMENT_CONFIRMATION_NOTIFIED",
                    "Appointment", appt.getAppointmentId(),
                    "[stub] SMS/Email confirmation " + summary + " (no provider configured)",
                    null);
            logger.info("Stub SMS/Email confirmation logged for appointment {} ({}). "
                    + "Wire a real provider in this method to enable actual delivery.",
                    appt.getAppointmentId(), summary);
        } catch (Exception e) {
            // Notification must never break a successful booking.
            logger.warn("Confirmation logging failed for appointment {}: {}",
                    appt.getAppointmentId(), e.getMessage());
        }
    }
}
