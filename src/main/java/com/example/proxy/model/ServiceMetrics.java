package com.example.proxy.model;

import lombok.Data;

import java.util.Map;

@Data
public class ServiceMetrics {
    private String serviceId;
    private long totalCalls;
    private double errorRate;
    private double avgDurationMs;
    private Map<Status, Long> callsByStatus;
}