package com.example.proxy.controller;

import com.example.proxy.proxy.LoggingProxy;
import com.example.proxy.proxy.MicroserviceProxy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final MicroserviceProxy<Map<String, Object>> inventoryProxy;
    private final MicroserviceProxy<Map<String, Object>> orderProxy;
    private final MicroserviceProxy<Map<String, Object>> paymentProxy;

    public ServiceController(
            @org.springframework.beans.factory.annotation.Qualifier("inventoryProxy") MicroserviceProxy<Map<String, Object>> inventoryProxy,
            @org.springframework.beans.factory.annotation.Qualifier("orderProxy") MicroserviceProxy<Map<String, Object>> orderProxy,
            @org.springframework.beans.factory.annotation.Qualifier("paymentProxy") MicroserviceProxy<Map<String, Object>> paymentProxy) {
        this.inventoryProxy = inventoryProxy;
        this.orderProxy = orderProxy;
        this.paymentProxy = paymentProxy;
    }

    @PostMapping("/inventory/{operation}")
    public ResponseEntity<Map<String, Object>> executeInventory(@PathVariable String operation, @RequestBody(required = false) Map<String, Object> body) {
        Object[] params = body != null ? body.values().toArray() : new Object[0];
        Map<String, Object> result = inventoryProxy.execute(operation, params);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/orders/{operation}")
    public ResponseEntity<Map<String, Object>> executeOrder(@PathVariable String operation, @RequestBody(required = false) Map<String, Object> body) {
        Object[] params = body != null ? body.values().toArray() : new Object[0];
        Map<String, Object> result = orderProxy.execute(operation, params);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/payments/{operation}")
    public ResponseEntity<Map<String, Object>> executePayment(@PathVariable String operation, @RequestBody(required = false) Map<String, Object> body) {
        Object[] params = body != null ? body.values().toArray() : new Object[0];
        Map<String, Object> result = paymentProxy.execute(operation, params);
        return ResponseEntity.ok(result);
    }
}