package com.fatcatkill.controller;

import com.fatcatkill.service.DayService;
import com.fatcatkill.service.GameLogService;
import com.fatcatkill.store.GameStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/day")
public class TallyVotesController {

    private final DayService dayService;
    private final GameStore gameStore;
    private final GameLogService gameLogService;

    public TallyVotesController(DayService dayService, GameStore gameStore, GameLogService gameLogService) {
        this.dayService = dayService;
        this.gameStore = gameStore;
        this.gameLogService = gameLogService;
    }

    @PostMapping("/tally/{roomId}")
    public ResponseEntity<?> execute(@PathVariable String roomId) {
        try {
            dayService.tallyVotesAndNextPhase(roomId);
            var game = gameStore.getGame(roomId);
            gameLogService.append(game, null, "TALLY_VOTES", game.getLastExiledPlayerId(), null,
                    "Votes tallied. Current phase: " + game.getCurrentPhase() + ".");
            return ResponseEntity.ok(gameStore.getGame(roomId));
        } catch (Exception e) {
            return ControllerResponses.badRequest(e);
        }
    }

    @PostMapping("/nomination/{roomId}")
    public ResponseEntity<?> startNomination(@PathVariable String roomId) {
        try {
            dayService.startVotingPhase(roomId);
            var game = gameStore.getGame(roomId);
            gameLogService.append(game, null, "START_NOMINATION", null, null,
                    "Day discussion advanced to nomination.");
            return ResponseEntity.ok(gameStore.getGame(roomId));
        } catch (Exception e) {
            return ControllerResponses.badRequest(e);
        }
    }
}
