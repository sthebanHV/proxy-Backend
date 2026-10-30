package com.example.proxy.config;

import com.example.proxy.proxy.LoggingProxy;
import com.example.proxy.proxy.MicroserviceProxy;
import com.example.proxy.repository.LogRepository;
import com.example.proxy.service.InventoryService;
import com.example.proxy.service.OrderService;
import com.example.proxy.service.PaymentService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Map;

@Configuration
public class ProxyConfig {

    @Bean
    public LogRepository logRepository() {
        return new LogRepository();
    }

    @Bean
    public InventoryService inventoryService() {
        return new InventoryService();
    }

    @Bean
    public OrderService orderService() {
        return new OrderService();
    }

    @Bean
    public PaymentService paymentService() {
        return new PaymentService();
    }

    @Bean
    @Primary
    @Qualifier("inventoryProxy")
    public MicroserviceProxy<Map<String, Object>> inventoryProxy(InventoryService inventoryService, LogRepository logRepository) {
        return new LoggingProxy<>(inventoryService, logRepository, "inventory");
    }

    @Bean
    @Qualifier("orderProxy")
    public MicroserviceProxy<Map<String, Object>> orderProxy(OrderService orderService, LogRepository logRepository) {
        return new LoggingProxy<>(orderService, logRepository, "orders");
    }

    @Bean
    @Qualifier("paymentProxy")
    public MicroserviceProxy<Map<String, Object>> paymentProxy(PaymentService paymentService, LogRepository logRepository) {
        return new LoggingProxy<>(paymentService, logRepository, "payments");
    }
}