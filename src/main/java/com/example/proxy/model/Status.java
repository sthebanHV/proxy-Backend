package com.example.proxy.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Status {
    SUCCESS("SUCCESS"),
    ERROR("ERROR");

    private final String value;
}