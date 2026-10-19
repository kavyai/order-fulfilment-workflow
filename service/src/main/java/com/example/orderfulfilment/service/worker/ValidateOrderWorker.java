
package com.example.orderfulfilment.service.worker;

import com.example.orderfulfilment.service.entity.OrderEntity;
import com.example.orderfulfilment.service.repository.OrderRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.camunda.bpm.client.ExternalTaskClient;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ValidateOrderWorker {

    private final OrderRepository orderRepository;
    private ExternalTaskClient client;

    public ValidateOrderWorker(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @PostConstruct
    public void startWorker() {
        client = ExternalTaskClient.create()
                .baseUrl("http://localhost:8080/engine-rest")
                .asyncResponseTimeout(1000)
                .build();

        client.subscribe("validate-order")
                .lockDuration(10000)
                .handler((externalTask, externalTaskService) -> {

                    String orderId =
                            externalTask.getVariable("orderId");
                    String customerId =
                            externalTask.getVariable("customerId");
                    Number amountValue =
                            externalTask.getVariable("amount");

                    String rejectionReason = null;

                    if (orderId == null || orderId.isBlank()) {
                        rejectionReason = "Order ID is required";
                    } else if (customerId == null
                            || customerId.isBlank()) {
                        rejectionReason = "Customer ID is required";
                    } else if (amountValue == null
                            || !Double.isFinite(amountValue.doubleValue())
                            || amountValue.doubleValue() <= 0) {
                        rejectionReason =
                                "Amount must be greater than zero";
                    } else if (!"CUST-101".equals(customerId)) {
                        rejectionReason = "Unknown customer";
                    }

                    boolean valid = rejectionReason == null;

                    // Make the reason effectively final for the lambda.
                    final String finalRejectionReason = rejectionReason;

                    System.out.println("Validating order: " + orderId);
                    System.out.println("Validation result: " + valid);

                    if (!valid) {
                        System.out.println(
                                "Rejection reason: " + rejectionReason);
                    }

                    if (orderId != null) {
                        orderRepository.findById(orderId)
                                .ifPresent(order -> {

                                    if (valid) {
                                        order.setStatus("VALIDATED");
                                        order.setRejectionReason(null);
                                    } else {
                                        order.setStatus("REJECTED");
                                        order.setRejectionReason(
                                                finalRejectionReason);
                                    }

                                    orderRepository.save(order);
                                });
                    }

                    externalTaskService.complete(
                            externalTask,
                            Map.of(
                                    "valid", valid,
                                    "rejectionReason",
                                    rejectionReason == null
                                            ? "" : rejectionReason
                            )
                    );
                })
                .open();
    }

    @PreDestroy
    public void stopWorker() {
        if (client != null) {
            client.stop();
        }
    }
    static String validateOrder(
            String orderId,
            String customerId,
            Number amountValue) {

        if (orderId == null || orderId.isBlank()) {
            return "Order ID is required";
        } else if (customerId == null || customerId.isBlank()) {
            return "Customer ID is required";
        } else if (amountValue == null
                || !Double.isFinite(amountValue.doubleValue())
                || amountValue.doubleValue() <= 0) {
            return "Amount must be greater than zero";
        } else if (!"CUST-101".equals(customerId)) {
            return "Unknown customer";
        }

        return null;
    }
}
