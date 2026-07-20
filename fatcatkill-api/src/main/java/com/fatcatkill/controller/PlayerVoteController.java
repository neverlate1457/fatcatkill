package com.fatcatkill.controller;

import com.fatcatkill.dto.GameActionRequest;
import com.fatcatkill.enums.GamePhase;
import com.fatcatkill.model.GameState;
import com.fatcatkill.model.InvalidGameActionPayloadException;
import com.fatcatkill.model.MessagePayload;
import com.fatcatkill.service.DayService;
import com.fatcatkill.service.BotService;
import com.fatcatkill.service.GameLogService;
import com.fatcatkill.store.GameStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/day")
public class PlayerVoteController {

    private final DayService dayService;
    private final GameStore gameStore;
    private final GameLogService gameLogService;
    private final BotService botService;

    public PlayerVoteController(DayService dayService, GameStore gameStore, GameLogService gameLogService, BotService botService) {
        this.dayService = dayService;
        this.gameStore = gameStore;
        this.gameLogService = gameLogService;
        this.botService = botService;
    }

    @PostMapping("/vote")
    public ResponseEntity<?> execute(@RequestBody(required = false) GameActionRequest request) {
        try {
            requireVoteRequest(request, true);
            GameState game = gameStore.getGame(request.getRoomId());
            GamePhase phase = game == null ? null : game.getCurrentPhase();
            dayService.playerVote(request.getRoomId(), request.getPlayerId(), request.getTargetId());
            game = gameOrFallback(request.getRoomId(), game);
            String actionType = phase == GamePhase.NOMINATION
                    ? "SELECT_NOMINATION"
                    : "SELECT_EXECUTION_VOTE";
            gameLogService.append(game, request.getPlayerId(), actionType, request.getTargetId(), null, null);
            botService.finishIfOnlyBotsAlive(gameOrFallback(request.getRoomId(), game));
            return ResponseEntity.ok(gameOrFallback(request.getRoomId(), game));
        } catch (Exception e) {
            return ControllerResponses.badRequest(e);
        }
    }

    @PostMapping("/vote/confirm")
    public ResponseEntity<?> confirm(@RequestBody(required = false) GameActionRequest request) {
        try {
            requireVoteRequest(request, false);
            GameState game = gameStore.getGame(request.getRoomId());
            GamePhase phase = game == null ? null : game.getCurrentPhase();
            dayService.confirmVote(request.getRoomId(), request.getPlayerId());
            game = gameOrFallback(request.getRoomId(), game);
            gameLogService.appendPayload(game, request.getPlayerId(), "CONFIRM_VOTE", null, null,
                    MessagePayload.of(
                            "backend.day.voteConfirmedLog",
                            Map.of("phase", phaseName(phase)),
                            "Vote confirmed. Current phase: " + phaseName(phase) + "."
                    ));
            botService.finishIfOnlyBotsAlive(gameOrFallback(request.getRoomId(), game));
            return ResponseEntity.ok(gameOrFallback(request.getRoomId(), game));
        } catch (Exception e) {
            return ControllerResponses.badRequest(e);
        }
    }

    @PostMapping("/vote/cancel")
    public ResponseEntity<?> cancel(@RequestBody(required = false) GameActionRequest request) {
        try {
            requireVoteRequest(request, false);
            dayService.cancelVote(request.getRoomId(), request.getPlayerId());
            GameState game = gameStore.getGame(request.getRoomId());
            gameLogService.appendPayload(game, request.getPlayerId(), "CANCEL_VOTE", null, null,
                    MessagePayload.of("backend.day.voteCancelledLog", "Vote cancelled."));
            botService.finishIfOnlyBotsAlive(gameOrFallback(request.getRoomId(), game));
            return ResponseEntity.ok(gameOrFallback(request.getRoomId(), game));
        } catch (Exception e) {
            return ControllerResponses.badRequest(e);
        }
    }

    @PostMapping("/vote/skip")
    public ResponseEntity<?> skip(@RequestBody(required = false) GameActionRequest request) {
        try {
            requireVoteRequest(request, false);
            GameState game = gameStore.getGame(request.getRoomId());
            GamePhase phase = game == null ? null : game.getCurrentPhase();
            dayService.skipVote(request.getRoomId(), request.getPlayerId());
            game = gameOrFallback(request.getRoomId(), game);
            gameLogService.appendPayload(game, request.getPlayerId(), "SKIP_VOTE", null, null,
                    MessagePayload.of(
                            "backend.day.voteSkippedLog",
                            Map.of("phase", phaseName(phase)),
                            "Vote skipped. Current phase: " + phaseName(phase) + "."
                    ));
            botService.finishIfOnlyBotsAlive(gameOrFallback(request.getRoomId(), game));
            return ResponseEntity.ok(gameOrFallback(request.getRoomId(), game));
        } catch (Exception e) {
            return ControllerResponses.badRequest(e);
        }
    }

    private GameState gameOrFallback(String roomId, GameState fallback) {
        GameState latest = gameStore.getGame(roomId);
        return latest == null ? fallback : latest;
    }

    private String phaseName(GamePhase phase) {
        return phase == null ? "UNKNOWN" : phase.name();
    }

    private void requireVoteRequest(GameActionRequest request, boolean targetRequired) {
        if (request == null) {
            throw new InvalidGameActionPayloadException(
                    MessagePayload.of("backend.action.missingPayload", "Missing action payload.")
            );
        }
        requireText(request.getRoomId(), "roomId");
        requireLong(request.getPlayerId(), "playerId");
        if (targetRequired) requireLong(request.getTargetId(), "targetId");
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.trim().isBlank()) {
            throw new InvalidGameActionPayloadException(
                    MessagePayload.of("backend.action.missingField", Map.of("field", fieldName), "Missing " + fieldName + ".")
            );
        }
    }

    private void requireLong(Long value, String fieldName) {
        if (value == null) {
            throw new InvalidGameActionPayloadException(
                    MessagePayload.of("backend.action.missingField", Map.of("field", fieldName), "Missing " + fieldName + ".")
            );
        }
    }
}
