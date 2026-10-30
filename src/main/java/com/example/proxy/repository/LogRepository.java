package com.example.proxy.repository;

import com.example.proxy.model.LogEntry;
import com.example.proxy.model.PageResponse;
import com.example.proxy.model.Status;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Repository
public class LogRepository {

    private final List<LogEntry> logs = new CopyOnWriteArrayList<>();

    public void save(LogEntry logEntry) {
        logs.add(logEntry);
    }

    public List<LogEntry> findAll() {
        return new ArrayList<>(logs);
    }

    public PageResponse<LogEntry> findWithFilters(String service, Status status, Instant from, Instant to, int page, int size) {
        List<LogEntry> filtered = logs.stream()
                .filter(log -> service == null || log.getServiceId().equalsIgnoreCase(service))
                .filter(log -> status == null || log.getStatus() == status)
                .filter(log -> from == null || !log.getTimestamp().isBefore(from))
                .filter(log -> to == null || !log.getTimestamp().isAfter(to))
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .collect(Collectors.toList());

        long totalElements = filtered.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int start = page * size;
        int end = Math.min(start + size, filtered.size());

        List<LogEntry> content = start >= filtered.size() ? Collections.emptyList() : filtered.subList(start, end);

        PageResponse<LogEntry> response = new PageResponse<>();
        response.setContent(content);
        response.setPage(page);
        response.setSize(size);
        response.setTotalElements(totalElements);
        response.setTotalPages(totalPages);
        response.setFirst(page == 0);
        response.setLast(page >= totalPages - 1);

        return response;
    }

    public void clear() {
        logs.clear();
    }

    public long count() {
        return logs.size();
    }
}