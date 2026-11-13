package org.example.spring.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    @Bean
    public DirectExchange courseExchange() {
        return new DirectExchange("course-exchange", true, false);
    }

    @Bean
    public Queue courseQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", "course-dlq-exchange");
        args.put("x-dead-letter-routing-key", "course.created.dlq");
        return new Queue("course-created", true, false, false, args);
    }

    @Bean
    public Binding courseBinding(DirectExchange courseExchange, Queue courseQueue) {
        return BindingBuilder
                .bind(courseQueue)
                .to(courseExchange)
                .with("course.created");
    }

    @Bean
    public DirectExchange courseDlqExchange() {
        return new DirectExchange("course-dlq-exchange", true, false);
    }

    @Bean
    public Queue courseDlqQueue() {
        return new Queue("course-created-dlq", true);
    }

    @Bean
    public Binding courseDlqBinding(DirectExchange courseDlqExchange, Queue courseDlqQueue) {
        return BindingBuilder
                .bind(courseDlqQueue)
                .to(courseDlqExchange)
                .with("course.created.dlq");
    }
}
