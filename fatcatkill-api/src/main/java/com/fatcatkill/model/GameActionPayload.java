package com.fatcatkill.model;

import com.fatcatkill.enums.Role;

import java.util.Map;

public record GameActionPayload(
        String roomId,
        Long playerId,
        String actionType,
        Role targetRole,
        Long targetId,
        Long targetId1,
        Long targetId2,
        Long targetId3
) {
    public static GameActionPayload from(Map<String, Object> payload) {
        if (payload == null) throw invalid("backend.action.missingPayload", "Missing action payload.");
        return new GameActionPayload(
                string(payload.get("roomId")),
                longValue(payload.get("playerId"), "playerId"),
                string(payload.get("actionType")),
                roleValue(payload.get("targetRole")),
                optionalLong(payload.get("targetId")),
                optionalLong(payload.get("targetId1")),
                optionalLong(payload.get("targetId2")),
                optionalLong(payload.get("targetId3"))
        );
    }

    public Long primaryLogTargetId() {
        return targetId != null ? targetId : targetId1;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Role roleValue(Object value) {
        if (value == null) return null;
        try {
            return Role.valueOf(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            throw invalid("backend.action.invalidRole", Map.of("role", String.valueOf(value)), "Invalid role: " + value + ".");
        }
    }

    private static Long optionalLong(Object value) {
        if (value == null) return null;
        if (!(value instanceof Number number)) throw invalid("backend.action.expectedNumericId", "Expected numeric id.");
        return number.longValue();
    }

    private static Long longValue(Object value, String fieldName) {
        if (value == null) throw invalid("backend.action.missingField", Map.of("field", fieldName), "Missing " + fieldName + ".");
        return optionalLong(value);
    }
    private static InvalidGameActionPayloadException invalid(String key, String fallback) {
        return new InvalidGameActionPayloadException(MessagePayload.of(key, fallback));
    }

    private static InvalidGameActionPayloadException invalid(String key, Map<String, Object> params, String fallback) {
        return new InvalidGameActionPayloadException(MessagePayload.of(key, params, fallback));
    }
}
