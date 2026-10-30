package com.example.proxy.model;

import lombok.Data;

import java.time.Instant;

@Data
public class LogEntry {
    private String serviceId;
    private String operation;
    private long durationMs;
    private Status status;
    private String requestId;
    private Instant timestamp;
    private String errorMessage;
}