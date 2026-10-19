
package com.example.orderfulfilment.service.worker;

import com.example.orderfulfilment.service.entity.OrderEntity;
import com.example.orderfulfilment.service.repository.OrderRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderWorkflowWorkersTest {

    @Test
    void autoCancelOrderUpdatesStatusAndReason() {
        OrderRepository repository = mock(OrderRepository.class);
        OrderEntity order = new OrderEntity();

        when(repository.findById("ORD-1"))
                .thenReturn(Optional.of(order));

        OrderWorkflowWorkers workers =
                new OrderWorkflowWorkers(repository);

        workers.autoCancelOrder("ORD-1");

        assertEquals("AUTO_CANCELLED", order.getStatus());
        assertEquals(
                "Credit review timed out after 10 minutes",
                order.getRejectionReason());

        verify(repository).save(order);
    }

    @Test
    void markOrderFulfilledUpdatesStatusAndClearsReason() {
        OrderRepository repository = mock(OrderRepository.class);
        OrderEntity order = new OrderEntity();

        order.setRejectionReason("Previous error");

        when(repository.findById("ORD-2"))
                .thenReturn(Optional.of(order));

        OrderWorkflowWorkers workers =
                new OrderWorkflowWorkers(repository);

        workers.markOrderFulfilled("ORD-2");

        assertEquals("FULFILLED", order.getStatus());
        assertNull(order.getRejectionReason());

        verify(repository).save(order);
    }

    @Test
    void autoCancelDoesNothingWhenOrderDoesNotExist() {
        OrderRepository repository = mock(OrderRepository.class);

        when(repository.findById("ORD-404"))
                .thenReturn(Optional.empty());

        OrderWorkflowWorkers workers =
                new OrderWorkflowWorkers(repository);

        assertDoesNotThrow(() -> workers.autoCancelOrder("ORD-404"));

        verify(repository, never()).save(any(OrderEntity.class));
    }

    @Test
    void markFulfilledDoesNothingWhenOrderDoesNotExist() {
        OrderRepository repository = mock(OrderRepository.class);

        when(repository.findById("ORD-404"))
                .thenReturn(Optional.empty());

        OrderWorkflowWorkers workers =
                new OrderWorkflowWorkers(repository);

        assertDoesNotThrow(() -> workers.markOrderFulfilled("ORD-404"));

        verify(repository, never()).save(any(OrderEntity.class));
    }

    @Test
    void stopWorkersIsSafeBeforeStartup() {
        OrderRepository repository = mock(OrderRepository.class);

        OrderWorkflowWorkers workers =
                new OrderWorkflowWorkers(repository);

        assertDoesNotThrow(workers::stopWorkers);
    }
}
