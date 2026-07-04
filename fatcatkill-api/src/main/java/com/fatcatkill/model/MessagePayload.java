package com.fatcatkill.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class MessagePayload {
    private String key;
    private Map<String, Object> params = new LinkedHashMap<>();
    private String fallback;

    public MessagePayload() {}

    public MessagePayload(String key, Map<String, Object> params, String fallback) {
        this.key = key;
        this.params = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
        this.fallback = fallback;
    }

    public static MessagePayload of(String key, String fallback) {
        return new MessagePayload(key, Collections.emptyMap(), fallback);
    }

    public static MessagePayload of(String key, Map<String, Object> params, String fallback) {
        return new MessagePayload(key, params, fallback);
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) {
        this.params = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
    }

    public String getFallback() { return fallback; }
    public void setFallback(String fallback) { this.fallback = fallback; }
}
