package com.gms.gateway.client;

import com.gms.gateway.dto.ApiKeyDto;
import com.gms.gateway.dto.AppointmentDto;
import com.gms.gateway.dto.AppointmentRequestDto;
import com.gms.gateway.dto.AppointmentSlotDto;
import com.gms.gateway.dto.AuditLogDto;
import com.gms.gateway.dto.AvailableSlotsResponseDto;
import com.gms.gateway.dto.DoctorDto;
import com.gms.gateway.dto.DoctorScheduleDto;
import com.gms.gateway.dto.DiseaseDto;
import com.gms.gateway.dto.HospitalDto;
import com.gms.gateway.dto.SpecializationDto;
import com.gms.gateway.dto.PatientDto;
import com.gms.gateway.dto.PatientHistoryResponseDto;
import com.gms.gateway.dto.PrescriptionDto;
import com.gms.gateway.dto.ReportDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Thin HTTP client over backend-service's REST API. All methods swallow
 * connectivity/4xx failures and degrade gracefully (empty list / empty
 * optional) so a backend-service outage doesn't 500 the gateway pages.
 */
@Component
public class BackendServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(BackendServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public BackendServiceClient(RestTemplate restTemplate, @Value("${services.backend-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    // ---------- Patient ----------

    public List<PatientDto> getAllPatients() {
        try {
            PatientDto[] result = restTemplate.getForObject(baseUrl + "/api/patient", PatientDto[].class);
            return result != null ? List.of(result) : Collections.emptyList();
        } catch (Exception e) {
            logger.error("Failed to fetch patients from backend-service", e);
            return Collections.emptyList();
        }
    }

    public Optional<PatientDto> getPatientById(Long id) {
        try {
            return Optional.ofNullable(restTemplate.getForObject(baseUrl + "/api/patient/" + id, PatientDto.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Failed to fetch patient {} from backend-service", id, e);
            return Optional.empty();
        }
    }

    public Optional<PatientDto> getPatientByNationalId(String nationalId) {
        try {
            return Optional.ofNullable(restTemplate.getForObject(
                    baseUrl + "/api/patient/national-id/" + nationalId, PatientDto.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Failed to fetch patient by national id from backend-service", e);
            return Optional.empty();
        }
    }

    public List<PatientDto> getPatientsByDoctorId(Long doctorId) {
        try {
            PatientDto[] result = restTemplate.getForObject(
                    baseUrl + "/api/patient/by-doctor/" + doctorId, PatientDto[].class);
            return result != null ? List.of(result) : Collections.emptyList();
        } catch (Exception e) {
            logger.error("Failed to fetch patients for doctor {} from backend-service", doctorId, e);
            return Collections.emptyList();
        }
    }

    public Optional<PatientDto> registerPatient(PatientDto patient) {
        try {
            return Optional.ofNullable(restTemplate.postForObject(baseUrl + "/api/patient/register", patient, PatientDto.class));
        } catch (Exception e) {
            logger.error("Failed to register patient in backend-service", e);
            return Optional.empty();
        }
    }

    public boolean updatePatient(Long id, PatientDto patient) {
        try {
            restTemplate.put(baseUrl + "/api/patient/" + id, patient);
            return true;
        } catch (Exception e) {
            logger.error("Failed to update patient {} in backend-service", id, e);
            return false;
        }
    }

    public Optional<PatientHistoryResponseDto> getPatientHistory(Long patientId) {
        try {
            return Optional.ofNullable(restTemplate.getForObject(
                    baseUrl + "/api/patient/" + patientId + "/history", PatientHistoryResponseDto.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Failed to fetch history for patient {} from backend-service", patientId, e);
            return Optional.empty();
        }
    }

    // ---------- Doctor ----------

    public List<DoctorDto> getAllDoctors() {
        try {
            DoctorDto[] result = restTemplate.getForObject(baseUrl + "/api/doctor", DoctorDto[].class);
            return result != null ? List.of(result) : Collections.emptyList();
        } catch (Exception e) {
            logger.error("Failed to fetch doctors from backend-service", e);
            return Collections.emptyList();
        }
    }

    public Optional<DoctorDto> getDoctorById(Long id) {
        try {
            return Optional.ofNullable(restTemplate.getForObject(baseUrl + "/api/doctor/" + id, DoctorDto.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Failed to fetch doctor {} from backend-service", id, e);
            return Optional.empty();
        }
    }

    public List<DoctorDto> getDoctorsByHospital(Long hospitalId) {
        try {
            DoctorDto[] result = restTemplate.getForObject(baseUrl + "/api/doctor/hospital/" + hospitalId, DoctorDto[].class);
            return result != null ? List.of(result) : Collections.emptyList();
        } catch (Exception e) {
            logger.error("Failed to fetch doctors for hospital {} from backend-service", hospitalId, e);
            return Collections.emptyList();
        }
    }

    public Optional<DoctorDto> registerDoctor(DoctorDto doctor) {
        try {
            return Optional.ofNullable(restTemplate.postForObject(baseUrl + "/api/doctor/register", doctor, DoctorDto.class));
        } catch (Exception e) {
            logger.error("Failed to register doctor in backend-service", e);
            return Optional.empty();
        }
    }

    public boolean updateDoctor(Long id, DoctorDto doctor) {
        try {
            restTemplate.put(baseUrl + "/api/doctor/" + id, doctor);
            return true;
        } catch (Exception e) {
            logger.error("Failed to update doctor {} in backend-service", id, e);
            return false;
        }
    }

    // ---------- Hospital ----------

    public List<HospitalDto> getAllHospitals() {
        try {
            HospitalDto[] result = restTemplate.getForObject(baseUrl + "/api/hospital", HospitalDto[].class);
            return result != null ? List.of(result) : Collections.emptyList();
        } catch (Exception e) {
            logger.error("Failed to fetch hospitals from backend-service", e);
            return Collections.emptyList();
        }
    }

    public Optional<HospitalDto> getHospitalById(Long id) {
        try {
            return Optional.ofNullable(restTemplate.getForObject(baseUrl + "/api/hospital/" + id, HospitalDto.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Failed to fetch hospital {} from backend-service", id, e);
            return Optional.empty();
        }
    }

    public Optional<HospitalDto> registerHospital(HospitalDto hospital) {
        try {
            return Optional.ofNullable(restTemplate.postForObject(baseUrl + "/api/hospital/register", hospital, HospitalDto.class));
        } catch (Exception e) {
            logger.error("Failed to register hospital in backend-service", e);
            return Optional.empty();
        }
    }

    public boolean updateHospital(Long id, HospitalDto hospital) {
        try {
            restTemplate.put(baseUrl + "/api/hospital/" + id, hospital);
            return true;
        } catch (Exception e) {
            logger.error("Failed to update hospital {} in backend-service", id, e);
            return false;
        }
    }

    // ---------- Prescription ----------

    public List<PrescriptionDto> getAllPrescriptions() {
        try {
            PrescriptionDto[] result = restTemplate.getForObject(baseUrl + "/api/prescription", PrescriptionDto[].class);
            return result != null ? List.of(result) : Collections.emptyList();
        } catch (Exception e) {
            logger.error("Failed to fetch prescriptions from backend-service", e);
            return Collections.emptyList();
        }
    }

    public Optional<PrescriptionDto> getPrescriptionById(Long id) {
        try {
            return Optional.ofNullable(restTemplate.getForObject(baseUrl + "/api/prescription/" + id, PrescriptionDto.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Failed to fetch prescription {} from backend-service", id, e);
            return Optional.empty();
        }
    }

    public List<PrescriptionDto> getPrescriptionsByPatient(Long patientId) {
        try {
            PrescriptionDto[] result = restTemplate.getForObject(baseUrl + "/api/prescription/patient/" + patientId, PrescriptionDto[].class);
            return result != null ? List.of(result) : Collections.emptyList();
        } catch (Exception e) {
            logger.error("Failed to fetch prescriptions for patient {} from backend-service", patientId, e);
            return Collections.emptyList();
        }
    }

    public List<PrescriptionDto> getPrescriptionsByDoctor(Long doctorId) {
        try {
            PrescriptionDto[] result = restTemplate.getForObject(baseUrl + "/api/prescription/doctor/" + doctorId, PrescriptionDto[].class);
            return result != null ? List.of(result) : Collections.emptyList();
        } catch (Exception e) {
            logger.error("Failed to fetch prescriptions for doctor {} from backend-service", doctorId, e);
            return Collections.emptyList();
        }
    }

    public Optional<PrescriptionDto> createPrescription(PrescriptionDto prescription) {
        try {
            return Optional.ofNullable(restTemplate.postForObject(baseUrl + "/api/prescription", prescription, PrescriptionDto.class));
        } catch (Exception e) {
            logger.error("Failed to create prescription in backend-service", e);
            return Optional.empty();
        }
    }

    // ---------- Report ----------

    public List<ReportDto> getAllReports() {
        try {
            ReportDto[] result = restTemplate.getForObject(baseUrl + "/api/report", ReportDto[].class);
            return result != null ? List.of(result) : Collections.emptyList();
        } catch (Exception e) {
            logger.error("Failed to fetch reports from backend-service", e);
            return Collections.emptyList();
        }
    }

    public Optional<ReportDto> getReportById(Long id) {
        try {
            return Optional.ofNullable(restTemplate.getForObject(baseUrl + "/api/report/" + id, ReportDto.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Failed to fetch report {} from backend-service", id, e);
            return Optional.empty();
        }
    }

    public List<ReportDto> getReportsByPatient(Long patientId) {
        try {
            ReportDto[] result = restTemplate.getForObject(baseUrl + "/api/report/patient/" + patientId, ReportDto[].class);
            return result != null ? List.of(result) : Collections.emptyList();
        } catch (Exception e) {
            logger.error("Failed to fetch reports for patient {} from backend-service", patientId, e);
            return Collections.emptyList();
        }
    }

    public Optional<ReportDto> createReport(ReportDto report) {
        try {
            return Optional.ofNullable(restTemplate.postForObject(baseUrl + "/api/report", report, ReportDto.class));
        } catch (Exception e) {
            logger.error("Failed to create report in backend-service", e);
            return Optional.empty();
        }
    }

    // ---------- Audit ----------

    public Optional<AuditLogDto> createAuditLog(AuditLogDto auditLog) {
        try {
            return Optional.ofNullable(restTemplate.postForObject(baseUrl + "/api/audit", auditLog, AuditLogDto.class));
        } catch (Exception e) {
            logger.error("Failed to create audit log in backend-service", e);
            return Optional.empty();
        }
    }

    public List<AuditLogDto> getAllAuditLogs() {
        try {
            AuditLogDto[] result = restTemplate.getForObject(baseUrl + "/api/audit", AuditLogDto[].class);
            return result != null ? List.of(result) : Collections.emptyList();
        } catch (Exception e) {
            logger.error("Failed to fetch audit logs from backend-service", e);
            return Collections.emptyList();
        }
    }

    // ---------- API Keys ----------

    public Optional<ApiKeyDto> createApiKey(String apiKey, String partnerName, String permissions) {
        try {
            var body = new java.util.HashMap<String, String>();
            body.put("apiKey", apiKey);
            body.put("partnerName", partnerName);
            body.put("permissions", permissions != null ? permissions : "");
            return Optional.ofNullable(restTemplate.postForObject(baseUrl + "/api/api-keys", body, ApiKeyDto.class));
        } catch (Exception e) {
            logger.error("Failed to create API key in backend-service", e);
            return Optional.empty();
        }
    }

    public List<ApiKeyDto> getApiKeys() {
        try {
            ApiKeyDto[] result = restTemplate.getForObject(baseUrl + "/api/api-keys", ApiKeyDto[].class);
            return result != null ? List.of(result) : Collections.emptyList();
        } catch (Exception e) {
            logger.error("Failed to fetch API keys from backend-service", e);
            return Collections.emptyList();
        }
    }

    public boolean revokeApiKey(String apiKey) {
        try {
            var body = new java.util.HashMap<String, String>();
            body.put("apiKey", apiKey);
            restTemplate.postForObject(baseUrl + "/api/api-keys/revoke", body, ApiKeyDto.class);
            return true;
        } catch (Exception e) {
            logger.error("Failed to revoke API key in backend-service", e);
            return false;
        }
    }

    // ---------- Specialization ----------

    public List<SpecializationDto> getAllSpecializations() {
        try {
            SpecializationDto[] result = restTemplate.getForObject(baseUrl + "/api/specialization", SpecializationDto[].class);
            return result != null ? List.of(result) : Collections.emptyList();
        } catch (Exception e) {
            logger.error("Failed to fetch specializations from backend-service", e);
            return Collections.emptyList();
        }
    }

    public Optional<SpecializationDto> getSpecializationById(Long id) {
        try {
            return Optional.ofNullable(restTemplate.getForObject(baseUrl + "/api/specialization/" + id, SpecializationDto.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Failed to fetch specialization {} from backend-service", id, e);
            return Optional.empty();
        }
    }

    public Optional<SpecializationDto> createSpecialization(SpecializationDto specialization) {
        try {
            return Optional.ofNullable(restTemplate.postForObject(baseUrl + "/api/specialization", specialization, SpecializationDto.class));
        } catch (Exception e) {
            logger.error("Failed to create specialization in backend-service", e);
            return Optional.empty();
        }
    }

    public boolean updateSpecialization(Long id, SpecializationDto specialization) {
        try {
            restTemplate.put(baseUrl + "/api/specialization/" + id, specialization);
            return true;
        } catch (Exception e) {
            logger.error("Failed to update specialization {} in backend-service", id, e);
            return false;
        }
    }

    public boolean deleteSpecialization(Long id) {
        try {
            restTemplate.delete(baseUrl + "/api/specialization/" + id);
            return true;
        } catch (Exception e) {
            logger.error("Failed to delete specialization {} in backend-service", id, e);
            return false;
        }
    }

    // ---------- Disease ----------

    public List<DiseaseDto> getAllDiseases() {
        try {
            DiseaseDto[] result = restTemplate.getForObject(baseUrl + "/api/disease", DiseaseDto[].class);
            return result != null ? List.of(result) : Collections.emptyList();
        } catch (Exception e) {
            logger.error("Failed to fetch diseases from backend-service", e);
            return Collections.emptyList();
        }
    }

    public Optional<DiseaseDto> getDiseaseById(Long id) {
        try {
            return Optional.ofNullable(restTemplate.getForObject(baseUrl + "/api/disease/" + id, DiseaseDto.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Failed to fetch disease {} from backend-service", id, e);
            return Optional.empty();
        }
    }

    public Optional<DiseaseDto> createDisease(DiseaseDto disease) {
        try {
            return Optional.ofNullable(restTemplate.postForObject(baseUrl + "/api/disease", disease, DiseaseDto.class));
        } catch (Exception e) {
            logger.error("Failed to create disease in backend-service", e);
            return Optional.empty();
        }
    }

    public boolean updateDisease(Long id, DiseaseDto disease) {
        try {
            restTemplate.put(baseUrl + "/api/disease/" + id, disease);
            return true;
        } catch (Exception e) {
            logger.error("Failed to update disease {} in backend-service", id, e);
            return false;
        }
    }

    public boolean deleteDisease(Long id) {
        try {
            restTemplate.delete(baseUrl + "/api/disease/" + id);
            return true;
        } catch (Exception e) {
            logger.error("Failed to delete disease {} in backend-service", id, e);
            return false;
        }
    }

    // ---------- Appointment ----------

    public Optional<AppointmentDto> bookAppointment(AppointmentRequestDto request) {
        try {
            return Optional.ofNullable(restTemplate.postForObject(
                    baseUrl + "/api/appointment", request, AppointmentDto.class));
        } catch (Exception e) {
            logger.error("Failed to book appointment in backend-service", e);
            return Optional.empty();
        }
    }

    public Optional<AppointmentDto> getAppointmentById(Long id) {
        try {
            return Optional.ofNullable(restTemplate.getForObject(
                    baseUrl + "/api/appointment/" + id, AppointmentDto.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Failed to fetch appointment {} from backend-service", id, e);
            return Optional.empty();
        }
    }

    public List<AppointmentDto> getAppointmentsByPatient(Long patientId) {
        try {
            AppointmentDto[] result = restTemplate.getForObject(
                    baseUrl + "/api/appointment/patient/" + patientId, AppointmentDto[].class);
            return result != null ? List.of(result) : Collections.emptyList();
        } catch (Exception e) {
            logger.error("Failed to fetch appointments for patient {} from backend-service", patientId, e);
            return Collections.emptyList();
        }
    }

    public List<AppointmentDto> getAppointmentsByDoctor(Long doctorId) {
        try {
            AppointmentDto[] result = restTemplate.getForObject(
                    baseUrl + "/api/appointment/doctor/" + doctorId, AppointmentDto[].class);
            return result != null ? List.of(result) : Collections.emptyList();
        } catch (Exception e) {
            logger.error("Failed to fetch appointments for doctor {} from backend-service", doctorId, e);
            return Collections.emptyList();
        }
    }

    public Optional<AvailableSlotsResponseDto> getAvailableSlots(Long doctorId, String date) {
        try {
            return Optional.ofNullable(restTemplate.getForObject(
                    baseUrl + "/api/appointment/doctor/" + doctorId + "/available-slots?date=" + date,
                    AvailableSlotsResponseDto.class));
        } catch (HttpClientErrorException e) {
            // 404 (no doctor) and 409 (no schedule that day) both degrade to
            // empty Optional so the form can show a friendly message.
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Failed to fetch available slots for doctor {} on {} from backend-service",
                    doctorId, date, e);
            return Optional.empty();
        }
    }

    /**
     * Returns the next {@code days} dates (default 14) starting today that
     * this doctor has a schedule row on, plus per-date slot counts and a
     * fully-booked flag. Empty list when the doctor has no schedule rows at
     * all. Used by the booking form's calendar highlight.
     */
    public List<com.gms.gateway.dto.DoctorAvailabilityDateDto> getDoctorAvailability(Long doctorId, int days) {
        try {
            com.gms.gateway.dto.DoctorAvailabilityDateDto[] result = restTemplate.getForObject(
                    baseUrl + "/api/appointment/doctor/" + doctorId + "/availability?days=" + days,
                    com.gms.gateway.dto.DoctorAvailabilityDateDto[].class);
            return result != null ? java.util.Arrays.asList(result) : Collections.emptyList();
        } catch (Exception e) {
            logger.error("Failed to fetch availability for doctor {} from backend-service", doctorId, e);
            return Collections.emptyList();
        }
    }

    /**
     * PATCH /api/doctor-schedule/{id}/capacity — admin-only capacity override.
     */
    public boolean updateScheduleCapacity(Long scheduleId, int capacity) {
        try {
            java.util.Map<String, Integer> body = new java.util.HashMap<>();
            body.put("capacityPerSlot", capacity);
            org.springframework.http.HttpEntity<java.util.Map<String, Integer>> entity =
                    new org.springframework.http.HttpEntity<>(body);
            restTemplate.exchange(
                    baseUrl + "/api/doctor-schedule/" + scheduleId + "/capacity",
                    org.springframework.http.HttpMethod.PATCH,
                    entity,
                    org.springframework.http.HttpStatus.class);
            return true;
        } catch (Exception e) {
            logger.error("Failed to update capacity for schedule {}", scheduleId, e);
            return false;
        }
    }

    public Optional<AppointmentDto> cancelAppointment(Long id) {
        try {
            restTemplate.delete(baseUrl + "/api/appointment/" + id);
            return getAppointmentById(id);
        } catch (Exception e) {
            logger.error("Failed to cancel appointment {} in backend-service", id, e);
            return Optional.empty();
        }
    }

    public Optional<AppointmentDto> updateAppointmentStatus(Long id, String status) {
        try {
            restTemplate.exchange(
                    baseUrl + "/api/appointment/" + id + "/status?status=" + status,
                    org.springframework.http.HttpMethod.PATCH,
                    HttpEntity.EMPTY,
                    AppointmentDto.class);
            return getAppointmentById(id);
        } catch (Exception e) {
            logger.error("Failed to update appointment {} status to {} in backend-service", id, status, e);
            return Optional.empty();
        }
    }

    // ---------- Doctor Schedule ----------

    public List<DoctorScheduleDto> getDoctorSchedules(Long doctorId) {
        try {
            DoctorScheduleDto[] result = restTemplate.getForObject(
                    baseUrl + "/api/doctor-schedule/doctor/" + doctorId, DoctorScheduleDto[].class);
            return result != null ? List.of(result) : Collections.emptyList();
        } catch (Exception e) {
            logger.error("Failed to fetch doctor schedules for {} from backend-service", doctorId, e);
            return Collections.emptyList();
        }
    }

    public Optional<DoctorScheduleDto> createDoctorSchedule(DoctorScheduleDto schedule) {
        try {
            return Optional.ofNullable(restTemplate.postForObject(
                    baseUrl + "/api/doctor-schedule", schedule, DoctorScheduleDto.class));
        } catch (Exception e) {
            logger.error("Failed to create doctor schedule in backend-service", e);
            return Optional.empty();
        }
    }

    public boolean updateDoctorSchedule(Long id, DoctorScheduleDto schedule) {
        try {
            restTemplate.put(baseUrl + "/api/doctor-schedule/" + id, schedule);
            return true;
        } catch (Exception e) {
            logger.error("Failed to update doctor schedule {} in backend-service", id, e);
            return false;
        }
    }

    public boolean deleteDoctorSchedule(Long id) {
        try {
            restTemplate.delete(baseUrl + "/api/doctor-schedule/" + id);
            return true;
        } catch (Exception e) {
            logger.error("Failed to delete doctor schedule {} in backend-service", id, e);
            return false;
        }
    }
}
