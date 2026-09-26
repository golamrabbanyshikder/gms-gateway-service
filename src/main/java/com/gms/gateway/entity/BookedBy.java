package com.gms.gateway.entity;

/**
 * Mirror of backend-service {@code BookedBy} enum. Lives in gateway because
 * the booking flow originates in the gateway (form post or voice) and the
 * enum travels in the JSON request body to backend-service.
 */
public enum BookedBy {
    PATIENT,
    RECEPTIONIST
}
