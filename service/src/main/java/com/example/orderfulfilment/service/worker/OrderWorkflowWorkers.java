
package com.example.orderfulfilment.service.worker;

import com.example.orderfulfilment.service.repository.OrderRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OrderWorkflowWorkers {

    private final OrderRepository orderRepository;
    private ExternalTaskClient client;

    public OrderWorkflowWorkers(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @PostConstruct
    public void startWorkers() {
        client = ExternalTaskClient.create()
                .baseUrl("http://localhost:8080/engine-rest")
                .asyncResponseTimeout(1000)
                .build();

        subscribe("update-sla-log", (task, service) -> {
            System.out.println(
                    "Priority update logged for order: "
                            + task.getVariable("orderId"));

            service.complete(task, Map.of("slaLogged", true));
        });

        subscribe("order-auto-cancel", (task, service) -> {
            String orderId = task.getVariable("orderId");

            autoCancelOrder(orderId);

            System.out.println("Order auto-cancelled: " + orderId);
            service.complete(task);
        });

        subscribe("reserve-inventory", (task, service) -> {
            String orderId = task.getVariable("orderId");

            System.out.println("Reserving inventory for: " + orderId);
            service.complete(task, Map.of("inventoryReserved", true));
        });

        subscribe("generate-invoice", (task, service) -> {
            String orderId = task.getVariable("orderId");

            System.out.println("Generating invoice for: " + orderId);
            service.complete(task, Map.of("invoiceGenerated", true));
        });

        subscribe("send-email", (task, service) -> {
            String orderId = task.getVariable("orderId");

            System.out.println(
                    "Email notification processed for: " + orderId);
            service.complete(task, Map.of("emailNotificationSent", true));
        });

        subscribe("send-sms", (task, service) -> {
            String orderId = task.getVariable("orderId");

            System.out.println(
                    "SMS notification processed for: " + orderId);
            service.complete(task, Map.of("smsNotificationSent", true));
        });

        subscribe("mark-order-fulfilled", (task, service) -> {
            String orderId = task.getVariable("orderId");

            markOrderFulfilled(orderId);

            System.out.println("Order fulfilled: " + orderId);
            service.complete(task);
        });
    }

    public void autoCancelOrder(String orderId) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus("AUTO_CANCELLED");
            order.setRejectionReason(
                    "Credit review timed out after 10 minutes");
            orderRepository.save(order);
        });
    }

    public void markOrderFulfilled(String orderId) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus("FULFILLED");
            order.setRejectionReason(null);
            orderRepository.save(order);
        });
    }

    private void subscribe(
            String topic,
            ExternalTaskHandler handler) {

        client.subscribe(topic)
                .lockDuration(10000)
                .handler(handler)
                .open();
    }

    @PreDestroy
    public void stopWorkers() {
        if (client != null) {
            client.stop();
        }
    }
}
