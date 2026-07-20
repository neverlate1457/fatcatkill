package com.fatcatkill.controller;

import com.fatcatkill.model.GameState;
import com.fatcatkill.model.MessagePayload;
import com.fatcatkill.service.BotService;
import com.fatcatkill.service.DayService;
import com.fatcatkill.service.GameLogService;
import com.fatcatkill.store.GameStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/day")
public class TallyVotesController {

    private final DayService dayService;
    private final GameStore gameStore;
    private final GameLogService gameLogService;
    private final BotService botService;

    public TallyVotesController(DayService dayService, GameStore gameStore, GameLogService gameLogService, BotService botService) {
        this.dayService = dayService;
        this.gameStore = gameStore;
        this.gameLogService = gameLogService;
        this.botService = botService;
    }

    @PostMapping("/tally/{roomId}")
    public ResponseEntity<?> execute(@PathVariable String roomId) {
        try {
            GameState game = gameStore.getGame(roomId);
            dayService.tallyVotesAndNextPhase(roomId);
            game = gameOrFallback(roomId, game);
            gameLogService.appendPayload(game, null, "TALLY_VOTES", game == null ? null : game.getLastExiledPlayerId(), null,
                    MessagePayload.of(
                            "backend.day.votesTallied",
                            Map.of("phase", game == null || game.getCurrentPhase() == null ? "UNKNOWN" : game.getCurrentPhase().name()),
                            "Votes tallied. Current phase: " + (game == null ? "UNKNOWN" : game.getCurrentPhase()) + "."
                    ));
            botService.finishIfOnlyBotsAlive(gameOrFallback(roomId, game));
            return ResponseEntity.ok(gameOrFallback(roomId, game));
        } catch (Exception e) {
            return ControllerResponses.badRequest(e);
        }
    }

    @PostMapping("/nomination/{roomId}")
    public ResponseEntity<?> startNomination(@PathVariable String roomId) {
        try {
            GameState game = gameStore.getGame(roomId);
            dayService.startVotingPhase(roomId);
            game = gameOrFallback(roomId, game);
            gameLogService.appendPayload(game, null, "START_NOMINATION", null, null,
                    MessagePayload.of("backend.day.nominationStarted", "Day discussion advanced to nomination."));
            botService.finishIfOnlyBotsAlive(gameOrFallback(roomId, game));
            return ResponseEntity.ok(gameOrFallback(roomId, game));
        } catch (Exception e) {
            return ControllerResponses.badRequest(e);
        }
    }

    private GameState gameOrFallback(String roomId, GameState fallback) {
        GameState latest = gameStore.getGame(roomId);
        return latest == null ? fallback : latest;
    }
}
