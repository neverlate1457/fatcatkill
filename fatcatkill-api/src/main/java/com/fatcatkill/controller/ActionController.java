package com.fatcatkill.controller;

import com.fatcatkill.enums.Role;
import com.fatcatkill.model.GameState;
import com.fatcatkill.model.PlayerState;
import com.fatcatkill.service.BotService;
import com.fatcatkill.service.DayService;
import com.fatcatkill.service.GameHelperService;
import com.fatcatkill.service.NightService;
import com.fatcatkill.service.SystemOutService;
import com.fatcatkill.store.GameStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/game")
public class ActionController {

    private final NightService nightService;
    private final DayService dayService;
    private final GameStore gameStore;
    private final GameHelperService gameHelper;
    private final SystemOutService systemOutService;
    private final BotService botService;

    public ActionController(NightService nightService, DayService dayService, GameStore gameStore, BotService botService, SystemOutService systemOutService, GameHelperService gameHelper) {
        this.nightService = nightService;
        this.dayService = dayService;
        this.gameStore = gameStore;
        this.botService = botService;
        this.systemOutService = systemOutService;
        this.gameHelper = gameHelper;
    }

    @PostMapping("/action")
    public ResponseEntity<?> executeAction(@RequestBody Map<String, Object> payload) {
        try {
            String roomId = (String) payload.get("roomId");
            Long playerId = ((Number) payload.get("playerId")).longValue();
            Role targetRole = payload.containsKey("targetRole") && payload.get("targetRole") != null
                    ? Role.valueOf((String) payload.get("targetRole")) : null;
            String actionType = (String) payload.get("actionType");


            Long targetId = payload.containsKey("targetId") && payload.get("targetId") != null
                    ? ((Number) payload.get("targetId")).longValue() : null;
            Long targetId1 = payload.containsKey("targetId1") && payload.get("targetId1") != null
                    ? ((Number) payload.get("targetId1")).longValue() : null;
            Long targetId2 = payload.containsKey("targetId2") && payload.get("targetId2") != null
                    ? ((Number) payload.get("targetId2")).longValue() : null;

            String resultMessage = null;


            GameState currentGame = gameStore.getGame(roomId);
            PlayerState actionPlayer = currentGame == null ? null : gameHelper.getPlayer(currentGame, playerId);
            Role illusionRole = roleForAction(actionType);
            if (gameHelper.isHighRabbitIllusionOf(currentGame, actionPlayer, illusionRole)) {
                resultMessage = "Ability activated.";
            } else {
                switch (actionType) {

                case "FATCAT_KILL":
                    resultMessage = nightService.fatcatKill(roomId, playerId, targetId);
                    break;
                case "FATCAT_TEAM_HINT":
                    resultMessage = nightService.fatcatTeamHint(roomId, playerId);
                    break;
                case "EMPEROR_REVEAL":
                    resultMessage = nightService.emperorRevealAction(roomId, playerId);
                    break;
                case "PH_SERVICE_ACTION":
                    resultMessage = nightService.phServiceAction(roomId, playerId, targetRole);
                    break;
                case "STR_ACTION":
                    nightService.strAction(roomId, playerId, targetId);
                    break;
                case "STR_SKIP":
                    nightService.strSkipAction(roomId, playerId);
                    break;
                case "GUOGUO_ACTION":
                    resultMessage = nightService.guoguoAction(roomId, playerId);
                    break;
                case "FORVKUSA_ACTION":
                    resultMessage = nightService.forvkusaAction(roomId, playerId);
                    break;
                case "HATONG_ACTION":
                    resultMessage = nightService.hatongAction(roomId, playerId);
                    break;
                case "XIAOXIANG_ACTION":
                    resultMessage = nightService.xiaoxiangAction(roomId, playerId);
                    break;
                case "MUBAIMU_ACTION":
                    java.util.List<Long> tartTargets = new java.util.ArrayList<>();
                    if (targetId1 != null) {
                        tartTargets.add(targetId1);
                    }
                    if (targetId2 != null) {
                        tartTargets.add(targetId2);
                    }
                    if (payload.containsKey("targetId3") && payload.get("targetId3") != null) {
                        tartTargets.add(((Number) payload.get("targetId3")).longValue());
                    }
                    resultMessage = nightService.mubaimuAction(roomId, playerId, tartTargets);
                    break;
                case "SHUSHU_ACTION":
                    resultMessage = nightService.shushuAction(roomId, playerId, targetId1, targetId2);
                    targetId = targetId1;
                    break;
                case "GRASS_BEAN_ACTION":
                    resultMessage = nightService.grassBeanAction(roomId, playerId);
                    break;
                case "LIVER_ACTION":
                    nightService.liverHeroAction(roomId, playerId, targetId);
                    break;
                case "CANMAN_ACTION":
                    nightService.canManAction(roomId, playerId, targetId);
                    break;
                case "NANGONG_ACTION":
                    nightService.nangongAction(roomId, playerId, targetId);
                    break;
                case "ANDY_ACTION":
                    resultMessage = nightService.andyAction(roomId, playerId);
                    break;
                case "METHANE_CHECK":
                    if (targetId1 == null || targetId2 == null) {
                        throw new IllegalArgumentException("Methane must choose two targets.");
                    }
                    resultMessage = nightService.methaneAction(roomId, playerId, targetId1, targetId2);
                    break;
                case "XIANGXIANG_ACTION":
                    resultMessage = nightService.xiangxiangAction(roomId, playerId);
                    break;
                case "AC_CAT_ACTION":
                    resultMessage = nightService.acCatAction(roomId, playerId);
                    break;
                case "MOCHI_BOSS_CHECK":
                    resultMessage = nightService.mochiBossCheckAction(roomId, playerId, targetId);
                    break;


                case "SALTED_FISH_STAB":
                    resultMessage = dayService.saltedFishStab(roomId, playerId, targetId);
                    break;
                case "SALTED_FISH_SKIP":
                    resultMessage = dayService.skipSaltedFishStab(roomId, playerId);
                    break;
                case "CHEN_ACTION":
                    resultMessage = dayService.chenAction(roomId, playerId, targetId);
                    break;
                case "CHEN_SKIP":
                    resultMessage = dayService.skipChenAction(roomId, playerId);
                    break;

                default:
                    return ResponseEntity.badRequest().body("Unknown action type: " + actionType);
                }
            }


            try {
                com.fatcatkill.model.GameState game = gameStore.getGame(roomId);
                if (game != null) {
                    PlayerState logPlayer = actionPlayer;
                    String username = logPlayer == null ? null : logPlayer.getUsername();
                    String roleName = logPlayer == null || logPlayer.getRole() == null ? null : logPlayer.getRole().name();
                    String ts = java.time.Instant.now().toString();

                    Long loggedTargetId = targetId != null ? targetId : targetId1;
                    com.fatcatkill.model.GameLogEntry entry = new com.fatcatkill.model.GameLogEntry(ts, playerId, username, roleName, actionType, loggedTargetId, targetId2, resultMessage);
                    game.addLog(entry);
                    gameStore.saveGame(game);
                    systemOutService.action(game, playerId, actionType, loggedTargetId, targetId2, resultMessage);
                }
            } catch (Exception logEx) {

                System.err.println("Failed to write game log: " + logEx.getMessage());
            }


            if (resultMessage != null) {

                return ResponseEntity.ok(Map.of(
                        "gameState", gameStore.getGame(roomId),
                        "message", resultMessage
                ));
            } else {

                return ResponseEntity.ok(gameStore.getGame(roomId));
            }

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
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
                return ResponseEntity.badRequest().body(e.getMessage());
            }
        }
    @GetMapping("/{roomId}/phase")
        public ResponseEntity<?> getGamePhase(@PathVariable String roomId) {
            try {

                GameState game = gameStore.getGame(roomId);
                if (game == null) {
                    return ResponseEntity.badRequest().body("Game not found for room: " + roomId);
                }


                Map<String, Object> phaseInfo = new HashMap<>();
                phaseInfo.put("roomId", game.getRoomId());
                phaseInfo.put("status", game.getStatus());
                phaseInfo.put("currentPhase", game.getCurrentPhase());
                phaseInfo.put("currentRound", game.getCurrentRound());

                return ResponseEntity.ok(phaseInfo);

            } catch (Exception e) {
                return ResponseEntity.badRequest().body(e.getMessage());
            }
        }
}
