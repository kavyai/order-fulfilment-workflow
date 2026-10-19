
package com.example.orderfulfilment.service.repository;

import com.example.orderfulfilment.service.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, String> {
}
