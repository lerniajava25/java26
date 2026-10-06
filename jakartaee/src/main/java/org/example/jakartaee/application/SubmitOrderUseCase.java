package org.example.jakartaee.application;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.example.jakartaee.domain.Order;
import org.example.jakartaee.domain.OrderExternalService;

import java.util.UUID;

@Dependent
public class SubmitOrderUseCase {
    @Inject
    private OrderExternalService externalService;

    public String execute(String customerId) {
        // Skapa domänobjekt
        String orderId = UUID.randomUUID().toString();
        Order order = new Order(orderId, customerId);
        // Skicka vidare till det externa API:et via infrastrukturlagret
        externalService.sendOrderToExternalApi(order);
        return orderId;
    }
}
