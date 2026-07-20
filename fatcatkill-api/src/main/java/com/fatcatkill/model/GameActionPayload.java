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
                requiredString(payload.get("roomId"), "roomId"),
                longValue(payload.get("playerId"), "playerId"),
                requiredString(payload.get("actionType"), "actionType"),
                roleValue(payload.get("targetRole")),
                optionalLong(payload.get("targetId"), "targetId"),
                optionalLong(payload.get("targetId1"), "targetId1"),
                optionalLong(payload.get("targetId2"), "targetId2"),
                optionalLong(payload.get("targetId3"), "targetId3")
        );
    }

    public Long primaryLogTargetId() {
        return targetId != null ? targetId : targetId1;
    }

    private static String requiredString(Object value, String fieldName) {
        String text = value == null ? null : String.valueOf(value).trim();
        if (text == null || text.isBlank()) throw invalid("backend.action.missingField", Map.of("field", fieldName), "Missing " + fieldName + ".");
        return text;
    }

    private static Role roleValue(Object value) {
        if (value == null) return null;
        try {
            return Role.valueOf(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            throw invalid("backend.action.invalidRole", Map.of("role", String.valueOf(value)), "Invalid role: " + value + ".");
        }
    }

    private static Long optionalLong(Object value, String fieldName) {
        if (value == null) return null;
        if (!(value instanceof Number number)) {
            throw invalid("backend.action.expectedNumericId", Map.of("field", fieldName), "Expected numeric id for " + fieldName + ".");
        }
        return number.longValue();
    }

    private static Long longValue(Object value, String fieldName) {
        if (value == null) throw invalid("backend.action.missingField", Map.of("field", fieldName), "Missing " + fieldName + ".");
        return optionalLong(value, fieldName);
    }

    private static InvalidGameActionPayloadException invalid(String key, String fallback) {
        return new InvalidGameActionPayloadException(MessagePayload.of(key, fallback));
    }

    private static InvalidGameActionPayloadException invalid(String key, Map<String, Object> params, String fallback) {
        return new InvalidGameActionPayloadException(MessagePayload.of(key, params, fallback));
    }
}
