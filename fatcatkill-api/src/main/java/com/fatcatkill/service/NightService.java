package com.fatcatkill.service;

import com.fatcatkill.enums.GamePhase;
import com.fatcatkill.enums.Role;
import com.fatcatkill.enums.RoomStatus;
import com.fatcatkill.model.GameState;
import com.fatcatkill.model.PlayerState;
import com.fatcatkill.store.GameStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

@Service
public class NightService {

    private final GameStore gameStore;
    private final GameHelperService gameHelper;
    private final Random random = new Random();

    public NightService(GameStore gameStore, GameHelperService gameHelper) {
        this.gameStore = gameStore;
        this.gameHelper = gameHelper;
    }

    private static final GamePhase[] NIGHT_SEQUENCE = {
            GamePhase.STR_ACTION,
            GamePhase.PH_SERVICE_ACTION,
            GamePhase.NIGHT_START,
            GamePhase.LIVER_INDEX_ACTION,
            GamePhase.GUOGUO_ACTION,
            GamePhase.FORVKUSA_ACTION,
            GamePhase.HATONG_ACTION,
            GamePhase.XIAOXIANG_ACTION,
            GamePhase.MUBAIMU_ACTION,
            GamePhase.SHUSHU_ACTION,
            GamePhase.GRASS_BEAN_ACTION,
            GamePhase.AC_CAT_ACTION,
            GamePhase.XIANGXIANG_ACTION,
            GamePhase.CAN_MAN_ACTION,
            GamePhase.NANGONG_ACTION,
            GamePhase.ANDY_ACTION,
            GamePhase.METHANE_ACTION,
            GamePhase.DAY_START
    };

    public void moveToNextPhase(GameState gameState) {
        GamePhase current = gameState.getCurrentPhase();
        int currentIndex = -1;

        for (int i = 0; i < NIGHT_SEQUENCE.length; i++) {
            if (NIGHT_SEQUENCE[i] == current) {
                currentIndex = i;
                break;
            }
        }

        for (int i = currentIndex + 1; i < NIGHT_SEQUENCE.length; i++) {
            GamePhase nextPhase = NIGHT_SEQUENCE[i];
            if (nextPhase == GamePhase.DAY_START) {
                settleNightActions(gameState);
                if (gameState.getCurrentPhase() == GamePhase.GAME_OVER) {
                    return;
                }
                if (gameState.getMochiBossPendingCheckPlayerId() != null) {
                    gameState.setCurrentPhase(GamePhase.MOCHI_BOSS_ACTION);
                } else {
                    gameState.setCurrentPhase(GamePhase.DAY_START);
                }
                return;
            }

            if (isPhaseNeeded(gameState, nextPhase)) {
                gameState.setCurrentPhase(nextPhase);
                return;
            }
        }
    }

    private boolean isPhaseNeeded(GameState gameState, GamePhase phase) {
        Role requiredRole;
        switch (phase) {
            case STR_ACTION:
                requiredRole = Role.STR;
                break;
            case PH_SERVICE_ACTION:
                if (gameState.getCurrentRound() != 1) return false;
                requiredRole = Role.PH_SERVICE;
                break;
            case GUOGUO_ACTION:
                if (gameState.getCurrentRound() != 1) return false;
                requiredRole = Role.GUOGUO;
                break;
            case FORVKUSA_ACTION:
                if (gameState.getCurrentRound() != 1) return false;
                requiredRole = Role.FORVKUSA;
                break;
            case HATONG_ACTION:
                if (gameState.getLastDayVoterIds() == null || gameState.getLastDayVoterIds().isEmpty()) return false;
                requiredRole = Role.HATONG;
                return gameState.getPlayers().stream()
                        .anyMatch(player -> player.isAlive()
                                && gameHelper.canActAs(gameState, player, Role.HATONG)
                                && gameState.getLastDayVoterIds().contains(player.getUserId()));
            case XIAOXIANG_ACTION:
                if (gameState.getCurrentRound() != 1) return false;
                requiredRole = Role.XIAOXIANG;
                break;
            case MUBAIMU_ACTION:
                if (gameState.getCurrentRound() != 1) return false;
                requiredRole = Role.MUBAIMU;
                break;
            case SHUSHU_ACTION:
                requiredRole = Role.SHUSHU;
                break;
            case GRASS_BEAN_ACTION:
                if (gameState.getCurrentRound() != 1) return false;
                requiredRole = Role.GRASS_BEAN;
                break;
            case AC_CAT_ACTION:
                requiredRole = Role.AC_CAT;
                break;
            case XIANGXIANG_ACTION:
                requiredRole = Role.XIANGXIANG;
                break;
            case LIVER_INDEX_ACTION:
                requiredRole = Role.LIVER_INDEX;
                break;
            case CAN_MAN_ACTION:
                requiredRole = Role.CAN_MAN;
                break;
            case NANGONG_ACTION:
                requiredRole = Role.NANGONG;
                return gameState.getPlayers().stream()
                        .anyMatch(player -> player.isAlive()
                                && gameHelper.canActAs(gameState, player, Role.NANGONG)
                                && !gameState.getNangongUsedPlayerIds().contains(player.getUserId()));
            case ANDY_ACTION:
                if (gameState.getCurrentRound() != 1) return false;
                requiredRole = Role.ANDY;
                break;
            case METHANE_ACTION:
                requiredRole = Role.METHANE;
                break;
            default:
                return true;
        }

        return gameHelper.hasActorForRole(gameState, requiredRole);
    }

    public void strAction(String roomId, Long playerId, Long targetId) {
        GameState gameState = gameHelper.validateAndGetGame(roomId, GamePhase.STR_ACTION);
        PlayerState actor = gameHelper.validateAndGetEffectivePlayer(gameState, playerId, Role.STR);
        targetId = redirectToXiaoenIfNeeded(gameState, playerId, targetId);
        PlayerState target = gameHelper.getAlivePlayer(gameState, targetId);

        if (actor.getUserId().equals(target.getUserId())) {
            throw new IllegalArgumentException("Str cannot swap with self.");
        }
        Integer actorSeat = gameHelper.getEffectiveSeatNumber(gameState, actor);
        Integer targetSeat = gameHelper.getEffectiveSeatNumber(gameState, target);
        if (actorSeat == null || targetSeat == null) {
            throw new IllegalStateException("Both players must have seat numbers.");
        }
        if (gameState.getStrSwappedSeatNumbers().contains(actorSeat)
                || gameState.getStrSwappedSeatNumbers().contains(targetSeat)) {
            throw new IllegalStateException("A swapped seat number cannot be swapped again tonight.");
        }

        gameState.getStrTemporarySeatNumbers().put(actor.getUserId(), targetSeat);
        gameState.getStrTemporarySeatNumbers().put(target.getUserId(), actorSeat);
        gameState.getStrSwappedSeatNumbers().add(actorSeat);
        gameState.getStrSwappedSeatNumbers().add(targetSeat);

        moveToNextPhase(gameState);
        gameStore.saveGame(gameState);
    }

    public void strSkipAction(String roomId, Long playerId) {
        GameState gameState = gameHelper.validateAndGetGame(roomId, GamePhase.STR_ACTION);
        gameHelper.validateAndGetEffectivePlayer(gameState, playerId, Role.STR);

        moveToNextPhase(gameState);
        gameStore.saveGame(gameState);
    }

    public String fatcatKill(String roomId, Long playerId, Long targetId) {
        GameState gameState = gameHelper.validateAndGetGame(roomId, GamePhase.NIGHT_START);
        PlayerState actor = gameHelper.validateAlive(gameState, playerId);
        if (!gameHelper.canUseFatcatKill(gameState, actor)) {
            throw new IllegalArgumentException("This player cannot use Fatcat kill right now.");
        }
        if (gameState.getCurrentRound() == 1) {
            throw new IllegalStateException("Fatcat cannot kill on the first night.");
        }
        if (gameState.getFatcatKillBlockedPlayerIds() != null
                && gameState.getFatcatKillBlockedPlayerIds().remove(playerId)) {
            gameHelper.transferFatcatKillToMagicMeow(gameState);
            moveToNextPhase(gameState);
            gameStore.saveGame(gameState);
            return "Fatcat kill was blocked by Pink Rabbit's protection.";
        }
        targetId = redirectToXiaoenIfNeeded(gameState, playerId, targetId);
        gameHelper.getAlivePlayer(gameState, targetId);

        gameState.getNightActions().put("FATCAT_KILL", targetId);
        gameState.getNightActions().put("FATCAT_KILL_ACTOR", playerId);
        moveToNextPhase(gameState);
        gameStore.saveGame(gameState);
        return null;
    }

    public String fatcatTeamHint(String roomId, Long playerId) {
        GameState gameState = gameStore.getGame(roomId);
        if (gameState == null || gameState.getStatus() != RoomStatus.PLAYING) {
            throw new IllegalStateException("Game is not active.");
        }
        if (gameState.getCurrentRound() != 1 || !isNightPhase(gameState.getCurrentPhase())) {
            throw new IllegalStateException("Team hint is only available during the first night.");
        }

        PlayerState actor = gameHelper.validateAlive(gameState, playerId);
        if (actor.getRole() != Role.FATCAT && !gameHelper.isFatcatHorcrux(actor.getRole())) {
            throw new IllegalArgumentException("Only Fatcat and Fatcat horcruxes can use this hint.");
        }

        String hint = actor.getRole() == Role.FATCAT
                ? buildFatcatTeamHint(gameState, actor) + " " + buildFatcatAbsentVolunteerHint(gameState)
                : buildFatcatTeamHint(gameState, actor);
        if (actor.getRole() == Role.FATCAT) {
            if (gameState.getCurrentPhase() == GamePhase.NIGHT_START) {
                moveToNextPhase(gameState);
            }
            gameStore.saveGame(gameState);
        }
        return hint;
    }

    public String phServiceAction(String roomId, Long playerId, Role targetRole) {
        GameState gameState = gameHelper.validateAndGetGame(roomId, GamePhase.PH_SERVICE_ACTION);
        gameHelper.validateAndGetPlayer(gameState, playerId, Role.PH_SERVICE);

        if (targetRole == null || !gameHelper.isVolunteerArmy(targetRole)) {
            throw new IllegalArgumentException("PH Service must choose a volunteer-army role.");
        }
        boolean rolePresent = gameState.getPlayers().stream()
                .anyMatch(player -> player.getRole() == targetRole);

        if (!rolePresent) {
            gameState.setPhServiceStolenRole(targetRole);
        }

        moveToNextPhase(gameState);
        gameStore.saveGame(gameState);

        return rolePresent
                ? "PH Service hack failed: chosen role is present."
                : "PH Service hack succeeded: stolen role = " + translateRole(targetRole) + ".";
    }

    public String emperorRevealAction(String roomId, Long playerId) {
        GameState gameState = gameStore.getGame(roomId);
        if (gameState == null || gameState.getStatus() != RoomStatus.PLAYING) {
            throw new IllegalStateException("Game is not active.");
        }
        if (gameState.getCurrentRound() != 1 || !isNightPhase(gameState.getCurrentPhase())) {
            throw new IllegalStateException("Emperor reveal is only available during the first night.");
        }

        gameHelper.validateAndGetPlayer(gameState, playerId, Role.EMPEROR);
        gameState.getPlayers().forEach(player -> gameHelper.rememberRatManChecker(gameState, playerId, player));
        return gameHelper.isAbilityDisabled(gameState, playerId)
                ? buildScrambledAllRolesHint(gameState)
                : buildAllRolesHint(gameState);
    }

    public void liverHeroAction(String roomId, Long playerId, Long targetId) {
        GameState gameState = gameHelper.validateAndGetGame(roomId, GamePhase.LIVER_INDEX_ACTION);
        gameHelper.validateAndGetPlayer(gameState, playerId, Role.LIVER_INDEX);
        if (gameHelper.isDrunk(gameState, playerId)) {
            moveToNextPhase(gameState);
            gameStore.saveGame(gameState);
            return;
        }
        targetId = redirectToXiaoenIfNeeded(gameState, playerId, targetId);
        gameHelper.getAlivePlayer(gameState, targetId);

        gameState.getNightActions().put("LIVER_DEBUFF", targetId);
        moveToNextPhase(gameState);
        gameStore.saveGame(gameState);
    }

    public void canManAction(String roomId, Long playerId, Long targetId) {
        GameState gameState = gameHelper.validateAndGetGame(roomId, GamePhase.CAN_MAN_ACTION);
        gameHelper.validateAndGetEffectivePlayer(gameState, playerId, Role.CAN_MAN);
        if (gameHelper.isAbilityDisabled(gameState, playerId)) {
            moveToNextPhase(gameState);
            gameStore.saveGame(gameState);
            return;
        }
        if (playerId.equals(targetId)) {
            throw new IllegalArgumentException("Can Man cannot drink with himself.");
        }
        targetId = redirectToXiaoenIfNeeded(gameState, playerId, targetId);
        if (playerId.equals(targetId)) {
            throw new IllegalArgumentException("Can Man cannot drink with himself.");
        }
        PlayerState target = gameHelper.getAlivePlayer(gameState, targetId);

        gameState.getNightActions().put("CAN_MAN_ID", playerId);
        if (!gameHelper.markBarkKingNoShowIfNeeded(gameState, target)) {
            gameState.getNightActions().put("CAN_MAN_DRINK", targetId);
        }
        moveToNextPhase(gameState);
        gameStore.saveGame(gameState);
    }

    public void nangongAction(String roomId, Long playerId, Long targetId) {
        GameState gameState = gameHelper.validateAndGetGame(roomId, GamePhase.NANGONG_ACTION);
        gameHelper.validateAndGetEffectivePlayer(gameState, playerId, Role.NANGONG);
        if (gameState.getNangongUsedPlayerIds().contains(playerId)) {
            throw new IllegalStateException("Nangong has already used this action.");
        }
        gameState.getNangongUsedPlayerIds().add(playerId);
        if (gameHelper.isAbilityDisabled(gameState, playerId)) {
            moveToNextPhase(gameState);
            gameStore.saveGame(gameState);
            return;
        }
        targetId = redirectToXiaoenIfNeeded(gameState, playerId, targetId);
        PlayerState target = gameHelper.getAlivePlayer(gameState, targetId);

        if (gameHelper.markBarkKingNoShowIfNeeded(gameState, target)) {
            moveToNextPhase(gameState);
            gameStore.saveGame(gameState);
            return;
        }

        gameState.getNightActions().put("NANGONG_ID", playerId);
        gameState.getNightActions().put("NANGONG_TARGET", targetId);
        moveToNextPhase(gameState);
        gameStore.saveGame(gameState);
    }

    public String methaneAction(String roomId, Long playerId, Long target1, Long target2) {
        GameState gameState = gameHelper.validateAndGetGame(roomId, GamePhase.METHANE_ACTION);
        gameHelper.validateAndGetEffectivePlayer(gameState, playerId, Role.METHANE);

        target1 = redirectToXiaoenIfNeeded(gameState, playerId, target1);
        target2 = redirectToXiaoenIfNeeded(gameState, playerId, target2);
        PlayerState first = gameHelper.getAlivePlayer(gameState, target1);
        PlayerState second = gameHelper.getAlivePlayer(gameState, target2);

        gameHelper.rememberRatManChecker(gameState, playerId, first);
        gameHelper.rememberRatManChecker(gameState, playerId, second);
        boolean hasFatcat = gameHelper.isFatcatForMethane(gameState, playerId, first)
                || gameHelper.isFatcatForMethane(gameState, playerId, second);
        boolean debuffed = gameHelper.isAbilityDisabled(gameState, playerId);

        moveToNextPhase(gameState);
        gameStore.saveGame(gameState);
        if (debuffed) {
            return "Methane check: no selected player appears to be Fatcat side.";
        }
        return hasFatcat ? "Methane check: at least one selected player appears to be Fatcat side."
                : "Methane check: no selected player appears to be Fatcat side.";
    }

    public String guoguoAction(String roomId, Long playerId) {
        GameState gameState = gameHelper.validateAndGetGame(roomId, GamePhase.GUOGUO_ACTION);
        gameHelper.validateAndGetEffectivePlayer(gameState, playerId, Role.GUOGUO);

        if (gameHelper.isAbilityDisabled(gameState, playerId)) {
            moveToNextPhase(gameState);
            gameStore.saveGame(gameState);
            return "Guoguo hint: no valid hint was found.";
        }

        if (gameState.getGuoguoHint() == null) {
            gameState.setGuoguoHint(buildGuoguoHint(gameState, playerId));
        }

        String hint = gameState.getGuoguoHint();
        moveToNextPhase(gameState);
        gameStore.saveGame(gameState);
        return hint;
    }

    public String forvkusaAction(String roomId, Long playerId) {
        GameState gameState = gameHelper.validateAndGetGame(roomId, GamePhase.FORVKUSA_ACTION);
        gameHelper.validateAndGetEffectivePlayer(gameState, playerId, Role.FORVKUSA);

        int adjacentPairs = gameHelper.isAbilityDisabled(gameState, playerId) ? 0 : gameHelper.countAdjacentFatcatSeatPairs(gameState);
        moveToNextPhase(gameState);
        gameStore.saveGame(gameState);
        return "Forvkusa check: Fatcat-side adjacent seat pairs = " + adjacentPairs + ".";
    }

    public String hatongAction(String roomId, Long playerId) {
        GameState gameState = gameHelper.validateAndGetGame(roomId, GamePhase.HATONG_ACTION);
        gameHelper.validateAndGetEffectivePlayer(gameState, playerId, Role.HATONG);
        if (!gameState.getLastDayVoterIds().contains(playerId)) {
            throw new IllegalStateException("Hatong must have voted on the previous day.");
        }

        boolean fatcatVoted = !gameHelper.isAbilityDisabled(gameState, playerId) && gameHelper.hasFatcatVotedYesterday(gameState);

        moveToNextPhase(gameState);
        gameStore.saveGame(gameState);
        return fatcatVoted ? "Hatong check: Fatcat voted yesterday."
                : "Hatong check: Fatcat did not vote yesterday.";
    }

    public String xiaoxiangAction(String roomId, Long playerId) {
        GameState gameState = gameHelper.validateAndGetGame(roomId, GamePhase.XIAOXIANG_ACTION);
        gameHelper.validateAndGetEffectivePlayer(gameState, playerId, Role.XIAOXIANG);

        long allianceCount = gameHelper.isAbilityDisabled(gameState, playerId) ? 0 : gameHelper.countAntiFatcatAlliance(gameState);

        moveToNextPhase(gameState);
        gameStore.saveGame(gameState);
        return "Xiaoxiang check: anti-Fatcat alliance players alive = " + allianceCount + ".";
    }

    public String mubaimuAction(String roomId, Long playerId, List<Long> targetIds) {
        GameState gameState = gameHelper.validateAndGetGame(roomId, GamePhase.MUBAIMU_ACTION);
        gameHelper.validateAndGetPlayer(gameState, playerId, Role.MUBAIMU);

        if (gameHelper.isAbilityDisabled(gameState, playerId)) {
            moveToNextPhase(gameState);
            gameStore.saveGame(gameState);
            return "Mubaimu action: tart shared safely.";
        }

        List<Long> targets = targetIds == null ? List.of() : targetIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (targets.size() > 3) {
            throw new IllegalArgumentException("Mubaimu can share tarts with at most 3 players.");
        }

        boolean fedFatcatFaction = false;
        for (Long targetId : targets) {
            targetId = redirectToXiaoenIfNeeded(gameState, playerId, targetId);
            PlayerState target = gameHelper.getAlivePlayer(gameState, targetId);
            if (target.getUserId().equals(playerId)) {
                throw new IllegalArgumentException("Mubaimu cannot give a tart to self.");
            }
            if (gameHelper.markBarkKingNoShowIfNeeded(gameState, target)) {
                continue;
            }
            if (gameHelper.isFatcatFaction(target.getRole())) {
                fedFatcatFaction = true;
            }
        }

        if (fedFatcatFaction) {
            gameState.getMubaimuDoomRounds().put(playerId, gameState.getCurrentRound() + 1);
        }

        moveToNextPhase(gameState);
        gameStore.saveGame(gameState);
        return fedFatcatFaction
                ? "Mubaimu action: tart shared. You will be kicked next night."
                : "Mubaimu action: tart shared safely.";
    }

    public String shushuAction(String roomId, Long playerId, Long targetId1, Long targetId2) {
        GameState gameState = gameHelper.validateAndGetGame(roomId, GamePhase.SHUSHU_ACTION);
        gameHelper.validateAndGetPlayer(gameState, playerId, Role.SHUSHU);

        if (gameHelper.isAbilityDisabled(gameState, playerId)) {
            moveToNextPhase(gameState);
            gameStore.saveGame(gameState);
            return "Shushu travel: the companions appeared to be from different factions.";
        }

        if (targetId1 == null || targetId2 == null) {
            throw new IllegalArgumentException("Shushu must choose two travel companions.");
        }
        if (targetId1.equals(targetId2)) {
            throw new IllegalArgumentException("Shushu must choose two different companions.");
        }
        if (targetId1.equals(playerId) || targetId2.equals(playerId)) {
            throw new IllegalArgumentException("Shushu cannot travel with self.");
        }

        targetId1 = redirectToXiaoenIfNeeded(gameState, playerId, targetId1);
        targetId2 = redirectToXiaoenIfNeeded(gameState, playerId, targetId2);
        PlayerState first = gameHelper.getAlivePlayer(gameState, targetId1);
        PlayerState second = gameHelper.getAlivePlayer(gameState, targetId2);

        boolean firstNoShow = gameHelper.markBarkKingNoShowIfNeeded(gameState, first);
        boolean secondNoShow = gameHelper.markBarkKingNoShowIfNeeded(gameState, second);
        boolean sameDisplayedFaction = false;
        if (!firstNoShow && !secondNoShow) {
            gameHelper.rememberRatManChecker(gameState, playerId, first);
            gameHelper.rememberRatManChecker(gameState, playerId, second);
            boolean firstFatcat = gameHelper.isDisplayedFatcatFaction(gameState, Role.SHUSHU, first);
            boolean secondFatcat = gameHelper.isDisplayedFatcatFaction(gameState, Role.SHUSHU, second);
            sameDisplayedFaction = firstFatcat == secondFatcat;
            if (sameDisplayedFaction) {
                gameState.getNightActions().put("SHUSHU_DOOM", playerId);
            }
        }

        moveToNextPhase(gameState);
        gameStore.saveGame(gameState);

        if (firstNoShow || secondNoShow) {
            return "Shushu travel: one or more companions no-showed, so the trip had no effect.";
        }

        String message = "Shushu travel: player " + first.getUserId() + " sees player " + second.getUserId()
                + ", and player " + second.getUserId() + " sees player " + first.getUserId() + ".";
        gameState.getPrivateMessages().put(first.getUserId(),
                "Your travel companion is player " + second.getUserId() + " (" + second.getUsername() + ").");
        gameState.getPrivateMessages().put(second.getUserId(),
                "Your travel companion is player " + first.getUserId() + " (" + first.getUsername() + ").");
        gameStore.saveGame(gameState);
        return sameDisplayedFaction
                ? message + " They displayed as the same faction, so Shushu was killed in the morning."
                : message + " They displayed as different factions.";
    }

    public String grassBeanAction(String roomId, Long playerId) {
        GameState gameState = gameHelper.validateAndGetGame(roomId, GamePhase.GRASS_BEAN_ACTION);
        gameHelper.validateAndGetEffectivePlayer(gameState, playerId, Role.GRASS_BEAN);

        if (gameHelper.isAbilityDisabled(gameState, playerId)) {
            moveToNextPhase(gameState);
            gameStore.saveGame(gameState);
            return "Grass Bean check: no alive Fatcat horcrux was found.";
        }

        List<PlayerState> horcruxes = gameState.getPlayers().stream()
                .filter(PlayerState::isAlive)
                .filter(player -> gameHelper.isFatcatHorcrux(player.getRole()))
                .filter(player -> player.getRole() != Role.RAT_MAN)
                .filter(player -> !(player.getRole() == Role.PH_SERVICE
                        && gameState.getPhServiceStolenRole() != null
                        && gameState.getCurrentRound() >= 2))
                .toList();

        moveToNextPhase(gameState);
        gameStore.saveGame(gameState);

        if (horcruxes.isEmpty()) {
            return "Grass Bean check: no alive Fatcat horcrux was found.";
        }

        PlayerState target = horcruxes.get(random.nextInt(horcruxes.size()));
        gameHelper.rememberRatManChecker(gameState, playerId, target);
        return "Grass Bean check: player " + target.getUserId() + " is a Fatcat horcrux.";
    }

    public String andyAction(String roomId, Long playerId) {
        GameState gameState = gameHelper.validateAndGetGame(roomId, GamePhase.ANDY_ACTION);
        gameHelper.validateAndGetEffectivePlayer(gameState, playerId, Role.ANDY);

        if (gameHelper.isAbilityDisabled(gameState, playerId)) {
            moveToNextPhase(gameState);
            gameStore.saveGame(gameState);
            return "Andy check: no alive volunteer-army player was found.";
        }

        if (gameState.getAndyCloudPlayerId() == null) {
            List<PlayerState> candidates = gameState.getPlayers().stream()
                    .filter(PlayerState::isAlive)
                    .filter(player -> !player.getUserId().equals(playerId))
                    .filter(player -> gameHelper.isVolunteerArmy(player.getRole()))
                    .toList();

            if (!candidates.isEmpty()) {
                PlayerState cloud = candidates.get(random.nextInt(candidates.size()));
                gameState.setAndyCloudPlayerId(cloud.getUserId());
            }
        }

        Long cloudId = gameState.getAndyCloudPlayerId();
        moveToNextPhase(gameState);
        gameStore.saveGame(gameState);

        if (cloudId == null) {
            return "Andy check: no alive volunteer-army player was found.";
        }

        PlayerState cloud = gameHelper.getPlayer(gameState, cloudId);
        return "Andy check: your cloud is player " + cloud.getUserId() + " ("
                + cloud.getUsername() + "), role " + translateRole(cloud.getRole()) + ".";
    }

    public String xiangxiangAction(String roomId, Long playerId) {
        GameState gameState = gameHelper.validateAndGetGame(roomId, GamePhase.XIANGXIANG_ACTION);
        PlayerState actor = gameHelper.validateAndGetEffectivePlayer(gameState, playerId, Role.XIANGXIANG);

        Integer actorSeat = gameHelper.getEffectiveSeatNumber(gameState, actor);
        if (actorSeat == null) {
            throw new IllegalStateException("Xiangxiang must have a seat number.");
        }
        int count = gameHelper.isAbilityDisabled(gameState, playerId) ? 0 : gameHelper.countAdjacentFatcats(gameState, actorSeat);
        moveToNextPhase(gameState);
        gameStore.saveGame(gameState);
        return "Xiangxiang check: found " + count + " adjacent Fatcat-side players.";
    }

    public String acCatAction(String roomId, Long playerId) {
        GameState gameState = gameHelper.validateAndGetGame(roomId, GamePhase.AC_CAT_ACTION);
        gameHelper.validateAndGetEffectivePlayer(gameState, playerId, Role.AC_CAT);

        if (gameHelper.isAbilityDisabled(gameState, playerId)) {
            moveToNextPhase(gameState);
            gameStore.saveGame(gameState);
            return "AC Cat check: no player was exiled yesterday.";
        }

        Long exiledId = gameState.getLastExiledPlayerId();
        if (exiledId == null) {
            moveToNextPhase(gameState);
            gameStore.saveGame(gameState);
            return "AC Cat check: no player was exiled yesterday.";
        }

        PlayerState exiled = gameHelper.getPlayer(gameState, exiledId);
        String roleName = translateRole(exiled.getRole());
        moveToNextPhase(gameState);
        gameStore.saveGame(gameState);
        return "AC Cat check: yesterday's exiled role was " + roleName + ".";
    }

    public String mochiBossCheckAction(String roomId, Long playerId, Long targetId) {
        GameState gameState = gameHelper.validateAndGetGame(roomId, GamePhase.MOCHI_BOSS_ACTION);
        gameHelper.validateAndGetEffectivePlayer(gameState, playerId, Role.MOCHI_BOSS);

        Long pendingPlayerId = gameState.getMochiBossPendingCheckPlayerId();
        if (pendingPlayerId == null || !pendingPlayerId.equals(playerId)) {
            throw new IllegalStateException("Mochi Boss has no pending Fatcat check.");
        }

        targetId = redirectToXiaoenIfNeeded(gameState, playerId, targetId);
        PlayerState target = gameHelper.getAlivePlayer(gameState, targetId);
        gameHelper.rememberRatManChecker(gameState, playerId, target);
        gameState.setMochiBossPendingCheckPlayerId(null);
        gameState.setCurrentPhase(GamePhase.DAY_START);
        gameStore.saveGame(gameState);

        return !gameHelper.isAbilityDisabled(gameState, playerId)
                && gameHelper.isDisplayedFatcatFaction(gameState, Role.MOCHI_BOSS, target)
                ? "Mochi Boss check: selected player is Fatcat."
                : "Mochi Boss check: selected player is not Fatcat.";
    }


    private Long redirectToXiaoenIfNeeded(GameState gameState, Long actorId, Long targetId) {
        if (targetId == null) return null;
        PlayerState target = gameHelper.getAlivePlayer(gameState, targetId);
        if (!gameHelper.isFatcatFaction(target.getRole())) return targetId;
        if (gameState.getXiaoenRedirectRound() != null
                && gameState.getXiaoenRedirectRound().equals(gameState.getCurrentRound())) {
            return targetId;
        }

        PlayerState xiaoen = gameState.getPlayers().stream()
                .filter(player -> player.isAlive() && player.getRole() == Role.XIAOEN)
                .findFirst()
                .orElse(null);
        if (xiaoen == null || xiaoen.getUserId().equals(actorId)) return targetId;

        gameState.setXiaoenRedirectRound(gameState.getCurrentRound());
        return xiaoen.getUserId();
    }

    private String buildFatcatTeamHint(GameState gameState, PlayerState actor) {
        List<PlayerState> seenPlayers = gameState.getPlayers().stream()
                .filter(PlayerState::isAlive)
                .filter(player -> !player.getUserId().equals(actor.getUserId()))
                .filter(player -> actor.getRole() == Role.FATCAT
                        ? gameHelper.isSeenAsFatcatHorcruxBy(actor.getRole(), player.getRole())
                        : gameHelper.isSeenAsFatcatBy(actor.getRole(), player.getRole()))
                .toList();

        String label = actor.getRole() == Role.FATCAT ? "Fatcat horcruxes" : "Fatcats";
        if (seenPlayers.isEmpty()) {
            return "First-night team hint: no visible " + label + " found.";
        }

        String players = seenPlayers.stream()
                .map(player -> player.getUserId() + " (" + player.getUsername() + ")")
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        return "First-night team hint: visible " + label + " = " + players + ".";
    }

    private String buildFatcatAbsentVolunteerHint(GameState gameState) {
        if (gameState.getFatcatAbsentVolunteerHintRoles() == null) {
            gameState.setFatcatAbsentVolunteerHintRoles(new ArrayList<>());
        }
        if (gameState.getFatcatAbsentVolunteerHintRoles().isEmpty()) {
            Set<Role> presentRoles = gameState.getPlayers().stream()
                    .map(PlayerState::getRole)
                    .collect(java.util.stream.Collectors.toSet());
            List<Role> absentRoles = new ArrayList<>(gameHelper.volunteerArmyRoles().stream()
                    .filter(role -> !presentRoles.contains(role))
                    .toList());
            Collections.shuffle(absentRoles);
            gameState.getFatcatAbsentVolunteerHintRoles().addAll(absentRoles.stream().limit(3).toList());
        }

        if (gameState.getFatcatAbsentVolunteerHintRoles().isEmpty()) {
            return "First-night Fatcat scout: no absent volunteer-army role found.";
        }

        String roles = gameState.getFatcatAbsentVolunteerHintRoles().stream()
                .map(this::translateRole)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        return "First-night Fatcat scout: absent volunteer-army roles = " + roles + ".";
    }

    private String buildAllRolesHint(GameState gameState) {
        String players = gameState.getPlayers().stream()
                .sorted(java.util.Comparator.comparing(player -> gameHelper.getEffectiveSeatNumber(gameState, player), java.util.Comparator.nullsLast(Integer::compareTo)))
                .map(player -> "seat " + (gameHelper.getEffectiveSeatNumber(gameState, player) == null ? "-" : gameHelper.getEffectiveSeatNumber(gameState, player))
                        + ": player " + player.getUserId()
                        + " (" + player.getUsername() + ") = " + translateRole(player.getRole()))
                .reduce((left, right) -> left + "; " + right)
                .orElse("");
        return "Emperor reveal: " + players + ".";
    }

    private String buildScrambledAllRolesHint(GameState gameState) {
        List<Role> roles = new ArrayList<>(gameState.getPlayers().stream()
                .map(PlayerState::getRole)
                .toList());
        Collections.shuffle(roles);

        List<PlayerState> players = gameState.getPlayers().stream()
                .sorted(java.util.Comparator.comparing(player -> gameHelper.getEffectiveSeatNumber(gameState, player), java.util.Comparator.nullsLast(Integer::compareTo)))
                .toList();

        String revealed = "";
        for (int i = 0; i < players.size(); i++) {
            PlayerState player = players.get(i);
            Integer effectiveSeat = gameHelper.getEffectiveSeatNumber(gameState, player);
            String item = "seat " + (effectiveSeat == null ? "-" : effectiveSeat)
                    + ": player " + player.getUserId()
                    + " (" + player.getUsername() + ") = " + translateRole(roles.get(i));
            revealed = revealed.isEmpty() ? item : revealed + "; " + item;
        }
        return "Emperor reveal: " + revealed + ".";
    }

    private boolean isNightPhase(GamePhase phase) {
        if (phase == GamePhase.MOCHI_BOSS_ACTION) return true;
        for (GamePhase nightPhase : NIGHT_SEQUENCE) {
            if (nightPhase == phase && phase != GamePhase.DAY_START) return true;
        }
        return false;
    }

    private String buildGuoguoHint(GameState gameState, Long playerId) {
        List<PlayerState> volunteers = gameState.getPlayers().stream()
                .filter(player -> player.isAlive())
                .filter(player -> !player.getUserId().equals(playerId))
                .filter(player -> gameHelper.isVolunteerArmy(player.getRole()))
                .toList();

        if (volunteers.isEmpty()) {
            return "Guoguo hint: no alive volunteer-army role was found.";
        }

        PlayerState roleSource = volunteers.get(random.nextInt(volunteers.size()));
        List<PlayerState> others = gameState.getPlayers().stream()
                .filter(player -> player.isAlive())
                .filter(player -> !player.getUserId().equals(playerId))
                .filter(player -> !player.getUserId().equals(roleSource.getUserId()))
                .toList();

        if (others.isEmpty()) {
            return "Guoguo hint: not enough other alive players to build a hint.";
        }

        PlayerState decoy = others.get(random.nextInt(others.size()));
        boolean swap = random.nextBoolean();
        Long firstId = swap ? roleSource.getUserId() : decoy.getUserId();
        Long secondId = swap ? decoy.getUserId() : roleSource.getUserId();
        return "Guoguo hint: between player " + firstId + " and player " + secondId
                + ", one role is " + translateRole(roleSource.getRole()) + ".";
    }




    private void settleNightActions(GameState gameState) {
        Long fatcatKill = gameState.getNightActions().get("FATCAT_KILL");
        Long fatcatKillActor = gameState.getNightActions().get("FATCAT_KILL_ACTOR");
        Long liverDebuff = gameState.getNightActions().get("LIVER_DEBUFF");
        Long canManId = gameState.getNightActions().get("CAN_MAN_ID");
        Long canManDrink = gameState.getNightActions().get("CAN_MAN_DRINK");
        Long nangongId = gameState.getNightActions().get("NANGONG_ID");
        Long nangongTarget = gameState.getNightActions().get("NANGONG_TARGET");
        Long shushuDoom = gameState.getNightActions().get("SHUSHU_DOOM");
        Set<Long> drunkenPlayerIds = new HashSet<>();

        if (nangongTarget != null && !Objects.equals(nangongId, liverDebuff)) {
            PlayerState target = gameState.getPlayers().stream()
                    .filter(player -> player.getUserId().equals(nangongTarget))
                    .findFirst()
                    .orElse(null);

            if (target != null) {
                drunkenPlayerIds.add(nangongId);
                gameState.getDrunkUntilRounds().put(nangongId, gameState.getCurrentRound() + 1);
                if (target.getRole() != Role.HIGH_RABBIT) {
                    drunkenPlayerIds.add(nangongTarget);
                    gameState.getDrunkUntilRounds().put(nangongTarget, gameState.getCurrentRound() + 1);
                }

                if (target.getRole() == Role.LIVER_INDEX) {
                    gameHelper.setDead(gameState, nangongTarget);
                    liverDebuff = null;
                }
            }
        }

        if (canManId != null && canManId.equals(liverDebuff)) {
            canManDrink = null;
        }

        boolean fatcatKillDisabled = Objects.equals(fatcatKillActor, liverDebuff)
                || gameHelper.isDrunk(gameState, fatcatKillActor);

        if (fatcatKill != null && !fatcatKillDisabled) {
            PlayerState killTarget = gameState.getPlayers().stream()
                    .filter(player -> player.getUserId().equals(fatcatKill))
                    .findFirst()
                    .orElse(null);

            boolean protectedByCanMan = canManDrink != null
                    && !canManDrink.equals(canManId)
                    && fatcatKill.equals(canManDrink);

            if (killTarget != null && gameHelper.canActAs(gameState, killTarget, Role.MOCHI_BOSS) && killTarget.isAlive()) {
                gameState.setMochiBossPendingCheckPlayerId(fatcatKill);
            } else if (!protectedByCanMan) {
                gameState.getFatcatKilledPlayerIds().add(fatcatKill);
                gameHelper.setDead(gameState, fatcatKill);
            }
        }

        if (canManId != null && canManDrink != null) {
            boolean canManDrinkingDied = false;
            if (gameHelper.hasOverdrinkingPenalty(gameState, canManId, liverDebuff, drunkenPlayerIds)
                    || gameHelper.hasOverdrinkingPenalty(gameState, canManDrink, liverDebuff, drunkenPlayerIds)) {
                gameHelper.setDead(gameState, canManId);
                gameHelper.setDead(gameState, canManDrink);
                canManDrinkingDied = true;
            }

        }

        settleMubaimuDoom(gameState);
        settleBarkKingDoom(gameState);
        if (shushuDoom != null) {
            gameHelper.setDead(gameState, shushuDoom);
        }
        settleChenAction(gameState);

        if (canManId != null && canManDrink != null && !gameHelper.isPlayerAlive(gameState, canManId)) {
            gameHelper.setDead(gameState, canManDrink);
        }

        gameState.getDrunkUntilRounds().entrySet().removeIf(
                entry -> entry.getValue() <= gameState.getCurrentRound()
        );

        gameState.getNightActions().clear();
    }





    private void settleChenAction(GameState gameState) {
        Long victimId = gameState.getChenPendingKillPlayerId();
        if (victimId == null) return;

        gameState.setChenPendingKillPlayerId(null);
        gameHelper.setDead(gameState, victimId);
    }

    private void settleMubaimuDoom(GameState gameState) {
        if (gameState.getMubaimuDoomRounds() == null || gameState.getMubaimuDoomRounds().isEmpty()) return;

        List<Long> doomedIds = gameState.getMubaimuDoomRounds().entrySet().stream()
                .filter(entry -> entry.getValue() <= gameState.getCurrentRound())
                .map(Map.Entry::getKey)
                .toList();

        for (Long doomedId : doomedIds) {
            gameState.getMubaimuDoomRounds().remove(doomedId);
            gameHelper.setDead(gameState, doomedId);
        }
    }

    private void settleBarkKingDoom(GameState gameState) {
        if (gameState.getBarkKingDoomRounds() == null || gameState.getBarkKingDoomRounds().isEmpty()) return;

        List<Long> doomedIds = gameState.getBarkKingDoomRounds().entrySet().stream()
                .filter(entry -> entry.getValue() <= gameState.getCurrentRound())
                .map(Map.Entry::getKey)
                .toList();

        for (Long doomedId : doomedIds) {
            gameState.getBarkKingDoomRounds().remove(doomedId);
            gameHelper.setDead(gameState, doomedId);
        }
    }

    private String translateRole(Role role) {
        return role == null ? "未知角色" : role.getDisplayName();
    }
}
