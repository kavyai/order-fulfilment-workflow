
package com.example.orderfulfilment.api;

import com.example.orderfulfilment.service.entity.OrderEntity;
import com.example.orderfulfilment.service.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderControllerTest {

    private OrderRepository repository;
    private OrderController controller;

    @BeforeEach
    void setUp() {
        repository = mock(OrderRepository.class);
        controller = new OrderController(repository);
    }

    @Test
    void getAllOrdersReturnsRepositoryResults() {
        when(repository.findAll()).thenReturn(List.of(new OrderEntity()));
        assertEquals(1, controller.getAllOrders().size());
    }

    @Test
    void getOrderReturnsExistingOrder() {
        OrderEntity order = new OrderEntity();
        when(repository.findById("ORD-1")).thenReturn(Optional.of(order));
        assertSame(order, controller.getOrder("ORD-1"));
    }

    @Test
    void getOrderThrowsNotFoundForMissingOrder() {
        when(repository.findById("ORD-404")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.getOrder("ORD-404"));

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void getOrderStatusReturnsStatusAndEmptyOptionalFields() {
        OrderEntity order = new OrderEntity();
        order.setOrderId("ORD-1");
        order.setStatus("FULFILLED");
        when(repository.findById("ORD-1")).thenReturn(Optional.of(order));

        Map<String, Object> result = controller.getOrderStatus("ORD-1");

        assertEquals("ORD-1", result.get("orderId"));
        assertEquals("FULFILLED", result.get("status"));
        assertEquals("", result.get("rejectionReason"));
        assertEquals("", result.get("processInstanceId"));
    }

    @Test
    void getOrderStatusThrowsNotFoundForMissingOrder() {
        when(repository.findById("ORD-404")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.getOrderStatus("ORD-404"));

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void createOrderRejectsMissingRequiredFields() {
        Map<String, Object> request = Map.of(
                "orderId", "ORD-1",
                "customerId", "CUST-101");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.createOrder(request));

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(repository);
    }

    @Test
    void createOrderRejectsZeroAmount() {
        Map<String, Object> request = Map.of(
                "orderId", "ORD-1",
                "customerId", "CUST-101",
                "amount", 0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.createOrder(request));

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(repository);
    }

    @Test
    void createOrderRejectsInvalidNotificationPreference() {
        Map<String, Object> request = Map.of(
                "orderId", "ORD-1",
                "customerId", "CUST-101",
                "amount", 500,
                "notificationPreference", "FAX");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.createOrder(request));

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(repository);
    }

    @Test
    void createOrderRejectsDuplicateOrderId() {
        when(repository.existsById("ORD-1")).thenReturn(true);

        Map<String, Object> request = Map.of(
                "orderId", "ORD-1",
                "customerId", "CUST-101",
                "amount", 500);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.createOrder(request));

        assertEquals(409, exception.getStatusCode().value());
        verify(repository, never()).save(any(OrderEntity.class));
    }
}
