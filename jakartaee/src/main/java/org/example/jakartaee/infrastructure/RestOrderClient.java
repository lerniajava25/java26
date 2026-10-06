package org.example.jakartaee.infrastructure;

import jakarta.enterprise.context.ApplicationScoped;
import org.example.jakartaee.domain.Order;
import org.example.jakartaee.domain.OrderExternalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class RestOrderClient implements OrderExternalService {
    Logger logger = LoggerFactory.getLogger(RestOrderClient.class);

    @Override
    public void sendOrderToExternalApi(Order order) {
        // Skapa en enkel DTO eller skicka domänobjektet direkt till det externa API:et
        ExternalOrderDto dto = new ExternalOrderDto(order.getId(), order.getCustomerId(), order.getStatus());
//        try (Client client = ClientBuilder.newClient()) {
//            client.target("https://api.external-system.com/v1/orders")
//                    .request(MediaType.APPLICATION_JSON)
//                    .post(Entity.entity(dto, MediaType.APPLICATION_JSON));
//        }
        logger.info("Sending order to external API: {}", dto);
    }

    // Intern DTO för det externa API:ets format (om det skiljer sig från domänen)
    public static class ExternalOrderDto {
        public String orderId;
        public String customerId;
        public String status;

        public ExternalOrderDto(String orderId, String customerId, String status) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.status = status;
        }

        @Override
        public String toString() {
            return "ExternalOrderDto{" +
                    "orderId='" + orderId + '\'' +
                    ", customerId='" + customerId + '\'' +
                    ", status='" + status + '\'' +
                    '}';
        }
    }
}
