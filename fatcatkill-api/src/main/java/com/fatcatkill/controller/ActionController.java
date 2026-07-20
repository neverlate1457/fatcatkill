package com.fatcatkill.controller;

import com.fatcatkill.enums.GamePhase;
import com.fatcatkill.enums.Role;
import com.fatcatkill.model.GameState;
import com.fatcatkill.model.GameActionPayload;
import com.fatcatkill.model.PlayerState;
import com.fatcatkill.model.MessagePayload;
import com.fatcatkill.service.BotService;
import com.fatcatkill.service.GameActionDispatcher;
import com.fatcatkill.service.GameHelperService;
import com.fatcatkill.service.LocalizedGameException;
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
    public ResponseEntity<?> executeAction(@RequestBody(required = false) Map<String, Object> payload) {
        try {
            requirePayload(payload);
            GameActionPayload action = GameActionPayload.from(payload);
            GameState currentGame = gameStore.getGame(action.roomId());
            PlayerState actionPlayer = currentGame == null ? null : gameHelper.getPlayer(currentGame, action.playerId());
            Role illusionRole = roleForAction(action.actionType());

            boolean highRabbitIllusion = gameHelper.isHighRabbitIllusionOf(currentGame, actionPlayer, illusionRole);
            if (highRabbitIllusion) {
                validateHighRabbitIllusionPhase(currentGame, action.actionType());
            }

            String resultMessage = highRabbitIllusion
                    ? "Ability activated."
                    : actionDispatcher.dispatch(action);

            GameState updatedGame = gameOrFallback(action.roomId(), currentGame);
            Object responseMessage = messagePayloadFor(action.actionType(), updatedGame, resultMessage);
            writeActionLog(action, currentGame, actionPlayer, resultMessage, responseMessage);
            botService.finishIfOnlyBotsAlive(gameOrFallback(action.roomId(), currentGame));

            if (resultMessage != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("gameState", gameOrFallback(action.roomId(), currentGame));
                response.put("message", responseMessage);
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.ok(gameOrFallback(action.roomId(), currentGame));
        } catch (UnknownGameActionException e) {
            return ControllerResponses.badRequest(MessagePayload.of("backend.action.unknownType", Map.of("actionType", e.getActionType()), e.getMessage()));
        } catch (Exception e) {
            return ControllerResponses.badRequest(e);
        }
    }

    private GameState gameOrFallback(String roomId, GameState fallback) {
        GameState latest = gameStore.getGame(roomId);
        return latest == null ? fallback : latest;
    }
    private void writeActionLog(GameActionPayload action, GameState fallbackGame, PlayerState actionPlayer, String resultMessage, Object responseMessage) {
        try {
            GameState game = gameStore.getGame(action.roomId());
            if (game == null) game = fallbackGame;
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

    private void validateHighRabbitIllusionPhase(GameState game, String actionType) {
        if (game == null) {
            throw new LocalizedGameException(MessagePayload.of("backend.game.notActive", "Game is not active."));
        }
        GamePhase phase = game.getCurrentPhase();
        boolean allowed = switch (actionType) {
            case "FATCAT_KILL", "FATCAT_TEAM_HINT" -> phase == GamePhase.NIGHT_START;
            case "EMPEROR_REVEAL" -> game.getCurrentRound() == 1 && isNightPhase(phase);
            case "PH_SERVICE_ACTION" -> phase == GamePhase.PH_SERVICE_ACTION;
            case "STR_ACTION", "STR_SKIP" -> phase == GamePhase.STR_ACTION;
            case "GUOGUO_ACTION" -> phase == GamePhase.GUOGUO_ACTION;
            case "FORVKUSA_ACTION" -> phase == GamePhase.FORVKUSA_ACTION;
            case "HATONG_ACTION" -> phase == GamePhase.HATONG_ACTION;
            case "XIAOXIANG_ACTION" -> phase == GamePhase.XIAOXIANG_ACTION;
            case "MUBAIMU_ACTION" -> phase == GamePhase.MUBAIMU_ACTION;
            case "SHUSHU_ACTION" -> phase == GamePhase.SHUSHU_ACTION;
            case "GRASS_BEAN_ACTION" -> phase == GamePhase.GRASS_BEAN_ACTION;
            case "LIVER_ACTION" -> phase == GamePhase.LIVER_INDEX_ACTION;
            case "CANMAN_ACTION" -> phase == GamePhase.CAN_MAN_ACTION;
            case "NANGONG_ACTION" -> phase == GamePhase.NANGONG_ACTION;
            case "ANDY_ACTION" -> phase == GamePhase.ANDY_ACTION;
            case "METHANE_CHECK" -> phase == GamePhase.METHANE_ACTION;
            case "XIANGXIANG_ACTION" -> phase == GamePhase.XIANGXIANG_ACTION;
            case "AC_CAT_ACTION" -> phase == GamePhase.AC_CAT_ACTION;
            case "MOCHI_BOSS_CHECK" -> phase == GamePhase.MOCHI_BOSS_ACTION;
            case "SALTED_FISH_STAB", "SALTED_FISH_SKIP" -> phase == GamePhase.VOTING;
            case "CHEN_ACTION", "CHEN_SKIP" -> phase == GamePhase.DAY_START;
            default -> false;
        };
        if (!allowed) {
            throw new LocalizedGameException(MessagePayload.of("backend.game.phaseNotAllowed", "Current phase does not allow this action."));
        }
    }

    private boolean isNightPhase(GamePhase phase) {
        return phase == GamePhase.NIGHT_START
                || phase == GamePhase.GUOGUO_ACTION
                || phase == GamePhase.FORVKUSA_ACTION
                || phase == GamePhase.HATONG_ACTION
                || phase == GamePhase.XIAOXIANG_ACTION
                || phase == GamePhase.MUBAIMU_ACTION
                || phase == GamePhase.SHUSHU_ACTION
                || phase == GamePhase.GRASS_BEAN_ACTION
                || phase == GamePhase.AC_CAT_ACTION
                || phase == GamePhase.XIANGXIANG_ACTION
                || phase == GamePhase.LIVER_INDEX_ACTION
                || phase == GamePhase.CAN_MAN_ACTION
                || phase == GamePhase.NANGONG_ACTION
                || phase == GamePhase.ANDY_ACTION
                || phase == GamePhase.METHANE_ACTION
                || phase == GamePhase.MOCHI_BOSS_ACTION;
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
    public ResponseEntity<?> autoPlayBot(@RequestBody(required = false) Map<String, Object> payload) {
        try {
            requirePayload(payload);
            String roomId = requiredString(payload.get("roomId"), "roomId");
            Long humanId = requiredLong(payload.get("playerId"), "playerId");

            GameState currentGame = gameStore.getGame(roomId);
            int steps = botService.autoPlay(roomId, humanId);
            GameState updatedGame = gameOrFallback(roomId, currentGame);
            systemOutService.action(updatedGame, humanId, "BOT_AUTO", null, null, "Bot auto step requested. steps=" + steps);

            return ResponseEntity.ok(updatedGame);
        } catch (Exception e) {
            return ControllerResponses.badRequest(e);
        }
    }


    private void requirePayload(Map<String, Object> payload) {
        if (payload == null) {
            throw new com.fatcatkill.model.InvalidGameActionPayloadException(
                    MessagePayload.of("backend.action.missingPayload", "Missing action payload.")
            );
        }
    }

    private String requiredString(Object value, String fieldName) {
        String text = value == null ? null : value.toString().trim();
        if (text == null || text.isBlank()) {
            throw new com.fatcatkill.model.InvalidGameActionPayloadException(
                    MessagePayload.of("backend.action.missingField", Map.of("field", fieldName), "Missing " + fieldName + ".")
            );
        }
        return text;
    }

    private Long requiredLong(Object value, String fieldName) {
        if (value == null) {
            throw new com.fatcatkill.model.InvalidGameActionPayloadException(
                    MessagePayload.of("backend.action.missingField", Map.of("field", fieldName), "Missing " + fieldName + ".")
            );
        }
        if (value instanceof Number number) return number.longValue();
        throw new com.fatcatkill.model.InvalidGameActionPayloadException(
                MessagePayload.of("backend.action.expectedNumericId", Map.of("field", fieldName), "Expected numeric id for " + fieldName + ".")
        );
    }

    @GetMapping("/{roomId}/phase")
    public ResponseEntity<?> getGamePhase(@PathVariable String roomId) {
        try {
            GameState game = gameStore.getGame(roomId);
            if (game == null) {
                return ControllerResponses.badRequest(MessagePayload.of("backend.game.notFoundForRoom", Map.of("roomId", roomId), "Game not found for room: " + roomId));
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
