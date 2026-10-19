package com.example.orderfulfilment.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class OrderRequest {

    @NotBlank(message = "Order ID is required")
    private String orderId;

    @NotBlank(message = "Customer ID is required")
    private String customerId;

    @NotNull(message = "Amount is required")
    @DecimalMin(
            value = "0.0",
            inclusive = false,
            message = "Amount must be greater than zero"
    )
    private Double amount;

    @NotBlank(message = "Notification preference is required")
    private String preferredNotification;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getPreferredNotification() {
        return preferredNotification;
    }

    public void setPreferredNotification(String preferredNotification) {
        this.preferredNotification = preferredNotification;
    }
}