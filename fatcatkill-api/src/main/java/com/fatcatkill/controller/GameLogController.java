package com.fatcatkill.controller;

import com.fatcatkill.enums.RoomStatus;
import com.fatcatkill.model.GameState;
import com.fatcatkill.model.PlayerState;
import com.fatcatkill.store.GameStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
public class GameLogController {

    private final GameStore gameStore;

    public GameLogController(GameStore gameStore) {
        this.gameStore = gameStore;
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<?> getLogs(@PathVariable String roomId) {
        GameState game = gameStore.getGame(roomId);
        if (game == null) return ResponseEntity.notFound().build();
        if (game.getStatus() != RoomStatus.FINISHED) {
            return ResponseEntity.status(409).body("Game logs are available after the game ends.");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("roomId", roomId);
        result.put("players", game.getPlayers().stream()
                .sorted(Comparator.comparing(PlayerState::getSeatNumber, Comparator.nullsLast(Integer::compareTo)))
                .map(player -> Map.of(
                        "seatNumber", player.getSeatNumber(),
                        "userId", player.getUserId(),
                        "username", player.getUsername(),
                        "role", player.getRole().name(),
                        "alive", player.isAlive()
                ))
                .toList());
        result.put("logs", game.getLogs());
        return ResponseEntity.ok(result);
    }
}
