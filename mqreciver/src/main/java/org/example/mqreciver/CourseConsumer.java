package org.example.mqreciver;

import org.example.mqreciver.entity.Inbox;
import org.example.mqreciver.repository.InboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class CourseConsumer {

    private static final Logger logger = LoggerFactory.getLogger(CourseConsumer.class);

    private final InboxRepository inboxRepository;

    @Value("${inbox.simulate-process-error:false}")
    private boolean simulateProcessError;

    public CourseConsumer(InboxRepository inboxRepository) {
        this.inboxRepository = inboxRepository;
    }

    @RabbitListener(queues = "course-created")
    @Transactional
    public void receiveMessage(String message,
                               @Header(name = "message-id", required = false) String messageId,
                               @Header(name = "custom-header") String customHeader
    ) {
        logger.info("[INBOX] Received message [messageId={}, customHeader={}]", messageId, customHeader);

        if (messageId == null) {
            logger.warn("[INBOX] ⚠ Message has no message-id — processing without inbox tracking: {}", message);
            processMessage(message, customHeader);
            return;
        }

        UUID messageUuid;
        try {
            messageUuid = UUID.fromString(messageId);
        } catch (IllegalArgumentException e) {
            logger.warn("[INBOX] ⚠ Invalid message-id format: {} — processing without inbox tracking: {}", messageId, message);
            processMessage(message, customHeader);
            return;
        }

        if (inboxRepository.existsById(messageUuid)) {
            logger.info("[INBOX] ✓ Duplicate message ignored [id={}] — already processed", messageUuid);
            return;
        }

        logger.info("[INBOX] New message [id={}] — saving to inbox and processing", messageUuid);
        var inbox = new Inbox(messageUuid, message);
        inboxRepository.save(inbox);
        logger.info("[INBOX] ✓ Saved to inbox [id={}]", messageUuid);
        try {
            processMessage(message, customHeader);
        } catch (RuntimeException e) {
            throw new AmqpRejectAndDontRequeueException("Send to DLQ", e);
        }
        logger.info("[INBOX] ✓ Message processed successfully [id={}]", messageUuid);
    }

    private void processMessage(String message, String customHeader) {
        if (simulateProcessError) {
            logger.error("[INBOX] ✗ SIMULATED ERROR — processing failure configured via inbox.simulate-process-error=true");
            throw new RuntimeException("Simulated processing failure for testing DLQ");
        }
        logger.info("[INBOX] Processing message payload: {}", message);
        logger.info("[INBOX] Custom header: {}", customHeader);
    }
}
