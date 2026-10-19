
package com.example.orderfulfilment.service.worker;

import com.example.orderfulfilment.service.entity.OrderEntity;
import com.example.orderfulfilment.service.repository.OrderRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderRejectedWorkerTest {

    @Test
    void rejectOrderUpdatesStatusAndReason() {
        OrderRepository repository = mock(OrderRepository.class);
        OrderEntity order = new OrderEntity();

        when(repository.findById("ORD-1"))
                .thenReturn(Optional.of(order));

        OrderRejectedWorker worker =
                new OrderRejectedWorker(repository);

        worker.rejectOrder("ORD-1", "Unknown customer");

        assertEquals("REJECTED", order.getStatus());
        assertEquals("Unknown customer", order.getRejectionReason());
        verify(repository).save(order);
    }

    @Test
    void nullOrderIdDoesNotAccessRepository() {
        OrderRepository repository = mock(OrderRepository.class);
        OrderRejectedWorker worker =
                new OrderRejectedWorker(repository);

        assertDoesNotThrow(() -> worker.rejectOrder(null, "Unknown customer"));

        verifyNoInteractions(repository);
    }

    @Test
    void missingOrderDoesNotSaveAnything() {
        OrderRepository repository = mock(OrderRepository.class);

        when(repository.findById("ORD-404"))
                .thenReturn(Optional.empty());

        OrderRejectedWorker worker =
                new OrderRejectedWorker(repository);

        worker.rejectOrder("ORD-404", "Unknown customer");

        verify(repository, never()).save(any(OrderEntity.class));
    }

    @Test
    void stopWorkerIsSafeBeforeStartup() {
        OrderRepository repository = mock(OrderRepository.class);
        OrderRejectedWorker worker =
                new OrderRejectedWorker(repository);

        assertDoesNotThrow(worker::stopWorker);
    }
}
