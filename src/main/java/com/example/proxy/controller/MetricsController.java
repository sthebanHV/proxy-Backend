package com.example.proxy.controller;

import com.example.proxy.model.LogEntry;
import com.example.proxy.model.PageResponse;
import com.example.proxy.model.ServiceMetrics;
import com.example.proxy.model.Status;
import com.example.proxy.repository.LogRepository;
import com.example.proxy.service.InventoryService;
import com.example.proxy.service.OrderService;
import com.example.proxy.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final LogRepository logRepository;
    private final InventoryService inventoryService;
    private final OrderService orderService;
    private final PaymentService paymentService;

    public MetricsController(LogRepository logRepository,
                             InventoryService inventoryService,
                             OrderService orderService,
                             PaymentService paymentService) {
        this.logRepository = logRepository;
        this.inventoryService = inventoryService;
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    @GetMapping("/summary")
    public ResponseEntity<List<ServiceMetrics>> getSummary() {
        List<LogEntry> allLogs = logRepository.findAll();
        List<ServiceMetrics> metrics = new ArrayList<>();

        for (String serviceId : List.of("inventory", "orders", "payments")) {
            List<LogEntry> serviceLogs = allLogs.stream()
                    .filter(log -> log.getServiceId().equals(serviceId))
                    .toList();

            long totalCalls = serviceLogs.size();
            long errorCount = serviceLogs.stream()
                    .filter(log -> log.getStatus() == Status.ERROR)
                    .count();
            double errorRate = totalCalls > 0 ? (double) errorCount / totalCalls * 100 : 0.0;
            double avgDurationMs = totalCalls > 0 ? serviceLogs.stream()
                    .mapToLong(LogEntry::getDurationMs)
                    .average()
                    .orElse(0.0) : 0.0;

            Map<Status, Long> callsByStatus = serviceLogs.stream()
                    .collect(Collectors.groupingBy(LogEntry::getStatus, Collectors.counting()));

            ServiceMetrics metric = new ServiceMetrics();
            metric.setServiceId(serviceId);
            metric.setTotalCalls(totalCalls);
            metric.setErrorRate(Math.round(errorRate * 100.0) / 100.0);
            metric.setAvgDurationMs(Math.round(avgDurationMs * 100.0) / 100.0);
            metric.setCallsByStatus(callsByStatus);

            metrics.add(metric);
        }

        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/logs")
    public ResponseEntity<PageResponse<LogEntry>> getLogs(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Status statusEnum = status != null ? Status.valueOf(status.toUpperCase()) : null;
        Instant fromInstant = from != null ? Instant.parse(from) : null;
        Instant toInstant = to != null ? Instant.parse(to) : null;

        PageResponse<LogEntry> response = logRepository.findWithFilters(service, statusEnum, fromInstant, toInstant, page, size);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/simulate-load")
    public ResponseEntity<Map<String, Object>> simulateLoad() {
        Random random = new Random();
        String[] services = {"inventory", "orders", "payments"};
        String[] inventoryOps = {"checkstock", "additem", "removeitem", "getall", "restock"};
        String[] orderOps = {"createorder", "getorder", "cancelorder", "getallorders", "updateorderstatus"};
        String[] paymentOps = {"processpayment", "refund", "gettransaction", "getalltransactions"};

        int successCount = 0;
        int errorCount = 0;

        for (int i = 0; i < 50; i++) {
            try {
                String service = services[random.nextInt(services.length)];
                String operation;
                Object[] params;

                switch (service) {
                    case "inventory":
                        operation = inventoryOps[random.nextInt(inventoryOps.length)];
                        params = generateInventoryParams(operation, random);
                        inventoryService.execute(operation, params);
                        break;
                    case "orders":
                        operation = orderOps[random.nextInt(orderOps.length)];
                        params = generateOrderParams(operation, random);
                        orderService.execute(operation, params);
                        break;
                    case "payments":
                        operation = paymentOps[random.nextInt(paymentOps.length)];
                        params = generatePaymentParams(operation, random);
                        paymentService.execute(operation, params);
                        break;
                    default:
                        continue;
                }
                successCount++;
            } catch (Exception e) {
                errorCount++;
            }
        }

        return ResponseEntity.ok(Map.of(
            "message", "Load simulation completed",
            "totalCalls", 50,
            "successful", successCount,
            "failed", errorCount,
            "errorRate", String.format("%.2f%%", (errorCount / 50.0) * 100)
        ));
    }

    private Object[] generateInventoryParams(String operation, Random random) {
        String[] products = {"PRODUCT_001", "PRODUCT_002", "PRODUCT_003", "PRODUCT_004"};
        return switch (operation) {
            case "checkstock", "getall" -> new Object[]{};
            case "additem", "restock" -> new Object[]{products[random.nextInt(products.length)], random.nextInt(50) + 1};
            case "removeitem" -> new Object[]{products[random.nextInt(products.length)], random.nextInt(10) + 1};
            default -> new Object[]{};
        };
    }

    private Object[] generateOrderParams(String operation, Random random) {
        return switch (operation) {
            case "createorder" -> new Object[]{"CUST-" + (random.nextInt(100) + 1), List.of(Map.of("productId", "PRODUCT_001", "quantity", random.nextInt(5) + 1, "price", 29.99))};
            case "getorder", "cancelorder" -> new Object[]{"ORD-" + (1000 + random.nextInt(100))};
            case "getallorders" -> new Object[]{};
            case "updateorderstatus" -> new Object[]{"ORD-" + (1000 + random.nextInt(100)), List.of("CREATED", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED").get(random.nextInt(5))};
            default -> new Object[]{};
        };
    }

    private Object[] generatePaymentParams(String operation, Random random) {
        return switch (operation) {
            case "processpayment" -> new Object[]{"ORD-" + (1000 + random.nextInt(100)), 10.0 + random.nextDouble() * 500, List.of("CREDIT_CARD", "DEBIT_CARD", "PAYPAL", "BANK_TRANSFER").get(random.nextInt(4))};
            case "refund" -> new Object[]{"TXN-" + (5000 + random.nextInt(100)), 10.0 + random.nextDouble() * 100};
            case "gettransaction" -> new Object[]{"TXN-" + (5000 + random.nextInt(100))};
            case "getalltransactions" -> new Object[]{};
            default -> new Object[]{};
        };
    }
}