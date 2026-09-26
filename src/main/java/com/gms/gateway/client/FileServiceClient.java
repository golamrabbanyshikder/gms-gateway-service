package com.gms.gateway.client;

import com.gms.gateway.dto.ApiKeyDto;
import com.gms.gateway.dto.FileMetadataDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * HTTP client over file-system-service. Upload uses multipart/form-data,
 * download returns raw bytes; gateway acts as the authenticated proxy.
 */
@Component
public class FileServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(FileServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public FileServiceClient(RestTemplate restTemplate, @Value("${services.filesystem-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public Optional<FileMetadataDto> upload(MultipartFile file, String fileGroupId, String relatedEntityType, Long relatedEntityId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            body.add("file", fileResource);
            if (fileGroupId != null && !fileGroupId.isBlank()) {
                body.add("fileGroupId", fileGroupId);
            }
            if (relatedEntityType != null && !relatedEntityType.isBlank()) {
                body.add("relatedEntityType", relatedEntityType);
            }
            if (relatedEntityId != null) {
                body.add("relatedEntityId", relatedEntityId);
            }

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<FileMetadataDto> response = restTemplate.postForEntity(
                    baseUrl + "/api/files/upload", requestEntity, FileMetadataDto.class);
            return Optional.ofNullable(response.getBody());
        } catch (IOException e) {
            logger.error("Failed to read uploaded file bytes", e);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Failed to upload file to file-system-service", e);
            return Optional.empty();
        }
    }

    public static class FileDownload {
        private final byte[] data;
        private final String contentType;
        private final String fileName;

        public FileDownload(byte[] data, String contentType, String fileName) {
            this.data = data;
            this.contentType = contentType;
            this.fileName = fileName;
        }

        public byte[] getData() {
            return data;
        }

        public String getContentType() {
            return contentType;
        }

        public String getFileName() {
            return fileName;
        }
    }

    public Optional<FileDownload> download(Long id) {
        try {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(baseUrl + "/api/files/download/" + id, byte[].class);
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                return Optional.empty();
            }
            HttpHeaders headers = response.getHeaders();
            String contentType = headers.getContentType() != null ? headers.getContentType().toString() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
            String fileName = extractFileName(headers);
            return Optional.of(new FileDownload(response.getBody(), contentType, fileName));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Failed to download file {} from file-system-service", id, e);
            return Optional.empty();
        }
    }

    private String extractFileName(HttpHeaders headers) {
        String disposition = headers.getFirst(HttpHeaders.CONTENT_DISPOSITION);
        if (disposition != null && disposition.contains("filename=")) {
            String name = disposition.substring(disposition.indexOf("filename=") + 9);
            return name.replace("\"", "").trim();
        }
        return "download";
    }

    public List<FileMetadataDto> getVersions(String fileGroupId) {
        try {
            FileMetadataDto[] result = restTemplate.getForObject(baseUrl + "/api/files/versions/" + fileGroupId, FileMetadataDto[].class);
            return result != null ? List.of(result) : Collections.emptyList();
        } catch (Exception e) {
            logger.error("Failed to fetch file versions for group {} from file-system-service", fileGroupId, e);
            return Collections.emptyList();
        }
    }

    public boolean delete(Long id) {
        try {
            restTemplate.delete(baseUrl + "/api/files/delete/" + id);
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (Exception e) {
            logger.error("Failed to delete file {} via file-system-service", id, e);
            return false;
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
            logger.error("Failed to create API key in file-system-service", e);
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
            logger.error("Failed to revoke API key in file-system-service", e);
            return false;
        }
    }
}
