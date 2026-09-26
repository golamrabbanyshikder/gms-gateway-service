package com.gms.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadataDto {
    private Long id;
    private String fileGroupId;
    private int version;
    private String originalFileName;
    private String storedFileName;
    private String contentType;
    private long fileSizeBytes;
    private String relatedEntityType;
    private Long relatedEntityId;
    private LocalDateTime createdAt;
}
