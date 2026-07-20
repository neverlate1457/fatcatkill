package com.fatcatkill.controller;

import com.fatcatkill.model.GameState;
import com.fatcatkill.model.MessagePayload;
import com.fatcatkill.service.BotService;
import com.fatcatkill.store.GameStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/room")
public class GetRoomController {

    private final GameStore gameStore;
    private final BotService botService;

    public GetRoomController(GameStore gameStore, BotService botService) {
        this.gameStore = gameStore;
        this.botService = botService;
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<GameState> getRoom(@PathVariable String roomId) {
        GameState gameState = gameStore.getGame(roomId);
        if (gameState == null) {
            return ResponseEntity.notFound().build();
        }
        botService.finishIfOnlyBotsAlive(gameState);
        return ResponseEntity.ok(gameStore.publicGameState(gameState));
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<?> deleteRoom(
            @PathVariable String roomId,
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        GameState gameState = gameStore.getGame(roomId);
        if (gameState == null) {
            return ResponseEntity.notFound().build();
        }
        Long hostId = gameState.getHostId();
        Long requesterId = parseLong(payload == null ? null : payload.get("playerId"));
        if (hostId != null && !hostId.equals(requesterId)) {
            return ControllerResponses.status(HttpStatus.FORBIDDEN, MessagePayload.of("backend.room.hostOnly", "Only the room host can perform this action."));
        }
        gameStore.removeGame(roomId);
        return ResponseEntity.noContent().build();
    }

    private Long parseLong(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
