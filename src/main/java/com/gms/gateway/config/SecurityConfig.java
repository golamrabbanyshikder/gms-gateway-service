package com.gms.gateway.config;

import com.gms.gateway.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                // Default is DENY, which blocks the same-origin iframe used to preview
                // prescriptions/reports in the Medical Timeline; SAMEORIGIN still blocks
                // cross-site framing/clickjacking.
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(authz -> authz
                        // Public auth-related routes
                        .requestMatchers(
                                "/api/auth/**",
                                "/login",
                                "/forgot-password",
                                "/verify-otp",
                                "/reset-password",
                                "/logout",
                                "/session-expired",
                                "/css/**",
                                "/js/**",
                                "/vendor/**",
                                "/models/**",
                                "/error/**",
                                "/error"
                        ).permitAll()
                        // Patient self-service portal - PATIENT role only
                        .requestMatchers("/patient/**").hasRole("PATIENT")
                        // Doctor contact directories - available to EVERY role
                        // (staff for referrals/booking, patients to find a specialist).
                        .requestMatchers("/contacts/**").authenticated()
                        // User management - admin only
                        .requestMatchers("/users/**").hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN")
                        // API key issuance for external medical partners - admin only
                        .requestMatchers("/api-keys/**").hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN")
                        // Specialization list maintenance - admin only; doctors/receptionists only
                        // need to read it indirectly via /doctors/create and /doctors/edit
                        .requestMatchers("/specializations/**").hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN")
                        // Disease list maintenance - admin only; doctors select from it on
                        // Create/Upload Prescription but don't manage the list themselves.
                        .requestMatchers("/diseases/**").hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN")
                        // DOCTOR can browse/clinically update their own patients (Patient
                        // Management, Medical Timeline, prescriptions, reports) but not edit a
                        // patient's demographic/identity record - that's a clerical task for
                        // admin/reception. Must be listed before the broader /patients/** rule
                        // below since Spring Security uses the first matching matcher.
                        .requestMatchers("/patients/edit/**").hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN", "RECEPTIONIST")
                        // Appointment booking - receptionist/admin can book on behalf of any
                        // patient; doctors can only see their own appointment list, not book.
                        .requestMatchers("/booking/**").hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN", "RECEPTIONIST", "DOCTOR")
                        // Server-side TTS proxy for the voice booking flow - any
                        // authenticated user (staff or patient) can request
                        // synthesized audio so missing-field prompts can be spoken
                        // back in the patient's own language.
                        .requestMatchers("/tts/**").authenticated()
                        // Used by the Hospital -> Category -> Doctor cascade on prescription/report
                        // forms - must stay reachable for DOCTOR even though Doctor Management
                        // (browsing/editing other doctors) below is not.
                        .requestMatchers("/doctors/by-hospital/**").hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN", "DOCTOR", "RECEPTIONIST")
                        // Emergency/return-visit identification stays available to DOCTOR even
                        // though standalone Biometric Registration below is not.
                        .requestMatchers("/biometric/emergency-identification").hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN", "DOCTOR", "RECEPTIONIST")
                        // Hospital management - reads broadly available to staff (excluding DOCTOR,
                        // who has no need to browse the hospital list), writes restricted to SUPER_ADMIN
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/hospital/**", "/hospitals/**")
                                .hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN", "RECEPTIONIST")
                        .requestMatchers("/hospital/**", "/hospitals/**").hasRole("SUPER_ADMIN")
                        // Doctor Management (browsing/editing/registering OTHER doctors) and
                        // standalone Biometric Registration and Audit Logs - admin/reception only,
                        // not a regular doctor's own login.
                        .requestMatchers("/doctor/**", "/doctors/**").hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN", "RECEPTIONIST")
                        .requestMatchers("/biometric/**").hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN", "RECEPTIONIST")
                        .requestMatchers("/audit/**").hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN", "RECEPTIONIST")
                        // Staff-facing clinical/admin routes - all 4 staff roles, explicitly excluding PATIENT
                        .requestMatchers(
                                "/patients/**",
                                "/prescription/**",
                                "/prescriptions/**",
                                "/report/**",
                                "/reports/**",
                                "/dashboard",
                                "/admin-dashboard",
                                "/doctor-dashboard",
                                "/hospital-dashboard"
                        ).hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN", "DOCTOR", "RECEPTIONIST")
                        // Profile - staff only; the staff profile view/fields aren't patient-appropriate
                        .requestMatchers("/profile/**").hasAnyRole("SUPER_ADMIN", "HOSPITAL_ADMIN", "DOCTOR", "RECEPTIONIST")
                        // Everything else requires authentication (defensive; should not be reachable
                        // for PATIENT given the explicit matchers above, so it can't fall through to a
                        // generic authenticated() catch-all)
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
