
package com.example.orderfulfilment.api;

import com.example.orderfulfilment.service.entity.OrderEntity;
import com.example.orderfulfilment.service.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Set<String> NOTIFICATION_OPTIONS =
            Set.of("EMAIL", "SMS", "BOTH", "NONE");

    private final OrderRepository orderRepository;

    private final RestClient camundaClient =
            RestClient.create(
                    "http://localhost:8080/engine-rest");

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @PostMapping
    public Map<String, Object> createOrder(
            @RequestBody Map<String, Object> request) {

        // 1. Read and validate the basic order fields.
        String orderId = request.get("orderId") instanceof String
                ? (String) request.get("orderId")
                : null;

        String customerId = request.get("customerId") instanceof String
                ? (String) request.get("customerId")
                : null;

        Object amountValue = request.get("amount");

        if (orderId == null || orderId.isBlank()
                || customerId == null || customerId.isBlank()
                || !(amountValue instanceof Number)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "orderId, customerId and numeric amount are required");
        }

        double amount = ((Number) amountValue).doubleValue();

        if (!Double.isFinite(amount) || amount <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Amount must be greater than zero");
        }

        // 2. Read and validate notification preference.
        String preference =
                request.get("notificationPreference") instanceof String
                        ? ((String) request.get("notificationPreference"))
                        .trim().toUpperCase()
                        : "BOTH";

        if (!NOTIFICATION_OPTIONS.contains(preference)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "notificationPreference must be EMAIL, SMS, BOTH or NONE");
        }

        // 3. Prevent duplicate order IDs.
        if (orderRepository.existsById(orderId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Order ID already exists: " + orderId);
        }

        // 4. Save the order before starting the Camunda process.
        OrderEntity order = new OrderEntity();
        order.setOrderId(orderId);
        order.setCustomerId(customerId);
        order.setAmount(amount);
        order.setStatus("PROCESS_STARTING");

        orderRepository.save(order);

        // 5. Prepare Camunda process variables.
        Map<String, Object> variables = Map.of(
                "orderId",
                Map.of("value", orderId, "type", "String"),

                "customerId",
                Map.of("value", customerId, "type", "String"),

                "amount",
                Map.of("value", amount, "type", "Double"),

                "notificationPreference",
                Map.of("value", preference, "type", "String")
        );

        Map<String, Object> camundaRequest = Map.of(
                "businessKey", orderId,
                "variables", variables
        );

        // 6. Start the Camunda workflow.
        try {
            Map<?, ?> result = camundaClient.post()
                    .uri("/process-definition/key/orderFulfilmentProcess/start")
                    .body(camundaRequest)
                    .retrieve()
                    .body(Map.class);

            if (result == null || result.get("id") == null) {
                throw new IllegalStateException(
                        "Camunda did not return a process instance ID");
            }

            // 7. Store the process instance ID.
            OrderEntity latest = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Order was not found after being saved: " + orderId));

            latest.setProcessInstanceId(result.get("id").toString());

            if ("PROCESS_STARTING".equals(latest.getStatus())) {
                latest.setStatus("PROCESS_STARTED");
            }

            orderRepository.save(latest);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("orderId", latest.getOrderId());
            response.put("processInstanceId",
                    latest.getProcessInstanceId());
            response.put("status", latest.getStatus());

            return response;

        } catch (RestClientException | IllegalStateException exception) {

            // Record a start failure only if a worker has not
            // already updated the order status.
            OrderEntity latest = orderRepository.findById(orderId)
                    .orElse(order);

            if ("PROCESS_STARTING".equals(latest.getStatus())) {
                latest.setStatus("START_FAILED");
                orderRepository.save(latest);
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not start the Camunda workflow: "
                            + exception.getMessage(),
                    exception);
        }
    }

    // GET /api/orders
    @GetMapping
    public List<OrderEntity> getAllOrders() {
        return orderRepository.findAll();
    }

    // GET /api/orders/{orderId}
    @GetMapping("/{orderId}")
    public OrderEntity getOrder(
            @PathVariable("orderId") String orderId) {

        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found: " + orderId));
    }

    // GET /api/orders/{orderId}/status
    @GetMapping("/{orderId}/status")
    public Map<String, Object> getOrderStatus(
            @PathVariable("orderId") String orderId) {

        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found: " + orderId));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("orderId", order.getOrderId());
        response.put("status", order.getStatus());
        response.put("rejectionReason",
                order.getRejectionReason() == null
                        ? "" : order.getRejectionReason());
        response.put("processInstanceId",
                order.getProcessInstanceId() == null
                        ? "" : order.getProcessInstanceId());

        return response;
    }
}
