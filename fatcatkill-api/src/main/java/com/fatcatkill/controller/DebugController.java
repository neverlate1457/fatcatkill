package com.fatcatkill.controller;

import com.fatcatkill.enums.GamePhase;
import com.fatcatkill.enums.Role;
import com.fatcatkill.enums.RoomStatus;
import com.fatcatkill.model.GameState;
import com.fatcatkill.model.PlayerState;
import com.fatcatkill.model.MessagePayload;
import com.fatcatkill.model.InvalidGameActionPayloadException;
import com.fatcatkill.service.GameLogService;
import com.fatcatkill.store.GameStore;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@RequestMapping("/api/debug")
public class DebugController {

    private final GameStore gameStore;
    private final ObjectMapper objectMapper;
    private final GameLogService gameLogService;

    public DebugController(GameStore gameStore, ObjectMapper objectMapper, GameLogService gameLogService) {
        this.gameStore = gameStore;
        this.objectMapper = objectMapper;
        this.gameLogService = gameLogService;
    }

    @PostMapping("/setRole")
    public ResponseEntity<?> setRole(@RequestBody(required = false) Map<String, Object> payload) {
        try {
            requirePayload(payload);
            String roomId = requiredString(payload.get("roomId"), "roomId");
            Long userId = requiredLong(payload.get("userId"), "userId");
            String roleName = requiredString(payload.get("role"), "role");
            String phase = optionalString(payload.get("phase"));

            GameState game = gameStore.getGame(roomId);
            if (game == null) return roomNotFound(roomId);

            PlayerState player = game.getPlayers().stream()
                    .filter(x -> x.getUserId().equals(userId))
                    .findFirst()
                    .orElse(null);
            if (player == null) return playerNotFound(userId);

            player.setRole(Role.valueOf(roleName));

            if (phase != null) {
                game.setCurrentPhase(GamePhase.valueOf(phase));
                game.setStatus(RoomStatus.PLAYING);
            }

            if (payload.containsKey("status") && payload.get("status") != null) {
                game.setStatus(RoomStatus.valueOf(requiredString(payload.get("status"), "status")));
            }

            if (payload.containsKey("lastExiledPlayerId") && payload.get("lastExiledPlayerId") != null) {
                Long exiledId = requiredLong(payload.get("lastExiledPlayerId"), "lastExiledPlayerId");
                game.setLastExiledPlayerId(exiledId);
            }

            gameStore.saveGame(game);
            gameLogService.append(game, userId, "SET_ROLE", null, null, "Debug role set to " + roleName);
            return ResponseEntity.ok(game);
        } catch (Exception e) {
            return ControllerResponses.badRequest(e);
        }
    }

    @PostMapping("/force/acCat")
    public ResponseEntity<?> forceAcCat(@RequestBody(required = false) Map<String, Object> payload) {
        try {
            requirePayload(payload);
            String roomId = requiredString(payload.get("roomId"), "roomId");
            Long playerId = requiredLong(payload.get("playerId"), "playerId");
            GameState game = gameStore.getGame(roomId);
            if (game == null) return roomNotFound(roomId);

            PlayerState actor = game.getPlayers().stream()
                    .filter(p -> p.getUserId().equals(playerId))
                    .findFirst()
                    .orElse(null);
            if (actor == null) return playerNotFound(playerId);

            Long exiledId = game.getLastExiledPlayerId();
            if (exiledId == null) {
                return ResponseEntity.ok(Map.of(
                        "message", MessagePayload.of("backend.debug.acCatNoLastExile", "AC Cat force check: no last exiled player is recorded."),
                        "gameState", game
                ));
            }

            PlayerState exiled = game.getPlayers().stream()
                    .filter(p -> p.getUserId().equals(exiledId))
                    .findFirst()
                    .orElse(null);
            String roleName = exiled == null || exiled.getRole() == null ? "UNKNOWN" : exiled.getRole().name();
            MessagePayload message = MessagePayload.of("backend.debug.acCatLastExiledRole", Map.of("role", roleName), "AC Cat force check: last exiled role is " + roleName + ".");
            return ResponseEntity.ok(Map.of("message", message, "gameState", game));
        } catch (Exception e) {
            return ControllerResponses.badRequest(e);
        }
    }

    @PostMapping("/force/xiangxiang")
    public ResponseEntity<?> forceXiang(@RequestBody(required = false) Map<String, Object> payload) {
        try {
            requirePayload(payload);
            String roomId = requiredString(payload.get("roomId"), "roomId");
            Long playerId = requiredLong(payload.get("playerId"), "playerId");
            GameState game = gameStore.getGame(roomId);
            if (game == null) return roomNotFound(roomId);

            PlayerState actor = game.getPlayers().stream()
                    .filter(p -> p.getUserId().equals(playerId))
                    .findFirst()
                    .orElse(null);
            if (actor == null) return playerNotFound(playerId);

            Map<Integer, PlayerState> seatMap = new HashMap<>();
            for (PlayerState player : game.getPlayers()) {
                if (player.isAlive() && player.getSeatNumber() != null) {
                    seatMap.put(player.getSeatNumber(), player);
                }
            }
            if (seatMap.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "message", MessagePayload.of("backend.debug.xiangxiangAdjacentCount", Map.of("count", 0), "Xiangxiang force check: found 0 adjacent fatcat-side players."),
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

            MessagePayload message = MessagePayload.of("backend.debug.xiangxiangAdjacentCount", Map.of("count", count), "Xiangxiang force check: found " + count + " adjacent fatcat-side players.");
            return ResponseEntity.ok(Map.of("message", message, "gameState", game));
        } catch (Exception e) {
            return ControllerResponses.badRequest(e);
        }
    }

    @GetMapping("/reveal/{roomId}")
    public ResponseEntity<?> revealAll(@PathVariable String roomId) {
        GameState game = gameStore.getGame(roomId);
        if (game == null) return roomNotFound(roomId);

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
        if (game == null) return roomNotFound(roomId);
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
            if (game == null) return roomNotFound(roomId);

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
            result.put("message", MessagePayload.of("backend.debug.logsExported", "Logs exported successfully."));
            result.put("file", filepath.toString());
            result.put("filename", filename);
            result.put("totalLogs", game.getLogs().size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ControllerResponses.badRequest(MessagePayload.of(
                    "backend.debug.exportLogsFailed",
                    Map.of("reason", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()),
                    "Failed to export logs."
            ));
        }
    }

    private void requirePayload(Map<String, Object> payload) {
        if (payload == null) {
            throw new InvalidGameActionPayloadException(
                    MessagePayload.of("backend.action.missingPayload", "Missing action payload.")
            );
        }
    }

    private String requiredString(Object value, String fieldName) {
        String text = optionalString(value);
        if (text == null || text.isBlank()) {
            throw new InvalidGameActionPayloadException(
                    MessagePayload.of("backend.action.missingField", Map.of("field", fieldName), "Missing " + fieldName + ".")
            );
        }
        return text;
    }

    private String optionalString(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Long requiredLong(Object value, String fieldName) {
        if (value == null) {
            throw new InvalidGameActionPayloadException(
                    MessagePayload.of("backend.action.missingField", Map.of("field", fieldName), "Missing " + fieldName + ".")
            );
        }
        if (value instanceof Number number) return number.longValue();
        throw new InvalidGameActionPayloadException(
                MessagePayload.of("backend.action.expectedNumericId", Map.of("field", fieldName), "Expected numeric id for " + fieldName + ".")
        );
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

    private ResponseEntity<?> roomNotFound(String roomId) {
        return ControllerResponses.badRequest(MessagePayload.of(
                "backend.game.notFoundForRoom",
                Map.of("roomId", roomId),
                "Game not found for room: " + roomId
        ));
    }

    private ResponseEntity<?> playerNotFound(Long playerId) {
        return ControllerResponses.badRequest(MessagePayload.of(
                "backend.debug.playerNotFound",
                Map.of("playerId", playerId),
                "Player not found: " + playerId
        ));
    }
}
