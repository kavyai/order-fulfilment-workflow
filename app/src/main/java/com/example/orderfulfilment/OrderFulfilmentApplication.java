package com.example.orderfulfilment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication(scanBasePackages = "com.example.orderfulfilment")
public class OrderFulfilmentApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderFulfilmentApplication.class, args);
    }
}
