
package com.example.orderfulfilment.api;

import com.example.orderfulfilment.service.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/orders")
public class CreditReviewController {

    private static final String CREDIT_TASK_KEY = "Task_CreditReview";

    private final OrderRepository orderRepository;

    private final RestClient camundaClient =
            RestClient.create("http://localhost:8080/engine-rest");

    public CreditReviewController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    // GET /api/orders/pending-credit-reviews
    @GetMapping("/pending-credit-reviews")
    public List<?> getPendingCreditReviews() {
        List<?> tasks = camundaClient.get()
                .uri("/task?taskDefinitionKey=" + CREDIT_TASK_KEY + "&active=true")
                .retrieve()
                .body(List.class);

        return tasks == null ? List.of() : tasks;
    }

    // POST /api/orders/credit-reviews/{taskId}
    @PostMapping("/credit-reviews/{taskId}")
    public Map<String, Object> completeCreditReview(
            @PathVariable("taskId") String taskId,
            @RequestBody Map<String, Object> request) {

        Object approvedValue = request.get("approved");

        if (!(approvedValue instanceof Boolean)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "'approved' must be true or false");
        }

        boolean approved = (Boolean) approvedValue;

        String comment = request.get("comment") instanceof String
                ? ((String) request.get("comment")).trim()
                : "";

        if (comment.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A credit review comment is required");
        }

        Map<?, ?> task;

        try {
            task = camundaClient.get()
                    .uri("/task/{id}", taskId)
                    .retrieve()
                    .body(Map.class);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Credit review task not found",
                    exception);
        }

        if (task == null
                || !CREDIT_TASK_KEY.equals(task.get("taskDefinitionKey"))
                || task.get("processInstanceId") == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The supplied task is not a credit review task");
        }

        String processInstanceId =
                task.get("processInstanceId").toString();

        Map<?, ?> processVariables = camundaClient.get()
                .uri("/process-instance/{id}/variables", processInstanceId)
                .retrieve()
                .body(Map.class);

        if (processVariables == null
                || !(processVariables.get("orderId") instanceof Map<?, ?> orderVariable)
                || orderVariable.get("value") == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Order ID was not found in process variables");
        }

        String orderId = orderVariable.get("value").toString();

        if (!orderRepository.existsById(orderId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Order not found: " + orderId);
        }

        try {
            camundaClient.post()
                    .uri("/task/{id}/comment/create", taskId)
                    .body(Map.of("message", comment))
                    .retrieve()
                    .toBodilessEntity();

            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("creditApproved", variable(approved, "Boolean"));
            variables.put("creditReviewComment", variable(comment, "String"));

            if (!approved) {
                variables.put(
                        "rejectionReason",
                        variable("Credit rejected: " + comment, "String"));
            }

            camundaClient.post()
                    .uri("/task/{id}/complete", taskId)
                    .body(Map.of("variables", variables))
                    .retrieve()
                    .toBodilessEntity();

        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Camunda could not complete the credit review: "
                            + exception.getResponseBodyAsString(),
                    exception);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("orderId", orderId);
        response.put("taskId", taskId);
        response.put("processInstanceId", processInstanceId);
        response.put("approved", approved);
        response.put("comment", comment);
        response.put(
                "message",
                approved
                        ? "Credit approved; order continues to fulfilment"
                        : "Credit rejected; order continues to rejection");

        return response;
    }

    // POST /api/orders/{orderId}/priority
    @PostMapping("/{orderId}/priority")
    public Map<String, Object> updatePriority(
            @PathVariable("orderId") String orderId,
            @RequestBody(required = false) Map<String, Object> request) {

        if (!orderRepository.existsById(orderId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Order not found: " + orderId);
        }

        String note = "Customer requested priority update";

        if (request != null && request.get("priorityUpdateNote") != null) {
            note = request.get("priorityUpdateNote").toString();
        } else if (request != null && request.get("note") != null) {
            note = request.get("note").toString();
        }

        Map<String, Object> message = Map.of(
                "messageName", "CustomerUpdatedPriority",
                "businessKey", orderId,
                "processVariables", Map.of(
                        "priorityUpdateNote",
                        variable(note, "String")
                )
        );

        try {
            List<?> correlationResult = camundaClient.post()
                    .uri("/message")
                    .body(message)
                    .retrieve()
                    .body(List.class);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("orderId", orderId);
            response.put("message", "Priority update sent");
            response.put("correlationResult", correlationResult);

            return response;

        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Camunda message correlation failed: "
                            + exception.getResponseBodyAsString(),
                    exception);
        }
    }

    private Map<String, Object> variable(Object value, String type) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("value", value);
        result.put("type", type);
        return result;
    }
}
