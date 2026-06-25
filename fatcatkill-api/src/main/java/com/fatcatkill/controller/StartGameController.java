package com.fatcatkill.controller;

import com.fatcatkill.enums.Role;
import com.fatcatkill.model.GameState;
import com.fatcatkill.service.RoomService;
import com.fatcatkill.service.GameLogService;
import com.fatcatkill.store.GameStore;
import org.springframework.http.ResponseEntity;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/room")
public class StartGameController {

    private final RoomService roomService;
    private final GameStore gameStore;
    private final GameLogService gameLogService;
    private final Environment environment;

    public StartGameController(RoomService roomService, GameStore gameStore, GameLogService gameLogService, Environment environment) {
        this.roomService = roomService;
        this.gameStore = gameStore;
        this.gameLogService = gameLogService;
        this.environment = environment;
    }

    // 接收 URL 參數 ?mode=xxx，如果沒傳就預設為 CLASSIC
    @PostMapping("/start/{roomId}")
    public ResponseEntity<?> execute(
            @PathVariable String roomId, 
            @RequestParam(defaultValue = "CLASSIC") String mode,
            @RequestBody(required = false) Map<String, Object> payload) {
        try {
            applyTestRoleAssignments(roomId, payload);
            boolean hostMode = payload != null && Boolean.TRUE.equals(payload.get("hostMode"));
            List<Role> customDeck = hostMode ? parseRoleList(payload.get("customDeck")) : null;
            List<Role> fatcatHints = hostMode ? parseRoleList(payload.get("fatcatHintRoles")) : null;
            Role highRabbitRole = hostMode ? parseRole(payload.get("highRabbitRole")) : null;
            Long methaneTargetId = hostMode ? parseLong(payload.get("methaneHallucinationTargetId")) : null;
            roomService.startGame(roomId, mode, customDeck, fatcatHints, highRabbitRole, methaneTargetId, hostMode);
            var game = gameStore.getGame(roomId);
            gameLogService.append(game, null, "START_GAME", null, null, "Game started with mode " + mode + ".");
            return ResponseEntity.ok(gameStore.getGame(roomId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private List<Role> parseRoleList(Object rawRoles) {
        if (rawRoles == null) return null;
        if (!(rawRoles instanceof List<?> values)) {
            throw new IllegalArgumentException("Role setting must be a list.");
        }
        List<Role> roles = new ArrayList<>();
        for (Object value : values) {
            Role role = parseRole(value);
            if (role != null) roles.add(role);
        }
        return roles;
    }

    private Role parseRole(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        return Role.valueOf(value.toString());
    }

    private Long parseLong(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        return Long.valueOf(value.toString());
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
            player.setRole(Role.valueOf(rawRole.toString()));
        }
        gameStore.saveGame(game);
    }
}
