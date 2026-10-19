package com.example.orderfulfilment.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

@Service
public class CamundaProcessClient {

    private final RestClient restClient;

    public CamundaProcessClient(
            @Value("${camunda.engine.base-url}") String baseUrl) {

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public Map<?, ?> startOrderProcess(OrderRequest request) {

        Map<String, Object> variables = new HashMap<>();

        variables.put("orderId", variable(
                request.getOrderId(), "String"));

        variables.put("customerId", variable(
                request.getCustomerId(), "String"));

        variables.put("amount", variable(
                request.getAmount(), "Double"));

        variables.put("preferredNotification", variable(
                request.getPreferredNotification(), "String"));

        Map<String, Object> body = new HashMap<>();
        body.put("businessKey", request.getOrderId());
        body.put("variables", variables);

        Map<?, ?> response = restClient.post()
                .uri("/process-definition/key/orderFulfilmentProcess/start")
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new IllegalStateException(
                    "Camunda returned an empty response");
        }

        return response;
    }

    private Map<String, Object> variable(
            Object value, String type) {

        Map<String, Object> variable = new HashMap<>();
        variable.put("value", value);
        variable.put("type", type);

        return variable;
    }
}