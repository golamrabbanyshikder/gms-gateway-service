package com.gms.gateway.client;

import com.gms.gateway.dto.AuditLogDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Convenience wrapper around BackendServiceClient.createAuditLog. Audit
 * logging must never break the user-facing request, so all failures are
 * caught and logged here rather than propagated.
 */
@Component
public class AuditClient {

    private static final Logger logger = LoggerFactory.getLogger(AuditClient.class);

    private final BackendServiceClient backendServiceClient;

    public AuditClient(BackendServiceClient backendServiceClient) {
        this.backendServiceClient = backendServiceClient;
    }

    public void log(Long userId, String username, String action, String entityType, Long entityId, String details, String ipAddress) {
        try {
            AuditLogDto auditLog = new AuditLogDto();
            auditLog.setUserId(userId);
            auditLog.setUsername(username);
            auditLog.setAction(action);
            auditLog.setEntityType(entityType);
            auditLog.setEntityId(entityId);
            auditLog.setDetails(details);
            auditLog.setIpAddress(ipAddress);
            backendServiceClient.createAuditLog(auditLog);
        } catch (Exception e) {
            logger.warn("Failed to write audit log for action {} on {}/{}", action, entityType, entityId, e);
        }
    }
}
