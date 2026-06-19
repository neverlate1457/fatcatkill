package com.fatcatkill.controller;

import com.fatcatkill.model.GameState;
import com.fatcatkill.store.GameStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/room")
public class GetRoomController {

    private final GameStore gameStore;

    public GetRoomController(GameStore gameStore) {
        this.gameStore = gameStore;
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<GameState> getRoom(@PathVariable String roomId) {
        GameState gameState = gameStore.getGame(roomId);
        if (gameState == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(gameState);
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> deleteRoom(@PathVariable String roomId) {
        gameStore.removeGame(roomId);
        return ResponseEntity.noContent().build();
    }
}
