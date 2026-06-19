package com.fatcatkill.controller;

import com.fatcatkill.dto.GameActionRequest;
import com.fatcatkill.service.DayService;
import com.fatcatkill.service.GameLogService;
import com.fatcatkill.store.GameStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/day")
public class PlayerVoteController {

    private final DayService dayService;
    private final GameStore gameStore;
    private final GameLogService gameLogService;

    public PlayerVoteController(DayService dayService, GameStore gameStore, GameLogService gameLogService) {
        this.dayService = dayService;
        this.gameStore = gameStore;
        this.gameLogService = gameLogService;
    }

    @PostMapping("/vote")
    public ResponseEntity<?> execute(@RequestBody GameActionRequest request) {
        try {
            dayService.playerVote(request.getRoomId(), request.getPlayerId(), request.getTargetId());
            var game = gameStore.getGame(request.getRoomId());
            String actionType = game.getCurrentPhase() == com.fatcatkill.enums.GamePhase.NOMINATION
                    ? "SELECT_NOMINATION"
                    : "SELECT_EXECUTION_VOTE";
            gameLogService.append(game, request.getPlayerId(), actionType, request.getTargetId(), null, null);
            return ResponseEntity.ok(gameStore.getGame(request.getRoomId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/vote/confirm")
    public ResponseEntity<?> confirm(@RequestBody GameActionRequest request) {
        try {
            dayService.confirmVote(request.getRoomId(), request.getPlayerId());
            var game = gameStore.getGame(request.getRoomId());
            gameLogService.append(game, request.getPlayerId(), "CONFIRM_VOTE", null, null,
                    "Vote confirmed. Current phase: " + game.getCurrentPhase() + ".");
            return ResponseEntity.ok(gameStore.getGame(request.getRoomId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/vote/cancel")
    public ResponseEntity<?> cancel(@RequestBody GameActionRequest request) {
        try {
            dayService.cancelVote(request.getRoomId(), request.getPlayerId());
            var game = gameStore.getGame(request.getRoomId());
            gameLogService.append(game, request.getPlayerId(), "CANCEL_VOTE", null, null, null);
            return ResponseEntity.ok(gameStore.getGame(request.getRoomId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/vote/skip")
    public ResponseEntity<?> skip(@RequestBody GameActionRequest request) {
        try {
            dayService.skipVote(request.getRoomId(), request.getPlayerId());
            var game = gameStore.getGame(request.getRoomId());
            gameLogService.append(game, request.getPlayerId(), "SKIP_VOTE", null, null,
                    "Vote skipped. Current phase: " + game.getCurrentPhase() + ".");
            return ResponseEntity.ok(gameStore.getGame(request.getRoomId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
