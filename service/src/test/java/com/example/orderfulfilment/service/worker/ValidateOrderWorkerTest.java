
package com.example.orderfulfilment.service.worker;

import com.example.orderfulfilment.service.entity.OrderEntity;
import com.example.orderfulfilment.service.repository.OrderRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ValidateOrderWorkerTest {

    @Test
    void validOrderReturnsNoError() {
        assertNull(ValidateOrderWorker.validateOrder(
                "ORD-1", "CUST-101", 500));
    }

    @Test
    void missingOrderIdIsRejected() {
        assertEquals("Order ID is required",
                ValidateOrderWorker.validateOrder(null, "CUST-101", 500));
    }

    @Test
    void missingCustomerIdIsRejected() {
        assertEquals("Customer ID is required",
                ValidateOrderWorker.validateOrder("ORD-1", " ", 500));
    }

    @Test
    void invalidAmountIsRejected() {
        assertEquals("Amount must be greater than zero",
                ValidateOrderWorker.validateOrder("ORD-1", "CUST-101", 0));
    }

    @Test
    void unknownCustomerIsRejected() {
        assertEquals("Unknown customer",
                ValidateOrderWorker.validateOrder("ORD-1", "CUST-999", 500));
    }

    @Test
    void workerCanBeCreatedAndStoppedSafely() {
        OrderRepository repository = mock(OrderRepository.class);
        ValidateOrderWorker worker = new ValidateOrderWorker(repository);

        assertNotNull(worker);
        assertDoesNotThrow(worker::stopWorker);
    }
}
