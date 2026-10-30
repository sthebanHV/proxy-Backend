package com.example.proxy.proxy;

public interface MicroserviceProxy<T> {
    T execute(String operation, Object... params);
}