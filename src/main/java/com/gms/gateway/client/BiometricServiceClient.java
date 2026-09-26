package com.gms.gateway.client;

import com.gms.gateway.dto.ApiKeyDto;
import com.gms.gateway.dto.BiometricDto;
import com.gms.gateway.dto.IdentificationResultDto;
import com.gms.gateway.dto.VerificationResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * HTTP client over biometric-service. verify/identify send the raw biometric
 * sample bytes as the request body with content-type application/octet-stream
 * (NOT JSON) per the biometric-service contract.
 */
@Component
public class BiometricServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(BiometricServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public BiometricServiceClient(RestTemplate restTemplate, @Value("${services.biometric-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public Optional<BiometricDto> enroll(BiometricDto biometric) {
        try {
            return Optional.ofNullable(restTemplate.postForObject(baseUrl + "/api/biometric/register", biometric, BiometricDto.class));
        } catch (Exception e) {
            logger.error("Failed to enroll biometric in biometric-service", e);
            return Optional.empty();
        }
    }

    public Optional<VerificationResultDto> verify(Long biometricId, byte[] template) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            HttpEntity<byte[]> request = new HttpEntity<>(template, headers);
            VerificationResultDto result = restTemplate.postForObject(
                    baseUrl + "/api/biometric/verify/" + biometricId, request, VerificationResultDto.class);
            return Optional.ofNullable(result);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Failed to verify biometric {} via biometric-service", biometricId, e);
            return Optional.empty();
        }
    }

    public Optional<IdentificationResultDto> identify(byte[] template, String typeFilter) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            HttpEntity<byte[]> request = new HttpEntity<>(template, headers);

            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/biometric/identify");
            if (typeFilter != null && !typeFilter.isBlank()) {
                builder.queryParam("type", typeFilter);
            }

            IdentificationResultDto result = restTemplate.postForObject(
                    builder.toUriString(), request, IdentificationResultDto.class);
            return Optional.ofNullable(result);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Failed to identify biometric via biometric-service", e);
            return Optional.empty();
        }
    }

    public List<BiometricDto> getByPatient(Long patientId) {
        try {
            BiometricDto[] result = restTemplate.getForObject(baseUrl + "/api/biometric/patient/" + patientId, BiometricDto[].class);
            return result != null ? List.of(result) : Collections.emptyList();
        } catch (Exception e) {
            logger.error("Failed to fetch biometrics for patient {} from biometric-service", patientId, e);
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
            logger.error("Failed to create API key in biometric-service", e);
            return Optional.empty();
        }
    }

    public boolean revokeApiKey(String apiKey) {
        try {
            var body = new java.util.HashMap<String, String>();
            body.put("apiKey", apiKey);
            restTemplate.postForObject(baseUrl + "/api/api-keys/revoke", body, ApiKeyDto.class);
            return true;
        } catch (Exception e) {
            logger.error("Failed to revoke API key in biometric-service", e);
            return false;
        }
    }
}
