package com.example.proxy.service;

import com.example.proxy.proxy.MicroserviceProxy;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Random;

@Service
public class PaymentService implements MicroserviceProxy<Map<String, Object>> {

    private final Map<String, Map<String, Object>> transactions = new ConcurrentHashMap<>();
    private final AtomicLong callCounter = new AtomicLong(0);
    private final AtomicLong transactionIdGenerator = new AtomicLong(5000);
    private final Random random = new Random();

    private static final double FAILURE_RATE = 0.10;

    @Override
    public Map<String, Object> execute(String operation, Object... params) {
        callCounter.incrementAndGet();

        if (shouldFail()) {
            throw new IllegalStateException("Payment processing failed: simulated random failure (10% rate)");
        }

        return switch (operation.toLowerCase()) {
            case "processpayment" -> processPayment(params);
            case "refund" -> refund(params);
            case "gettransaction" -> getTransaction(params);
            case "getalltransactions" -> getAllTransactions();
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };
    }

    private boolean shouldFail() {
        return random.nextDouble() < FAILURE_RATE;
    }

    private Map<String, Object> processPayment(Object... params) {
        String orderId = (String) params[0];
        double amount = ((Number) params[1]).doubleValue();
        String paymentMethod = (String) params[2];

        String transactionId = "TXN-" + transactionIdGenerator.incrementAndGet();
        Map<String, Object> transaction = Map.of(
            "transactionId", transactionId,
            "orderId", orderId,
            "amount", amount,
            "paymentMethod", paymentMethod,
            "status", "COMPLETED",
            "processedAt", System.currentTimeMillis()
        );

        transactions.put(transactionId, transaction);
        return Map.of("transactionId", transactionId, "transaction", transaction, "message", "Payment processed successfully");
    }

    private Map<String, Object> refund(Object... params) {
        String transactionId = (String) params[0];
        double amount = ((Number) params[1]).doubleValue();

        Map<String, Object> originalTransaction = transactions.get(transactionId);
        if (originalTransaction == null) {
            throw new IllegalArgumentException("Transaction not found: " + transactionId);
        }

        String refundId = "REF-" + transactionIdGenerator.incrementAndGet();
        Map<String, Object> refund = Map.of(
            "refundId", refundId,
            "originalTransactionId", transactionId,
            "amount", amount,
            "status", "REFUNDED",
            "refundedAt", System.currentTimeMillis()
        );

        transactions.put(refundId, refund);
        return Map.of("refundId", refundId, "refund", refund, "message", "Refund processed successfully");
    }

    private Map<String, Object> getTransaction(Object... params) {
        String transactionId = (String) params[0];
        Map<String, Object> transaction = transactions.get(transactionId);
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction not found: " + transactionId);
        }
        return Map.of("transaction", transaction);
    }

    private Map<String, Object> getAllTransactions() {
        return Map.of("transactions", new java.util.ArrayList<>(transactions.values()), "totalTransactions", transactions.size());
    }

    public long getCallCount() {
        return callCounter.get();
    }
}