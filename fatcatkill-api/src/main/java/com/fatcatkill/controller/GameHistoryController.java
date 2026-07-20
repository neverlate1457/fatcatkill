package com.fatcatkill.controller;

import com.fatcatkill.entity.GameRecord;
import com.fatcatkill.model.MessagePayload;
import com.fatcatkill.repository.GameRecordRepository;
import com.fatcatkill.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/history")
public class GameHistoryController {
    private final GameRecordRepository gameRecordRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public GameHistoryController(GameRecordRepository gameRecordRepository, UserRepository userRepository, ObjectMapper objectMapper) {
        this.gameRecordRepository = gameRecordRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<?> listHistory(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Auth-Token", required = false) String authToken
    ) {
        Long accountId = authenticatedAccountId(userId, authToken);
        if (accountId == null) return unauthorized();

        return ResponseEntity.ok(gameRecordRepository.findAllByOrderByEndTimeDesc().stream()
                .filter(record -> includesParticipant(record, accountId))
                .map(this::summary)
                .toList());
    }

    @GetMapping("/{gameId}")
    public ResponseEntity<?> getHistory(
            @PathVariable String gameId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Auth-Token", required = false) String authToken
    ) {
        Long accountId = authenticatedAccountId(userId, authToken);
        if (accountId == null) return unauthorized();

        return gameRecordRepository.findByGameId(gameId)
                .filter(record -> includesParticipant(record, accountId))
                .<ResponseEntity<?>>map(record -> ResponseEntity.ok(Map.of(
                        "summary", summary(record),
                        "finalState", sanitizedFinalState(record.getFinalStateJson())
                )))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Long authenticatedAccountId(String userId, String authToken) {
        if (userId == null || userId.isBlank() || authToken == null || authToken.isBlank()) return null;
        try {
            Long id = Long.valueOf(userId);
            return userRepository.findByIdAndSessionToken(id, authToken)
                    .map(user -> user.getId())
                    .orElse(null);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean includesParticipant(GameRecord record, Long accountId) {
        if (record == null || accountId == null || record.getParticipantAccountIds() == null || record.getParticipantAccountIds().isBlank()) return false;
        String needle = String.valueOf(accountId);
        for (String value : record.getParticipantAccountIds().split(",")) {
            if (needle.equals(value.trim())) return true;
        }
        return false;
    }

    private String sanitizedFinalState(String finalStateJson) {
        if (finalStateJson == null || finalStateJson.isBlank()) return "";
        try {
            Object value = objectMapper.readValue(finalStateJson, Object.class);
            stripSensitiveFields(value);
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private void stripSensitiveFields(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = (Map<String, Object>) rawMap;
            map.remove("accountId");
            map.remove("sessionToken");
            for (Object child : map.values()) {
                stripSensitiveFields(child);
            }
        } else if (value instanceof List<?> list) {
            for (Object child : list) {
                stripSensitiveFields(child);
            }
        }
    }

    private ResponseEntity<?> unauthorized() {
        return ControllerResponses.status(HttpStatus.UNAUTHORIZED, MessagePayload.of("backend.auth.unauthorized", "Please login again."));
    }

    private Map<String, Object> summary(GameRecord record) {
        return Map.of(
                "id", record.getId(),
                "gameId", record.getGameId(),
                "roomId", record.getRoomId(),
                "gameMode", record.getGameMode() == null ? "" : record.getGameMode(),
                "winnerCamp", record.getWinnerCamp() == null ? "" : record.getWinnerCamp().name(),
                "roundsPlayed", record.getRoundsPlayed() == null ? 0 : record.getRoundsPlayed(),
                "playerCount", record.getPlayerCount() == null ? 0 : record.getPlayerCount(),
                "startedAt", record.getStartedAt() == null ? "" : record.getStartedAt().toString(),
                "endTime", record.getEndTime() == null ? "" : record.getEndTime().toString()
        );
    }
}
