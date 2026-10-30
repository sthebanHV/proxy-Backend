package com.example.proxy.proxy;

import com.example.proxy.model.LogEntry;
import com.example.proxy.model.Status;
import com.example.proxy.repository.LogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class LoggingProxy<T> implements MicroserviceProxy<T> {

    private static final Logger log = LoggerFactory.getLogger(LoggingProxy.class);

    private final MicroserviceProxy<T> target;
    private final LogRepository logRepository;
    private final String serviceId;

    public LoggingProxy(MicroserviceProxy<T> target, LogRepository logRepository, String serviceId) {
        this.target = target;
        this.logRepository = logRepository;
        this.serviceId = serviceId;
    }

    @Override
    public T execute(String operation, Object... params) {
        String requestId = UUID.randomUUID().toString();
        long startTime = System.nanoTime();
        Instant timestamp = Instant.now();

        LogEntry logEntry = new LogEntry();
        logEntry.setServiceId(serviceId);
        logEntry.setOperation(operation);
        logEntry.setRequestId(requestId);
        logEntry.setTimestamp(timestamp);

        try {
            log.info("Starting operation: {} on service: {} with requestId: {}", operation, serviceId, requestId);
            T result = target.execute(operation, params);

            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            logEntry.setDurationMs(durationMs);
            logEntry.setStatus(Status.SUCCESS);
            logRepository.save(logEntry);

            log.info("Completed operation: {} on service: {} in {} ms with requestId: {}", operation, serviceId, durationMs, requestId);
            return result;

        } catch (Exception e) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            logEntry.setDurationMs(durationMs);
            logEntry.setStatus(Status.ERROR);
            logEntry.setErrorMessage(getShortStackTrace(e));
            logRepository.save(logEntry);

            log.error("Failed operation: {} on service: {} after {} ms with requestId: {} - Error: {}", 
                    operation, serviceId, durationMs, requestId, e.getMessage(), e);
            throw e;
        }
    }

    private String getShortStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
        if (e.getStackTrace().length > 0) {
            sb.append(" at ").append(e.getStackTrace()[0].toString());
        }
        return sb.toString();
    }
}