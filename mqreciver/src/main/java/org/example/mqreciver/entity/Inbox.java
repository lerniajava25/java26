package org.example.mqreciver.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
public class Inbox {
    @Id
    private UUID messageId;
    private String payload;
    private LocalDateTime processedAt = LocalDateTime.now();

    public Inbox() {
    }

    public Inbox(UUID messageId, String payload) {
        this.messageId = messageId;
        this.payload = payload;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public void setMessageId(UUID messageId) {
        this.messageId = messageId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
}
