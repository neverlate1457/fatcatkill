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

import java.util.Map;

@RestController
@RequestMapping("/api/history")
public class GameHistoryController {
    private final GameRecordRepository gameRecordRepository;
    private final UserRepository userRepository;

    public GameHistoryController(GameRecordRepository gameRecordRepository, UserRepository userRepository) {
        this.gameRecordRepository = gameRecordRepository;
        this.userRepository = userRepository;
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
                        "finalState", record.getFinalStateJson()
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

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "message", MessagePayload.of("backend.auth.unauthorized", "Please login again.")
        ));
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