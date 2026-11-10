package org.example.mqreciver;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class CourseConsumer {

    @RabbitListener(queues = "course-created")
    public void receiveMessage(String message,
                               @Header(name = "custom-header") String customHeader
    ) {
        System.out.println("Message received " + message);
        System.out.println("Custom header " + customHeader);
    }
}
