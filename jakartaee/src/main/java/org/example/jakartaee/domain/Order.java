package org.example.jakartaee.domain;

public class Order {
    private String id;
    private String customerId;
    private String status;

    public Order(String id, String customerId) {
        this.id = id;
        this.customerId = customerId;
        this.status = "SUBMITTED";
    }

    public String getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getStatus() {
        return status;
    }
}
