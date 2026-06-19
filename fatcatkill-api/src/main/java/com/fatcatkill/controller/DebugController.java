package com.fatcatkill.controller;

import com.fatcatkill.enums.GamePhase;
import com.fatcatkill.enums.Role;
import com.fatcatkill.enums.RoomStatus;
import com.fatcatkill.model.GameState;
import com.fatcatkill.model.PlayerState;
import com.fatcatkill.service.GameLogService;
import com.fatcatkill.service.NightService;
import com.fatcatkill.store.GameStore;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@ConditionalOnProperty(name = "fatcatkill.debug-actions", havingValue = "true")
@CrossOrigin(origins = "*")
@RequestMapping("/api/debug")
public class DebugController {

    private final GameStore gameStore;
    private final NightService nightService;
    private final ObjectMapper objectMapper;
    private final GameLogService gameLogService;

    public DebugController(GameStore gameStore, NightService nightService, ObjectMapper objectMapper, GameLogService gameLogService) {
        this.gameStore = gameStore;
        this.nightService = nightService;
        this.objectMapper = objectMapper;
        this.gameLogService = gameLogService;
    }

    @PostMapping("/setRole")
    public ResponseEntity<?> setRole(@RequestBody Map<String, Object> payload) {
        try {
            String roomId = (String) payload.get("roomId");
            Long userId = ((Number) payload.get("userId")).longValue();
            String roleName = (String) payload.get("role");
            String phase = payload.containsKey("phase") && payload.get("phase") != null
                    ? (String) payload.get("phase")
                    : null;

            GameState game = gameStore.getGame(roomId);
            if (game == null) return ResponseEntity.badRequest().body("Room not found: " + roomId);

            PlayerState player = game.getPlayers().stream()
                    .filter(x -> x.getUserId().equals(userId))
                    .findFirst()
                    .orElse(null);
            if (player == null) return ResponseEntity.badRequest().body("Player not found: " + userId);

            player.setRole(Role.valueOf(roleName));

            if (phase != null) {
                game.setCurrentPhase(GamePhase.valueOf(phase));
                game.setStatus(RoomStatus.PLAYING);
            }

            if (payload.containsKey("status") && payload.get("status") != null) {
                game.setStatus(RoomStatus.valueOf((String) payload.get("status")));
            }

            if (payload.containsKey("lastExiledPlayerId") && payload.get("lastExiledPlayerId") != null) {
                Long exiledId = ((Number) payload.get("lastExiledPlayerId")).longValue();
                game.setLastExiledPlayerId(exiledId);
            }

            gameStore.saveGame(game);
            gameLogService.append(game, userId, "SET_ROLE", null, null, "Debug role set to " + roleName);
            return ResponseEntity.ok(game);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/force/acCat")
    public ResponseEntity<?> forceAcCat(@RequestBody Map<String, Object> payload) {
        try {
            String roomId = (String) payload.get("roomId");
            Long playerId = ((Number) payload.get("playerId")).longValue();
            GameState game = gameStore.getGame(roomId);
            if (game == null) return ResponseEntity.badRequest().body("Room not found: " + roomId);

            PlayerState actor = game.getPlayers().stream()
                    .filter(p -> p.getUserId().equals(playerId))
                    .findFirst()
                    .orElse(null);
            if (actor == null) return ResponseEntity.badRequest().body("Player not found: " + playerId);

            Long exiledId = game.getLastExiledPlayerId();
            if (exiledId == null) {
                return ResponseEntity.ok(Map.of(
                        "message", "AC Cat force check: no last exiled player is recorded.",
                        "gameState", game
                ));
            }

            PlayerState exiled = game.getPlayers().stream()
                    .filter(p -> p.getUserId().equals(exiledId))
                    .findFirst()
                    .orElse(null);
            String roleName = exiled == null || exiled.getRole() == null ? "UNKNOWN" : exiled.getRole().name();
            String message = "AC Cat force check: last exiled role is " + roleName + ".";
            return ResponseEntity.ok(Map.of("message", message, "gameState", game));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/force/xiangxiang")
    public ResponseEntity<?> forceXiang(@RequestBody Map<String, Object> payload) {
        try {
            String roomId = (String) payload.get("roomId");
            Long playerId = ((Number) payload.get("playerId")).longValue();
            GameState game = gameStore.getGame(roomId);
            if (game == null) return ResponseEntity.badRequest().body("Room not found: " + roomId);

            PlayerState actor = game.getPlayers().stream()
                    .filter(p -> p.getUserId().equals(playerId))
                    .findFirst()
                    .orElse(null);
            if (actor == null) return ResponseEntity.badRequest().body("Player not found: " + playerId);

            Map<Integer, PlayerState> seatMap = new HashMap<>();
            for (PlayerState player : game.getPlayers()) {
                if (player.isAlive() && player.getSeatNumber() != null) {
                    seatMap.put(player.getSeatNumber(), player);
                }
            }
            if (seatMap.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "message", "Xiangxiang force check: found 0 adjacent fatcat-side players.",
                        "gameState", game
                ));
            }

            List<Integer> seats = new ArrayList<>(seatMap.keySet());
            Collections.sort(seats);

            int fromSeat = actor.getSeatNumber() == null ? -1 : actor.getSeatNumber();
            int idx = -1;
            for (int i = 0; i < seats.size(); i++) {
                if (seats.get(i) == fromSeat) {
                    idx = i;
                    break;
                }
                if (seats.get(i) > fromSeat) {
                    idx = i;
                    break;
                }
            }
            if (idx == -1) idx = 0;

            int start = idx;
            int n = seats.size();
            PlayerState left = null;
            PlayerState right = null;

            int i = start;
            while (true) {
                i = (i - 1 + n) % n;
                if (i == start) break;
                PlayerState player = seatMap.get(seats.get(i));
                if (player != null) {
                    left = player;
                    break;
                }
            }

            i = start;
            while (true) {
                i = (i + 1) % n;
                if (i == start) break;
                PlayerState player = seatMap.get(seats.get(i));
                if (player != null) {
                    right = player;
                    break;
                }
            }

            int count = 0;
            Set<Role> fatRoles = Set.of(
                    Role.FATCAT,
                    Role.LIVER_INDEX,
                    Role.PINK_RABBIT,
                    Role.EMPEROR,
                    Role.NTHU_MATH,
                    Role.MAGIC_MEOW,
                    Role.PH_SERVICE,
                    Role.RAT_MAN
            );
            if (left != null && left.getRole() != null && fatRoles.contains(left.getRole())) count++;
            if (right != null && right.getRole() != null && fatRoles.contains(right.getRole())) count++;

            String message = "Xiangxiang force check: found " + count + " adjacent fatcat-side players.";
            return ResponseEntity.ok(Map.of("message", message, "gameState", game));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/reveal/{roomId}")
    public ResponseEntity<?> revealAll(@PathVariable String roomId) {
        GameState game = gameStore.getGame(roomId);
        if (game == null) return ResponseEntity.badRequest().body("Room not found: " + roomId);

        var players = game.getPlayers().stream()
                .map(player -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("userId", player.getUserId());
                    data.put("username", player.getUsername());
                    data.put("role", player.getRole() == null ? null : player.getRole().name());
                    data.put("alive", player.isAlive());
                    data.put("seatNumber", player.getSeatNumber());
                    return data;
                })
                .toList();

        return ResponseEntity.ok(Map.of("roomId", roomId, "players", players));
    }

    @PostMapping("/reveal/{roomId}")
    public ResponseEntity<?> revealAllPost(@PathVariable String roomId) {
        return revealAll(roomId);
    }

    @GetMapping("/logs/{roomId}")
    public ResponseEntity<?> getLogs(@PathVariable String roomId) {
        GameState game = gameStore.getGame(roomId);
        if (game == null) return ResponseEntity.badRequest().body("Room not found: " + roomId);
        return ResponseEntity.ok(Map.of(
                "roomId", roomId,
                "players", buildPlayerSnapshot(game),
                "logs", game.getLogs()
        ));
    }

    @PostMapping("/export-logs/{roomId}")
    public ResponseEntity<?> exportLogs(@PathVariable String roomId) {
        try {
            GameState game = gameStore.getGame(roomId);
            if (game == null) return ResponseEntity.badRequest().body("Room not found: " + roomId);

            Path logsDir = Paths.get("logs");
            if (!Files.exists(logsDir)) {
                Files.createDirectories(logsDir);
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "room_" + roomId + "_" + timestamp + ".json";
            Path filepath = logsDir.resolve(filename);

            Map<String, Object> logData = new HashMap<>();
            logData.put("roomId", roomId);
            logData.put("exportTime", Instant.now().toString());
            logData.put("players", buildPlayerSnapshot(game));
            logData.put("totalLogs", game.getLogs().size());
            logData.put("logs", game.getLogs());

            String jsonContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(logData);
            Files.write(filepath, jsonContent.getBytes(StandardCharsets.UTF_8));

            Map<String, Object> result = new HashMap<>();
            result.put("message", "Logs exported successfully.");
            result.put("file", filepath.toString());
            result.put("filename", filename);
            result.put("totalLogs", game.getLogs().size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to export logs.");
            error.put("reason", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    private List<Map<String, Object>> buildPlayerSnapshot(GameState game) {
        return game.getPlayers().stream()
                .sorted(java.util.Comparator.comparing(
                        PlayerState::getSeatNumber,
                        java.util.Comparator.nullsLast(Integer::compareTo)
                ))
                .map(player -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("seatNumber", player.getSeatNumber());
                    data.put("userId", player.getUserId());
                    data.put("username", player.getUsername());
                    data.put("role", player.getRole() == null ? null : player.getRole().name());
                    data.put("alive", player.isAlive());
                    return data;
                })
                .toList();
    }
}

