package com.fatcatkill.service;

import com.fatcatkill.enums.GamePhase;
import com.fatcatkill.enums.Role;
import com.fatcatkill.enums.RoomStatus;
import com.fatcatkill.model.GameState;
import com.fatcatkill.model.PlayerState;
import com.fatcatkill.model.MessagePayload;
import com.fatcatkill.store.GameStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.util.function.Function;

@Service
public class BotService {

    private final GameStore gameStore;
    private final NightService nightService;
    private final DayService dayService;
    private final GameHelperService gameHelper;
    private final GameLogService gameLogService;
    private final SystemOutService systemOutService;
    private final Random random = new Random();

    public BotService(GameStore gameStore, NightService nightService, DayService dayService, GameHelperService gameHelper, GameLogService gameLogService, SystemOutService systemOutService) {
        this.gameStore = gameStore;
        this.nightService = nightService;
        this.dayService = dayService;
        this.gameHelper = gameHelper;
        this.gameLogService = gameLogService;
        this.systemOutService = systemOutService;
    }

    public int autoPlay(String roomId, Long humanPlayerId) {
        int steps = 0;
        GameState before = gameStore.getGame(roomId);
        if (before == null || before.getStatus() != RoomStatus.PLAYING) return steps;

        if (finishIfOnlyBotsAlive(before)) {
            return 1;
        }

        autoPlaySingleStep(roomId, humanPlayerId);
        steps++;

        GameState latest = gameStore.getGame(roomId);
        if (finishIfOnlyBotsAlive(latest)) {
            return steps + 1;
        }
        return steps;
    }

    public boolean finishIfOnlyBotsAlive(GameState game) {
        if (!onlyBotsAlive(game)) return false;

        gameHelper.finishGame(game, gameHelper.resolveWinnerCamp(game));
        gameLogService.appendPayload(game, null, "BOT_ONLY_FAST_FORWARD", null, null,
                MessagePayload.of("backend.bot.onlyBotsFastForward", "Only bots remain. The game was fast-forwarded to the end."));
        gameStore.saveGame(game);
        return true;
    }
    private boolean onlyBotsAlive(GameState game) {
        return game != null
                && game.getStatus() == RoomStatus.PLAYING
                && game.getPlayers() != null
                && game.getPlayers().stream().anyMatch(PlayerState::isAlive)
                && game.getPlayers().stream().filter(PlayerState::isAlive).allMatch(this::isBotPlayer);
    }


    public void autoPlaySingleStep(String roomId, Long humanPlayerId) {
        GameState game = gameStore.getGame(roomId);
        if (game == null || game.getStatus() != RoomStatus.PLAYING) return;

        GamePhase phase = game.getCurrentPhase();
        List<PlayerState> alivePlayers = game.getPlayers().stream().filter(PlayerState::isAlive).toList();
        if (alivePlayers.isEmpty()) return;

        java.util.function.Supplier<Long> randomTarget = () -> alivePlayers.get(random.nextInt(alivePlayers.size())).getUserId();
        java.util.function.Function<Long, Long> randomOtherTarget = actorId -> {
            List<PlayerState> candidates = alivePlayers.stream()
                    .filter(player -> !player.getUserId().equals(actorId))
                    .toList();
            if (candidates.isEmpty()) return null;
            return candidates.get(random.nextInt(candidates.size())).getUserId();
        };

        switch (phase) {
            case STR_ACTION:
                doActionIfBot(roomId, game, humanPlayerId, Role.STR, bot -> alivePlayers.stream()
                        .filter(player -> !player.getUserId().equals(bot.getUserId()))
                        .findAny()
                        .map(target -> {
                            nightService.strAction(roomId, bot.getUserId(), target.getUserId());
                            return new BotActionLog("STR_ACTION", target.getUserId(), null, null);
                        })
                        .orElseGet(() -> {
                            nightService.strSkipAction(roomId, bot.getUserId());
                            return new BotActionLog("STR_SKIP", null, null, null);
                        }));
                break;
            case NIGHT_START:
                game.getPlayers().stream()
                        .filter(player -> player.isAlive() && isBotPlayer(player) && gameHelper.canUseFatcatKill(game, player))
                        .findFirst()
                        .ifPresent(bot -> {
                    if (bot.getUserId().equals(humanPlayerId)) return;
                    BotActionLog log;
                    if (game.getCurrentRound() == 1) {
                        String message = nightService.fatcatTeamHint(roomId, bot.getUserId());
                        log = new BotActionLog("FATCAT_TEAM_HINT", null, null, message);
                    } else {
                        Long targetId = randomOtherTarget.apply(bot.getUserId());
                        if (targetId == null) return;
                        String message = nightService.fatcatKill(roomId, bot.getUserId(), targetId);
                        log = new BotActionLog("FATCAT_KILL", targetId, null, message);
                    }
                    appendLatest(roomId, bot.getUserId(), log.actionType(), log.targetId(), log.targetId2(), log.message());
                });
                break;
            case PH_SERVICE_ACTION:
                doActionIfBot(roomId, game, humanPlayerId, Role.PH_SERVICE, bot -> {
                    List<Role> candidates = List.of(
                            Role.METHANE, Role.GUOGUO, Role.XIANGXIANG, Role.AC_CAT,
                            Role.FORVKUSA, Role.HATONG, Role.KB, Role.SALTED_FISH,
                            Role.XIAOXIANG, Role.MOCHI_BOSS, Role.GRASS_BEAN, Role.NANGONG,
                            Role.CASTER, Role.ANDY, Role.CAN_MAN, Role.SINGLE_DOG, Role.STR
                    );
                    Role targetRole = candidates.stream()
                            .filter(role -> game.getPlayers().stream().noneMatch(player -> player.getRole() == role))
                            .findFirst()
                            .orElse(candidates.get(0));
                    String message = nightService.phServiceAction(roomId, bot.getUserId(), targetRole);
                    return new BotActionLog("PH_SERVICE_ACTION", null, null, message == null ? "targetRole=" + targetRole : message);
                });
                break;
            case GUOGUO_ACTION:
                doActionIfBot(roomId, game, humanPlayerId, Role.GUOGUO, bot -> new BotActionLog("GUOGUO_ACTION", null, null, nightService.guoguoAction(roomId, bot.getUserId())));
                break;
            case FORVKUSA_ACTION:
                doActionIfBot(roomId, game, humanPlayerId, Role.FORVKUSA, bot -> new BotActionLog("FORVKUSA_ACTION", null, null, nightService.forvkusaAction(roomId, bot.getUserId())));
                break;
            case HATONG_ACTION:
                doActionIfBot(roomId, game, humanPlayerId, Role.HATONG, bot -> new BotActionLog("HATONG_ACTION", null, null, nightService.hatongAction(roomId, bot.getUserId())));
                break;
            case XIAOXIANG_ACTION:
                doActionIfBot(roomId, game, humanPlayerId, Role.XIAOXIANG, bot -> new BotActionLog("XIAOXIANG_ACTION", null, null, nightService.xiaoxiangAction(roomId, bot.getUserId())));
                break;
            case MUBAIMU_ACTION:
                doActionIfBot(roomId, game, humanPlayerId, Role.MUBAIMU, bot -> {
                    List<Long> targets = alivePlayers.stream()
                            .filter(player -> !player.getUserId().equals(bot.getUserId()))
                            .limit(3)
                            .map(PlayerState::getUserId)
                            .toList();
                    String message = nightService.mubaimuAction(roomId, bot.getUserId(), targets);
                    String targetMessage = "targets=" + targets;
                    return new BotActionLog(
                            "MUBAIMU_ACTION",
                            targets.isEmpty() ? null : targets.get(0),
                            targets.size() > 1 ? targets.get(1) : null,
                            message == null ? targetMessage : message + " (" + targetMessage + ")"
                    );
                });
                break;
            case SHUSHU_ACTION:
                doActionIfBot(roomId, game, humanPlayerId, Role.SHUSHU, bot -> {
                    List<Long> targets = alivePlayers.stream()
                            .filter(player -> !player.getUserId().equals(bot.getUserId()))
                            .limit(2)
                            .map(PlayerState::getUserId)
                            .toList();
                    if (targets.size() < 2) return null;

                    String message = nightService.shushuAction(roomId, bot.getUserId(), targets.get(0), targets.get(1));
                    return new BotActionLog("SHUSHU_ACTION", targets.get(0), targets.get(1), message);
                });
                break;
            case GRASS_BEAN_ACTION:
                doActionIfBot(roomId, game, humanPlayerId, Role.GRASS_BEAN, bot -> new BotActionLog("GRASS_BEAN_ACTION", null, null, nightService.grassBeanAction(roomId, bot.getUserId())));
                break;
            case XIANGXIANG_ACTION:
                doActionIfBot(roomId, game, humanPlayerId, Role.XIANGXIANG, bot -> new BotActionLog("XIANGXIANG_ACTION", null, null, nightService.xiangxiangAction(roomId, bot.getUserId())));
                break;
            case AC_CAT_ACTION:
                doActionIfBot(roomId, game, humanPlayerId, Role.AC_CAT, bot -> new BotActionLog("AC_CAT_ACTION", null, null, nightService.acCatAction(roomId, bot.getUserId())));
                break;
            case LIVER_INDEX_ACTION:
                doActionIfBot(roomId, game, humanPlayerId, Role.LIVER_INDEX, bot -> {
                    Long targetId = randomOtherTarget.apply(bot.getUserId());
                    if (targetId == null) return null;
                    nightService.liverHeroAction(roomId, bot.getUserId(), targetId);
                    return new BotActionLog("LIVER_ACTION", targetId, null, null);
                });
                break;
            case CAN_MAN_ACTION:
                doActionIfBot(roomId, game, humanPlayerId, Role.CAN_MAN, bot -> {
                    Long targetId = randomOtherTarget.apply(bot.getUserId());
                    if (targetId == null) return null;
                    nightService.canManAction(roomId, bot.getUserId(), targetId);
                    return new BotActionLog("CANMAN_ACTION", targetId, null, null);
                });
                break;
            case NANGONG_ACTION:
                doActionIfBot(roomId, game, humanPlayerId, Role.NANGONG, bot -> {
                    Long targetId = randomOtherTarget.apply(bot.getUserId());
                    if (targetId == null) return null;
                    nightService.nangongAction(roomId, bot.getUserId(), targetId);
                    return new BotActionLog("NANGONG_ACTION", targetId, null, null);
                });
                break;
            case ANDY_ACTION:
                doActionIfBot(roomId, game, humanPlayerId, Role.ANDY, bot -> new BotActionLog("ANDY_ACTION", null, null, nightService.andyAction(roomId, bot.getUserId())));
                break;
            case METHANE_ACTION:
                doActionIfBot(roomId, game, humanPlayerId, Role.METHANE, bot -> {
                    List<Long> targets = alivePlayers.stream()
                            .filter(player -> !player.getUserId().equals(bot.getUserId()))
                            .limit(2)
                            .map(PlayerState::getUserId)
                            .toList();
                    Long firstTarget = targets.isEmpty() ? bot.getUserId() : targets.get(0);
                    Long secondTarget = targets.size() > 1 ? targets.get(1) : firstTarget;
                    String message = nightService.methaneAction(roomId, bot.getUserId(), firstTarget, secondTarget);
                    return new BotActionLog("METHANE_CHECK", firstTarget, secondTarget, message);
                });
                break;
            case MOCHI_BOSS_ACTION:
                doActionIfBot(roomId, game, humanPlayerId, Role.MOCHI_BOSS, bot -> {
                    Long targetId = randomTarget.get();
                    String message = nightService.mochiBossCheckAction(roomId, bot.getUserId(), targetId);
                    return new BotActionLog("MOCHI_BOSS_CHECK", targetId, null, message);
                });
                break;
            case DAY_START:
                dayService.startVotingPhase(roomId);
                gameLogService.appendPayload(gameStore.getGame(roomId), null, "START_NOMINATION", null, null, MessagePayload.of("backend.bot.startNomination", "Bot auto started nomination."));
                break;
            case NOMINATION:
                autoVoteAllBots(roomId, humanPlayerId, GamePhase.NOMINATION, "NOMINATE", randomTarget);
                break;
            case VOTING:
                autoVoteAllBots(roomId, humanPlayerId, GamePhase.VOTING, "EXECUTION_VOTE", () -> {
                    GameState latestGame = gameStore.getGame(roomId);
                    return latestGame == null ? null : latestGame.getNominatedPlayerId();
                });
                break;
            default:
                break;
        }
    }

    private void autoVoteAllBots(String roomId, Long humanId, GamePhase expectedPhase, String actionType, java.util.function.Supplier<Long> targetSupplier) {
        GameState game = gameStore.getGame(roomId);
        if (game == null) return;

        List<Long> botIds = game.getPlayers().stream()
                .filter(player -> player.isAlive()
                        || game.getFinalVoteEligiblePlayerIds().contains(player.getUserId()))
                .filter(this::isBotPlayer)
                .filter(player -> !player.getUserId().equals(humanId))
                .map(PlayerState::getUserId)
                .toList();

        for (Long botId : botIds) {
            GameState latestGame = gameStore.getGame(roomId);
            if (latestGame == null || latestGame.getStatus() != RoomStatus.PLAYING) return;
            if (latestGame.getCurrentPhase() != expectedPhase) return;

            PlayerState bot = latestGame.getPlayers().stream()
                    .filter(player -> player.getUserId().equals(botId))
                    .findFirst()
                    .orElse(null);
            if (bot == null
                    || (!bot.isAlive() && !latestGame.getFinalVoteEligiblePlayerIds().contains(bot.getUserId()))
                    || bot.getVotedTargetId() != null) continue;

            Long targetId = targetSupplier.get();
            if (targetId == null) continue;

            try {
                dayService.playerVote(roomId, botId, targetId);
                gameLogService.append(gameStore.getGame(roomId), botId, actionType.equals("NOMINATE") ? "SELECT_NOMINATION" : "SELECT_EXECUTION_VOTE", targetId, null, null);
                dayService.confirmVote(roomId, botId);
                gameLogService.append(gameStore.getGame(roomId), botId, "CONFIRM_VOTE", targetId, null, null);
            } catch (IllegalStateException | IllegalArgumentException ignored) {
                // Another action may have changed the phase or vote state while auto voting.
            }
        }
    }

    private void doActionIfBot(String roomId, GameState game, Long humanId, Role targetRole, Function<PlayerState, BotActionLog> action) {
        game.getPlayers().stream()
                .filter(p -> p.isAlive() && isBotPlayer(p) && gameHelper.canActAs(game, p, targetRole))
                .findFirst()
                .ifPresent(bot -> {
                    if (bot.getUserId().equals(humanId)) {
                        systemOutService.action(game, humanId, "BOT_AUTO_SKIP_HUMAN", null, null, "Skip auto bot for human player acting as " + targetRole + ".");
                        return;
                    }

                    BotActionLog log = action.apply(bot);
                    if (log != null) {
                        appendLatest(roomId, bot.getUserId(), log.actionType(), log.targetId(), log.targetId2(), log.message());
                    }
                });
    }

    private void appendLatest(String roomId, Long playerId, String actionType, Long targetId, Long targetId2, String message) {
        gameLogService.append(gameStore.getGame(roomId), playerId, actionType, targetId, targetId2, message);
    }

    private boolean isBotPlayer(PlayerState player) {
        return player != null && player.isBot();
    }

    private record BotActionLog(String actionType, Long targetId, Long targetId2, String message) {}
}
