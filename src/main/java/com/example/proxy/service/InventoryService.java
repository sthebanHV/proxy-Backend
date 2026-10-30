package com.example.proxy.service;

import com.example.proxy.proxy.MicroserviceProxy;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class InventoryService implements MicroserviceProxy<Map<String, Object>> {

    private final Map<String, Integer> inventory = new ConcurrentHashMap<>();
    private final AtomicLong callCounter = new AtomicLong(0);

    public InventoryService() {
        inventory.put("PRODUCT_001", 100);
        inventory.put("PRODUCT_002", 50);
        inventory.put("PRODUCT_003", 200);
    }

    @Override
    public Map<String, Object> execute(String operation, Object... params) {
        callCounter.incrementAndGet();
        
        return switch (operation.toLowerCase()) {
            case "checkstock" -> checkStock(params);
            case "additem" -> addItem(params);
            case "removeitem" -> removeItem(params);
            case "getall" -> getAll();
            case "restock" -> restock(params);
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };
    }

    private Map<String, Object> checkStock(Object... params) {
        String productId = (String) params[0];
        int stock = inventory.getOrDefault(productId, 0);
        return Map.of("productId", productId, "stock", stock, "available", stock > 0);
    }

    private Map<String, Object> addItem(Object... params) {
        String productId = (String) params[0];
        int quantity = (Integer) params[1];
        inventory.merge(productId, quantity, Integer::sum);
        return Map.of("productId", productId, "newStock", inventory.get(productId), "message", "Item added successfully");
    }

    private Map<String, Object> removeItem(Object... params) {
        String productId = (String) params[0];
        int quantity = (Integer) params[1];
        int currentStock = inventory.getOrDefault(productId, 0);
        
        if (currentStock < quantity) {
            throw new IllegalStateException("Insufficient stock for product: " + productId);
        }
        
        inventory.merge(productId, -quantity, Integer::sum);
        return Map.of("productId", productId, "newStock", inventory.get(productId), "message", "Item removed successfully");
    }

    private Map<String, Object> getAll() {
        return Map.of("inventory", new ConcurrentHashMap<>(inventory), "totalProducts", inventory.size());
    }

    private Map<String, Object> restock(Object... params) {
        String productId = (String) params[0];
        int quantity = (Integer) params[1];
        inventory.merge(productId, quantity, Integer::sum);
        return Map.of("productId", productId, "newStock", inventory.get(productId), "message", "Restocked successfully");
    }

    public long getCallCount() {
        return callCounter.get();
    }
}