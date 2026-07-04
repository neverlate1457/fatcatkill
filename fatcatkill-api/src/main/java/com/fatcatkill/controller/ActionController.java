package com.fatcatkill.controller;

import com.fatcatkill.enums.Role;
import com.fatcatkill.model.GameState;
import com.fatcatkill.model.GameActionPayload;
import com.fatcatkill.model.PlayerState;
import com.fatcatkill.model.MessagePayload;
import com.fatcatkill.service.BotService;
import com.fatcatkill.service.GameActionDispatcher;
import com.fatcatkill.service.GameHelperService;
import com.fatcatkill.service.SystemOutService;
import com.fatcatkill.service.UnknownGameActionException;
import com.fatcatkill.store.GameStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class ActionController {

    private final GameStore gameStore;
    private final GameHelperService gameHelper;
    private final SystemOutService systemOutService;
    private final BotService botService;
    private final GameActionDispatcher actionDispatcher;

    public ActionController(GameStore gameStore, BotService botService, SystemOutService systemOutService, GameHelperService gameHelper, GameActionDispatcher actionDispatcher) {
        this.gameStore = gameStore;
        this.botService = botService;
        this.systemOutService = systemOutService;
        this.gameHelper = gameHelper;
        this.actionDispatcher = actionDispatcher;
    }

    @PostMapping("/action")
    public ResponseEntity<?> executeAction(@RequestBody Map<String, Object> payload) {
        try {
            GameActionPayload action = GameActionPayload.from(payload);
            GameState currentGame = gameStore.getGame(action.roomId());
            PlayerState actionPlayer = currentGame == null ? null : gameHelper.getPlayer(currentGame, action.playerId());
            Role illusionRole = roleForAction(action.actionType());

            String resultMessage = gameHelper.isHighRabbitIllusionOf(currentGame, actionPlayer, illusionRole)
                    ? "Ability activated."
                    : actionDispatcher.dispatch(action);

            GameState updatedGame = gameStore.getGame(action.roomId());
            Object responseMessage = messagePayloadFor(action.actionType(), updatedGame, resultMessage);
            writeActionLog(action, actionPlayer, resultMessage, responseMessage);

            if (resultMessage != null) {
                return ResponseEntity.ok(Map.of(
                        "gameState", gameStore.getGame(action.roomId()),
                        "message", responseMessage
                ));
            }
            return ResponseEntity.ok(gameStore.getGame(action.roomId()));
        } catch (UnknownGameActionException e) {
            return ResponseEntity.badRequest().body(Map.of("message", MessagePayload.of("backend.action.unknownType", Map.of("actionType", e.getActionType()), e.getMessage())));
        } catch (Exception e) {
            return ControllerResponses.badRequest(e);
        }
    }

    private void writeActionLog(GameActionPayload action, PlayerState actionPlayer, String resultMessage, Object responseMessage) {
        try {
            GameState game = gameStore.getGame(action.roomId());
            if (game == null) return;
            String username = actionPlayer == null ? null : actionPlayer.getUsername();
            String roleName = actionPlayer == null || actionPlayer.getRole() == null ? null : actionPlayer.getRole().name();
            String ts = java.time.Instant.now().toString();
            Long loggedTargetId = action.primaryLogTargetId();
            com.fatcatkill.model.GameLogEntry entry = new com.fatcatkill.model.GameLogEntry(ts, action.playerId(), username, roleName, action.actionType(), loggedTargetId, action.targetId2(), resultMessage);
            if (responseMessage instanceof MessagePayload payloadMessage) {
                entry.setMessageKey(payloadMessage.getKey());
                entry.setMessageParams(payloadMessage.getParams());
                entry.setMessageFallback(payloadMessage.getFallback());
            }
            game.addLog(entry);
            gameStore.saveGame(game);
            systemOutService.action(game, action.playerId(), action.actionType(), loggedTargetId, action.targetId2(), resultMessage);
        } catch (Exception logEx) {
            systemOutService.error("ACTION_LOG_FAILED", "Failed to write game log: " + logEx.getMessage());
        }
    }

    private Object messagePayloadFor(String actionType, GameState game, String fallback) {
        if (fallback == null) return null;
        if (("CHEN_ACTION".equals(actionType) || "SALTED_FISH_STAB".equals(actionType))
                && game != null && game.getPublicMessage() instanceof MessagePayload payload) {
            return payload;
        }
        if ("CHEN_SKIP".equals(actionType)) {
            return MessagePayload.of("backend.chen.skipped", fallback);
        }
        if ("SALTED_FISH_SKIP".equals(actionType)) {
            return MessagePayload.of("backend.saltedFish.skipped", fallback);
        }
        if ("Ability activated.".equals(fallback)) {
            return MessagePayload.of("backend.action.abilityActivated", fallback);
        }
        return MessagePayload.of("backend.raw", Map.of("text", fallback), fallback);
    }
    private Role roleForAction(String actionType) {
        if (actionType == null) return null;
        return switch (actionType) {
            case "FATCAT_KILL", "FATCAT_TEAM_HINT" -> Role.FATCAT;
            case "EMPEROR_REVEAL" -> Role.EMPEROR;
            case "PH_SERVICE_ACTION" -> Role.PH_SERVICE;
            case "STR_ACTION", "STR_SKIP" -> Role.STR;
            case "GUOGUO_ACTION" -> Role.GUOGUO;
            case "FORVKUSA_ACTION" -> Role.FORVKUSA;
            case "HATONG_ACTION" -> Role.HATONG;
            case "XIAOXIANG_ACTION" -> Role.XIAOXIANG;
            case "MUBAIMU_ACTION" -> Role.MUBAIMU;
            case "SHUSHU_ACTION" -> Role.SHUSHU;
            case "GRASS_BEAN_ACTION" -> Role.GRASS_BEAN;
            case "LIVER_ACTION" -> Role.LIVER_INDEX;
            case "CANMAN_ACTION" -> Role.CAN_MAN;
            case "NANGONG_ACTION" -> Role.NANGONG;
            case "ANDY_ACTION" -> Role.ANDY;
            case "METHANE_CHECK" -> Role.METHANE;
            case "XIANGXIANG_ACTION" -> Role.XIANGXIANG;
            case "AC_CAT_ACTION" -> Role.AC_CAT;
            case "MOCHI_BOSS_CHECK" -> Role.MOCHI_BOSS;
            case "SALTED_FISH_STAB", "SALTED_FISH_SKIP" -> Role.SALTED_FISH;
            case "CHEN_ACTION", "CHEN_SKIP" -> Role.CHEN;
            default -> null;
        };
    }

    @PostMapping("/bot/auto")
        public ResponseEntity<?> autoPlayBot(@RequestBody Map<String, Object> payload) {
            try {
                String roomId = (String) payload.get("roomId");
                Long humanId = ((Number) payload.get("playerId")).longValue();

                botService.autoPlaySingleStep(roomId, humanId);
                systemOutService.action(gameStore.getGame(roomId), humanId, "BOT_AUTO", null, null, "Bot auto step requested.");

                return ResponseEntity.ok(gameStore.getGame(roomId));
            } catch (Exception e) {
                return ControllerResponses.badRequest(e);
            }
        }
    @GetMapping("/{roomId}/phase")
        public ResponseEntity<?> getGamePhase(@PathVariable String roomId) {
            try {

                GameState game = gameStore.getGame(roomId);
                if (game == null) {
                    return ResponseEntity.badRequest().body(Map.of("message", MessagePayload.of("backend.game.notFoundForRoom", Map.of("roomId", roomId), "Game not found for room: " + roomId)));
                }


                Map<String, Object> phaseInfo = new HashMap<>();
                phaseInfo.put("roomId", game.getRoomId());
                phaseInfo.put("status", game.getStatus());
                phaseInfo.put("currentPhase", game.getCurrentPhase());
                phaseInfo.put("currentRound", game.getCurrentRound());

                return ResponseEntity.ok(phaseInfo);

            } catch (Exception e) {
                return ControllerResponses.badRequest(e);
            }
        }
}
