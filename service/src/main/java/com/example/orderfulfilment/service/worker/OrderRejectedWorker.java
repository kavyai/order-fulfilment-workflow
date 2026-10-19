
package com.example.orderfulfilment.service.worker;

import com.example.orderfulfilment.service.repository.OrderRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.camunda.bpm.client.ExternalTaskClient;
import org.springframework.stereotype.Component;

@Component
public class OrderRejectedWorker {

    private final OrderRepository orderRepository;
    private ExternalTaskClient client;

    public OrderRejectedWorker(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @PostConstruct
    public void startWorker() {
        client = ExternalTaskClient.create()
                .baseUrl("http://localhost:8080/engine-rest")
                .asyncResponseTimeout(1000)
                .build();

        client.subscribe("order-rejected")
                .lockDuration(10000)
                .handler((externalTask, externalTaskService) -> {
                    String orderId =
                            externalTask.getVariable("orderId");
                    String reason =
                            externalTask.getVariable("rejectionReason");

                    rejectOrder(orderId, reason);

                    System.out.println("Order rejected: " + orderId);
                    System.out.println("Reason: " + reason);

                    externalTaskService.complete(externalTask);
                })
                .open();
    }

    public void rejectOrder(String orderId, String reason) {
        if (orderId == null) {
            return;
        }

        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus("REJECTED");
            order.setRejectionReason(reason);
            orderRepository.save(order);
        });
    }

    @PreDestroy
    public void stopWorker() {
        if (client != null) {
            client.stop();
        }
    }
}
