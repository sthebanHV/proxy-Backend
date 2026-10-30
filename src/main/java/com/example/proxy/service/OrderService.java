package com.example.proxy.service;

import com.example.proxy.proxy.MicroserviceProxy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OrderService implements MicroserviceProxy<Map<String, Object>> {

    private final Map<String, Map<String, Object>> orders = new ConcurrentHashMap<>();
    private final AtomicLong callCounter = new AtomicLong(0);
    private final AtomicLong orderIdGenerator = new AtomicLong(1000);

    @Override
    public Map<String, Object> execute(String operation, Object... params) {
        callCounter.incrementAndGet();

        return switch (operation.toLowerCase()) {
            case "createorder" -> createOrder(params);
            case "getorder" -> getOrder(params);
            case "cancelorder" -> cancelOrder(params);
            case "getallorders" -> getAllOrders();
            case "updateorderstatus" -> updateOrderStatus(params);
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };
    }

    private Map<String, Object> createOrder(Object... params) {
        String customerId = (String) params[0];
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) params[1];
        
        String orderId = "ORD-" + orderIdGenerator.incrementAndGet();
        Map<String, Object> order = Map.of(
            "orderId", orderId,
            "customerId", customerId,
            "items", items,
            "status", "CREATED",
            "totalAmount", calculateTotal(items),
            "createdAt", System.currentTimeMillis()
        );
        
        orders.put(orderId, order);
        return Map.of("orderId", orderId, "order", order, "message", "Order created successfully");
    }

    private Map<String, Object> getOrder(Object... params) {
        String orderId = (String) params[0];
        Map<String, Object> order = orders.get(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }
        return Map.of("order", order);
    }

    private Map<String, Object> cancelOrder(Object... params) {
        String orderId = (String) params[0];
        Map<String, Object> order = orders.get(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }
        
        order = new ConcurrentHashMap<>(order);
        order.put("status", "CANCELLED");
        orders.put(orderId, order);
        
        return Map.of("orderId", orderId, "order", order, "message", "Order cancelled successfully");
    }

    private Map<String, Object> getAllOrders() {
        return Map.of("orders", new ArrayList<>(orders.values()), "totalOrders", orders.size());
    }

    private Map<String, Object> updateOrderStatus(Object... params) {
        String orderId = (String) params[0];
        String status = (String) params[1];
        
        Map<String, Object> order = orders.get(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }
        
        order = new ConcurrentHashMap<>(order);
        order.put("status", status);
        orders.put(orderId, order);
        
        return Map.of("orderId", orderId, "order", order, "message", "Order status updated");
    }

    private double calculateTotal(List<Map<String, Object>> items) {
        return items.stream()
                .mapToDouble(item -> ((Number) item.getOrDefault("price", 0)).doubleValue() * 
                        ((Number) item.getOrDefault("quantity", 0)).intValue())
                .sum();
    }

    public long getCallCount() {
        return callCounter.get();
    }
}