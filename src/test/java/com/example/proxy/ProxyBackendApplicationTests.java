package com.example.proxy;

import com.example.proxy.model.LogEntry;
import com.example.proxy.model.PageResponse;
import com.example.proxy.model.Status;
import com.example.proxy.proxy.LoggingProxy;
import com.example.proxy.proxy.MicroserviceProxy;
import com.example.proxy.repository.LogRepository;
import com.example.proxy.service.InventoryService;
import com.example.proxy.service.OrderService;
import com.example.proxy.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ProxyBackendApplicationTests {

    @Autowired
    private LogRepository logRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    @Qualifier("inventoryProxy")
    private MicroserviceProxy<Map<String, Object>> inventoryProxy;

    @Autowired
    @Qualifier("orderProxy")
    private MicroserviceProxy<Map<String, Object>> orderProxy;

    @Autowired
    @Qualifier("paymentProxy")
    private MicroserviceProxy<Map<String, Object>> paymentProxy;

    @BeforeEach
    void setUp() {
        logRepository.clear();
    }

    @Test
    void contextLoads() {
        assertNotNull(inventoryService);
        assertNotNull(orderService);
        assertNotNull(paymentService);
        assertNotNull(inventoryProxy);
        assertNotNull(orderProxy);
        assertNotNull(paymentProxy);
        assertNotNull(logRepository);
    }

    @Test
    void loggingProxyLogsSuccess() {
        Map<String, Object> result = inventoryProxy.execute("checkstock", "PRODUCT_001");
        assertNotNull(result);
        assertEquals("PRODUCT_001", result.get("productId"));

        PageResponse<LogEntry> logs = logRepository.findWithFilters("inventory", null, null, null, 0, 10);
        assertEquals(1, logs.getTotalElements());
        LogEntry log = logs.getContent().get(0);
        assertEquals("inventory", log.getServiceId());
        assertEquals("checkstock", log.getOperation());
        assertEquals(Status.SUCCESS, log.getStatus());
        assertNotNull(log.getRequestId());
        assertNotNull(log.getTimestamp());
        assertTrue(log.getDurationMs() >= 0);
    }

    @Test
    void loggingProxyLogsError() {
        assertThrows(Exception.class, () -> {
            inventoryProxy.execute("invalidoperation");
        });

        PageResponse<LogEntry> logs = logRepository.findWithFilters("inventory", null, null, null, 0, 10);
        assertEquals(1, logs.getTotalElements());
        LogEntry log = logs.getContent().get(0);
        assertEquals(Status.ERROR, log.getStatus());
        assertNotNull(log.getErrorMessage());
    }

    @Test
    void logRepositoryFiltersByService() {
        inventoryProxy.execute("checkstock", "PRODUCT_001");
        orderProxy.execute("getallorders");
        paymentProxy.execute("getalltransactions");

        PageResponse<LogEntry> inventoryLogs = logRepository.findWithFilters("inventory", null, null, null, 0, 10);
        assertEquals(1, inventoryLogs.getTotalElements());

        PageResponse<LogEntry> orderLogs = logRepository.findWithFilters("orders", null, null, null, 0, 10);
        assertEquals(1, orderLogs.getTotalElements());

        PageResponse<LogEntry> paymentLogs = logRepository.findWithFilters("payments", null, null, null, 0, 10);
        assertEquals(1, paymentLogs.getTotalElements());
    }

    @Test
    void logRepositoryFiltersByStatus() {
        inventoryProxy.execute("checkstock", "PRODUCT_001");
        assertThrows(Exception.class, () -> inventoryProxy.execute("invalidoperation"));

        PageResponse<LogEntry> successLogs = logRepository.findWithFilters(null, Status.SUCCESS, null, null, 0, 10);
        assertEquals(1, successLogs.getTotalElements());

        PageResponse<LogEntry> errorLogs = logRepository.findWithFilters(null, Status.ERROR, null, null, 0, 10);
        assertEquals(1, errorLogs.getTotalElements());
    }

    @Test
    void logRepositoryPagination() {
        for (int i = 0; i < 25; i++) {
            inventoryProxy.execute("checkstock", "PRODUCT_001");
        }

        PageResponse<LogEntry> page1 = logRepository.findWithFilters(null, null, null, null, 0, 10);
        assertEquals(10, page1.getContent().size());
        assertEquals(25, page1.getTotalElements());
        assertEquals(3, page1.getTotalPages());
        assertTrue(page1.isFirst());
        assertFalse(page1.isLast());

        PageResponse<LogEntry> page2 = logRepository.findWithFilters(null, null, null, null, 1, 10);
        assertEquals(10, page2.getContent().size());
        assertFalse(page2.isFirst());
        assertFalse(page2.isLast());

        PageResponse<LogEntry> page3 = logRepository.findWithFilters(null, null, null, null, 2, 10);
        assertEquals(5, page3.getContent().size());
        assertFalse(page3.isFirst());
        assertTrue(page3.isLast());
    }

    @Test
    void paymentServiceFailureRate() {
        int failures = 0;
        int totalCalls = 1000;

        for (int i = 0; i < totalCalls; i++) {
            try {
                paymentProxy.execute("processpayment", "ORD-1001", 100.0, "CREDIT_CARD");
            } catch (Exception e) {
                failures++;
            }
        }

        double failureRate = (double) failures / totalCalls * 100;
        assertTrue(failureRate >= 5.0 && failureRate <= 15.0, 
            "Failure rate should be around 10%, but was " + failureRate + "%");
    }

    @Test
    void metricsCalculation() {
        inventoryProxy.execute("checkstock", "PRODUCT_001");
        inventoryProxy.execute("checkstock", "PRODUCT_002");
        assertThrows(Exception.class, () -> inventoryProxy.execute("invalidoperation"));

        PageResponse<LogEntry> logs = logRepository.findWithFilters("inventory", null, null, null, 0, 10);
        long totalCalls = logs.getTotalElements();
        long errorCount = logs.getContent().stream().filter(l -> l.getStatus() == Status.ERROR).count();
        double avgDuration = logs.getContent().stream().mapToLong(LogEntry::getDurationMs).average().orElse(0.0);

        assertEquals(3, totalCalls);
        assertEquals(1, errorCount);
        assertTrue(avgDuration >= 0);
    }

    @Test
    void threadSafetyOfLogRepository() throws InterruptedException {
        int threadCount = 10;
        int callsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < callsPerThread; j++) {
                        inventoryProxy.execute("checkstock", "PRODUCT_001");
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(0, errors.get());
        assertEquals(threadCount * callsPerThread, logRepository.count());
    }
}