package com.fatcatkill.controller;

import com.fatcatkill.enums.Role;
import com.fatcatkill.enums.RoomStatus;
import com.fatcatkill.model.GameState;
import com.fatcatkill.model.MessagePayload;
import com.fatcatkill.model.PlayerState;
import com.fatcatkill.service.LocalizedGameException;
import com.fatcatkill.repository.UserRepository;
import com.fatcatkill.service.RoomService;
import com.fatcatkill.service.GameLogService;
import com.fatcatkill.store.GameStore;
import org.springframework.http.ResponseEntity;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping("/api/room")
public class StartGameController {

    private final RoomService roomService;
    private final GameStore gameStore;
    private final GameLogService gameLogService;
    private final Environment environment;
    private final UserRepository userRepository;

    public StartGameController(RoomService roomService, GameStore gameStore, GameLogService gameLogService, Environment environment, UserRepository userRepository) {
        this.roomService = roomService;
        this.gameStore = gameStore;
        this.gameLogService = gameLogService;
        this.environment = environment;
        this.userRepository = userRepository;
    }

    // 接收 URL 參數 ?mode=xxx，如果沒傳就預設為 OLD_HOME
    @PostMapping("/start/{roomId}")
    public ResponseEntity<?> execute(
            @PathVariable String roomId, 
            @RequestParam(defaultValue = "OLD_HOME") String mode,
            @RequestBody(required = false) Map<String, Object> payload) {
        try {
            ensureWaitingRoom(roomId, payload);
            applyTestRoleAssignments(roomId, payload);
            boolean hostMode = payload != null && Boolean.TRUE.equals(payload.get("hostMode"));
            List<Role> customDeck = hostMode ? parseRoleList(payload.get("customDeck")) : null;
            List<Role> fatcatHints = hostMode ? parseRoleList(payload.get("fatcatHintRoles")) : null;
            Role highRabbitRole = hostMode ? parseRole(payload.get("highRabbitRole")) : null;
            Long methaneTargetId = hostMode ? parseLong(payload.get("methaneHallucinationTargetId")) : null;
            roomService.startGame(roomId, mode, customDeck, fatcatHints, highRabbitRole, methaneTargetId, hostMode);
            var game = gameStore.getGame(roomId);
            gameLogService.appendPayload(game, null, "START_GAME", null, null, MessagePayload.of("backend.room.gameStarted", Map.of("mode", mode), "Game started with mode " + mode + "."));
            return ResponseEntity.ok(gameStore.getGame(roomId));
        } catch (Exception e) {
            return ControllerResponses.badRequest(e);
        }
    }


    private void ensureWaitingRoom(String roomId, Map<String, Object> payload) {
        if (gameStore.getGame(roomId) != null) return;
        if (payload == null || !(payload.get("players") instanceof List<?> rawPlayers) || rawPlayers.isEmpty()) {
            return;
        }

        GameState game = new GameState();
        game.setRoomId(roomId);
        game.setStatus(RoomStatus.WAITING);
        game.setHostMode(Boolean.TRUE.equals(payload.get("hostMode")));
        Long hostId = parseLong(payload.get("playerId"));
        if (hostId != null) game.setHostId(hostId);

        List<PlayerState> players = new ArrayList<>();
        Set<Long> usedIds = new HashSet<>();
        for (Object rawPlayer : rawPlayers) {
            if (!(rawPlayer instanceof Map<?, ?> playerData)) continue;
            Long playerId = parseLong(playerData.get("userId"));
            if (playerId == null || usedIds.contains(playerId)) continue;

            String nickname = playerData.get("nickname") == null
                    ? "Player " + playerId
                    : playerData.get("nickname").toString().trim();
            if (nickname.isBlank()) nickname = "Player " + playerId;

            PlayerState player = new PlayerState();
            player.setUserId(playerId);
            player.setUsername(nickname);
            player.setAccountId(validAccountId(playerData));
            players.add(player);
            usedIds.add(playerId);
        }

        if (!players.isEmpty()) {
            game.setPlayers(players);
            gameStore.saveGame(game);
        }
    }
    private Long validAccountId(Map<?, ?> playerData) {
        Long accountId = parseOptionalLong(playerData.get("accountId"));
        Object rawToken = playerData.get("sessionToken");
        if (accountId == null || rawToken == null || rawToken.toString().isBlank()) return null;
        return userRepository.findByIdAndSessionToken(accountId, rawToken.toString()).isPresent() ? accountId : null;
    }

    private List<Role> parseRoleList(Object rawRoles) {
        if (rawRoles == null) return null;
        if (!(rawRoles instanceof List<?> values)) {
            throw new LocalizedGameException(MessagePayload.of("backend.room.roleSettingMustBeList", "Role setting must be a list."));
        }
        List<Role> roles = new ArrayList<>();
        for (Object value : values) {
            Role role = parseRole(value, "role");
            if (role != null) roles.add(role);
        }
        return roles;
    }

    private Role parseRole(Object value) {
        return parseRole(value, "role");
    }

    private Role parseRole(Object value, String fieldName) {
        if (value == null || value.toString().isBlank()) return null;
        try {
            return Role.valueOf(value.toString());
        } catch (IllegalArgumentException ex) {
            throw new LocalizedGameException(MessagePayload.of(
                    "backend.action.invalidRole",
                    Map.of("role", String.valueOf(value), "field", fieldName),
                    "Invalid role: " + value + "."
            ));
        }
    }

    private Long parseLong(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            throw new LocalizedGameException(MessagePayload.of(
                    "backend.action.expectedNumericId",
                    Map.of("field", "id"),
                    "Expected numeric id."
            ));
        }
    }


    private Long parseOptionalLong(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void applyTestRoleAssignments(String roomId, Map<String, Object> payload) {
        if (payload == null || !environment.getProperty("fatcatkill.debug-actions", Boolean.class, false)) return;
        Object rawAssignments = payload.get("roleAssignments");
        if (!(rawAssignments instanceof Map<?, ?> assignments) || assignments.isEmpty()) return;

        GameState game = gameStore.getGame(roomId);
        if (game == null || game.getPlayers() == null) return;

        for (var player : game.getPlayers()) {
            Object rawRole = assignments.get(String.valueOf(player.getUserId()));
            if (rawRole == null) {
                rawRole = assignments.get(player.getUserId());
            }
            if (rawRole == null || rawRole.toString().isBlank()) {
                player.setRole(null);
                continue;
            }
            player.setRole(parseRole(rawRole, "roleAssignments"));
        }
        gameStore.saveGame(game);
    }
}
