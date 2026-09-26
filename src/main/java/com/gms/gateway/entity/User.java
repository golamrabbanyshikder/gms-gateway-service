package com.gms.gateway.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String username;

    @NotBlank
    @Column(nullable = false)
    private String passwordHash;

    @NotBlank
    @Column(nullable = false)
    private String fullName;

    @Column
    private String email;

    // Forced to VARCHAR (not a native MySQL ENUM column) because
    // Hibernate 6's default ENUM mapping can't be widened by
    // ddl-auto=update when a new Role constant is added later -
    // adding PATIENT here caused exactly that failure once.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private Role role;

    @Column(name = "hospital_id")
    private Long hospitalId;

    @Column(name = "patient_id")
    private Long patientId;

    // Links a DOCTOR-role login to their backend-service Doctor record,
    // so the gateway can resolve "this doctor's own patients" without a
    // separate lookup table.
    @Column(name = "doctor_id")
    private Long doctorId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
