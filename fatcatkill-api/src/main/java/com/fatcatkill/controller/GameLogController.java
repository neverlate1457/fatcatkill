package com.fatcatkill.controller;

import com.fatcatkill.enums.RoomStatus;
import com.fatcatkill.model.GameState;
import com.fatcatkill.model.MessagePayload;
import com.fatcatkill.model.PlayerState;
import com.fatcatkill.repository.GameRecordRepository;
import com.fatcatkill.store.GameStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
public class GameLogController {

    private final GameStore gameStore;
    private final GameRecordRepository gameRecordRepository;
    private final ObjectMapper objectMapper;

    public GameLogController(GameStore gameStore, GameRecordRepository gameRecordRepository, ObjectMapper objectMapper) {
        this.gameStore = gameStore;
        this.gameRecordRepository = gameRecordRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<?> getLogs(@PathVariable String roomId) {
        GameState game = gameStore.getGame(roomId);
        if (game == null) game = loadFinishedGameFromHistory(roomId);
        if (game == null) return ResponseEntity.notFound().build();
        if (game.getStatus() != RoomStatus.FINISHED) {
            return ControllerResponses.status(HttpStatus.CONFLICT, MessagePayload.of("backend.game.logsUnavailable", "Game logs are available after the game ends."));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("roomId", roomId);
        result.put("players", game.getPlayers().stream()
                .sorted(Comparator.comparing(PlayerState::getSeatNumber, Comparator.nullsLast(Integer::compareTo)))
                .map(this::playerSummary)
                .toList());
        result.put("logs", game.getLogs());
        return ResponseEntity.ok(result);
    }

    private GameState loadFinishedGameFromHistory(String roomId) {
        if (roomId == null || gameRecordRepository == null) return null;
        return gameRecordRepository.findTopByRoomIdOrderByEndTimeDesc(roomId)
                .map(record -> {
                    try {
                        return objectMapper.readValue(record.getFinalStateJson(), GameState.class);
                    } catch (RuntimeException ex) {
                        return null;
                    }
                })
                .orElse(null);
    }

    private Map<String, Object> playerSummary(PlayerState player) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("seatNumber", player.getSeatNumber());
        summary.put("userId", player.getUserId());
        summary.put("username", player.getUsername() == null ? "" : player.getUsername());
        summary.put("role", player.getRole() == null ? "" : player.getRole().name());
        summary.put("alive", player.isAlive());
        summary.put("bot", player.isBot());
        return summary;
    }
}