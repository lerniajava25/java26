package org.example.spring.service;

import org.example.spring.entity.Outbox;
import org.example.spring.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OutboxPublisher {

    private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final RabbitMessagingTemplate messagingTemplate;

    @Value("${outbox.simulate-publish-error:false}")
    private boolean simulatePublishError;

    @Value("${outbox.simulate-duplicate-publish:false}")
    private boolean simulateDuplicatePublish;

    public OutboxPublisher(OutboxRepository outboxRepository, RabbitMessagingTemplate messagingTemplate) {
        this.outboxRepository = outboxRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-delay:5000}")
    @Transactional
    public void publishPendingMessages() {
        List<Outbox> pendingMessages = outboxRepository.findAllByPublishedFalse();
        logger.info("[OUTBOX] Polling started — found {} pending message(s) to publish", pendingMessages.size());

        if (pendingMessages.isEmpty()) {
            logger.info("[OUTBOX] Nothing to do — waiting for next poll cycle");
            return;
        }

        for (Outbox outbox : pendingMessages) {
            try {
                logger.info("[OUTBOX] Attempting to publish message [id={}, type={}, payload={}]",
                        outbox.getId(), outbox.getMessageType(), outbox.getPayload());

                if (simulatePublishError) {
                    logger.warn("[OUTBOX] ⚠ SIMULATED ERROR — publish failure configured via outbox.simulate-publish-error=true");
                    throw new RuntimeException("Simulated publish failure for testing");
                }

                var message = MessageBuilder.withPayload(outbox.getPayload())
                        .setHeader("message-id", outbox.getId().toString())
                        .setHeader("custom-header", "header value")
                        .build();

                messagingTemplate.send("course-exchange", "course.created", message);
                logger.info("[OUTBOX] ✓ Successfully published message [id={}] to exchange 'course-exchange'", outbox.getId());

                if (simulateDuplicatePublish) {
                    logger.warn("[OUTBOX] ⚠ SIMULATED DUPLICATE — re-publishing message [id={}] for testing inbox duplicate detection", outbox.getId());
                    try {
                        messagingTemplate.send("course-exchange", "course.created", message);
                        logger.info("[OUTBOX] ✓ Duplicate published [id={}] — inbox should ignore this", outbox.getId());
                    } catch (Exception e) {
                        logger.error("[OUTBOX] ✗ Failed to publish duplicate [id={}]: {}", outbox.getId(), e.getMessage());
                    }
                }

                outbox.setPublished(true);
                outboxRepository.save(outbox);
            } catch (Exception e) {
                logger.error("[OUTBOX] ✗ Failed to publish message [id={}]: {}. Message will be retried on next poll.",
                        outbox.getId(), e.getMessage());
            }
        }

        logger.info("[OUTBOX] Poll cycle finished");
    }
}
