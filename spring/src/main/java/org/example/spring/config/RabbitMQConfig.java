package org.example.spring.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public DirectExchange courseExchange() {
        return new DirectExchange("course-exchange", true, false);
    }

    @Bean
    public Queue courseQueue() {
        return new Queue("course-created", true);
    }

    @Bean
    public Binding courseBinding(DirectExchange courseExchange, Queue courseQueue) {
        return BindingBuilder
                .bind(courseQueue)
                .to(courseExchange)
                .with("course.created");
    }
}
