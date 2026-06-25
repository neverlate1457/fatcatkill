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
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;

@Service
public class RoomService {

    private final GameStore gameStore;
    private final SystemOutService systemOutService;

    public RoomService(GameStore gameStore, SystemOutService systemOutService) {
        this.gameStore = gameStore;
        this.systemOutService = systemOutService;
    }

    public void startGame(String roomId, String gameMode) {
        startGame(roomId, gameMode, null, null, null, null, false);
    }

    public void startGame(String roomId, String gameMode, List<Role> customDeck,
                          List<Role> fatcatHintRoles, Role highRabbitRole,
                          Long methaneHallucinationTargetId, boolean hostMode) {
        GameState gameState = gameStore.getGame(roomId);
        if (gameState == null || gameState.getStatus() == RoomStatus.PLAYING) {
            throw new IllegalStateException("房間不存在或遊戲已經在進行中！");
        }

        int playerCount = gameState.getPlayers().size();

        // 1. 把房間人數傳給牌庫總管
        List<Role> roleDeck = customDeck == null
                ? buildRoleDeck(gameMode, playerCount)
                : validateCustomDeck(customDeck, playerCount);
        
        // 防呆：檢查牌庫數量跟房間人數對不對得上
        if (roleDeck.size() != playerCount) {
            throw new IllegalArgumentException("房間人數 (" + playerCount + ") 與選擇的模式 [" + gameMode + "] 不符！(該模式可能尚未支援此人數)");
        }

        // 2. 洗牌 / 主持人自訂牌組照座位發牌
        if (customDeck == null) {
            List<Role> fixedRoles = gameState.getPlayers().stream()
                    .map(PlayerState::getRole)
                    .filter(role -> role != null)
                    .toList();
            for (Role fixedRole : fixedRoles) {
                roleDeck.remove(fixedRole);
            }
            Collections.shuffle(roleDeck);
        }

        // 3. 發牌
        int deckIndex = 0;
        for (int i = 0; i < playerCount; i++) {
            PlayerState player = gameState.getPlayers().get(i);
            if (customDeck != null) {
                player.setRole(roleDeck.get(i));
            } else if (player.getRole() == null) {
                if (deckIndex >= roleDeck.size()) {
                    throw new IllegalArgumentException("Not enough roles left after applying test role assignments.");
                }
                player.setRole(roleDeck.get(deckIndex++));
            }
            player.setSeatNumber(i + 1);
            player.setAlive(true);
            player.setVotedTargetId(null);
            player.setVoteConfirmed(false);
        }

        if (gameState.getPlayers().stream().noneMatch(player -> player.getRole() == Role.FATCAT)) {
            throw new IllegalArgumentException("Cannot start game: Fatcat role is missing. Please assign one player as Fatcat or clear a fixed role.");
        }

        // ==========================================
        // 🌟 新增：甲烷的幻覺鎖定邏輯
        // ==========================================
        boolean hasMethane = gameState.getPlayers().stream().anyMatch(p -> p.getRole() == Role.METHANE);
        if (hasMethane) {
            List<PlayerState> innocentGoodGuys = gameState.getPlayers().stream()
                    .filter(p -> p.getRole() != Role.FATCAT && p.getRole() != Role.WEREWOLF && p.getRole() != Role.METHANE)
                    .toList();

            if (!innocentGoodGuys.isEmpty()) {
                PlayerState target = null;
                if (methaneHallucinationTargetId != null) {
                    target = innocentGoodGuys.stream()
                            .filter(player -> player.getUserId().equals(methaneHallucinationTargetId))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Methane hallucination target must be a non-Methane non-Fatcat player."));
                } else {
                    target = innocentGoodGuys.get(new Random().nextInt(innocentGoodGuys.size()));
                }

                gameState.setMethaneHallucinationTargetId(target.getUserId());
                systemOutService.action(gameState, null, "METHANE_HALLUCINATION_LOCKED", target.getUserId(), null, "Methane hallucination target locked.");
            }
        }
        // ==========================================

        validateHostSettings(gameState, fatcatHintRoles, highRabbitRole);
        gameState.setHostMode(hostMode);
        gameState.setHostConfiguredFatcatHintRoles(fatcatHintRoles == null ? null : new ArrayList<>(fatcatHintRoles));
        gameState.setHostConfiguredHighRabbitRole(highRabbitRole);
        gameState.setGameMode(customDeck == null ? gameMode : "CUSTOM");
        gameState.setGameId(java.util.UUID.randomUUID().toString());
        gameState.setStartedAt(java.time.LocalDateTime.now().toString());
        gameState.setHistoryRecorded(false);
        gameState.setWinnerCamp(null);
        gameState.setStatus(RoomStatus.PLAYING);
        boolean hasStr = gameState.getPlayers().stream().anyMatch(p -> p.isAlive() && p.getRole() == Role.STR);
        boolean hasPhService = gameState.getPlayers().stream().anyMatch(p -> p.isAlive() && p.getRole() == Role.PH_SERVICE);
        gameState.setCurrentPhase(hasStr ? GamePhase.STR_ACTION : hasPhService ? GamePhase.PH_SERVICE_ACTION : GamePhase.NIGHT_START);
        gameState.setCurrentRound(1);
        gameState.setAndyCloudPlayerId(null);
        gameState.setMochiBossPendingCheckPlayerId(null);
        gameState.setFatcatKillerPlayerId(gameState.getPlayers().stream()
                .filter(player -> player.getRole() == Role.FATCAT)
                .map(PlayerState::getUserId)
                .findFirst()
                .orElse(null));
        gameState.setChenPendingKillPlayerId(null);
        gameState.setPhServiceStolenRole(null);
        gameState.setXiaoenRedirectRound(null);
        gameState.getFatcatAbsentVolunteerHintRoles().clear();
        if (fatcatHintRoles != null) {
            gameState.getFatcatAbsentVolunteerHintRoles().addAll(fatcatHintRoles);
        }
        gameState.getHighRabbitPerceivedRoles().clear();
        gameState.getNightActions().clear();
        gameState.getDelayedDeathRounds().clear();
        gameState.getMubaimuDoomRounds().clear();
        gameState.getBarkKingDoomRounds().clear();
        gameState.getDrunkUntilRounds().clear();
        gameState.getPrivateMessages().clear();
        gameState.getStrOriginalSeatNumbers().clear();
        gameState.getStrTemporarySeatNumbers().clear();
        gameState.getStrSwappedSeatNumbers().clear();
        gameState.getChenUsedPlayerIds().clear();
        gameState.getChenSkippedRounds().clear();
        gameState.getRatManCheckerIds().clear();
        gameState.getSaltedFishSkippedRounds().clear();
        gameState.getNangongUsedPlayerIds().clear();
        gameState.getFinalVoteEligiblePlayerIds().clear();
        gameState.getFatcatKilledPlayerIds().clear();
        assignHighRabbitPerceivedRoles(gameState, highRabbitRole);

        gameStore.saveGame(gameState);
        systemOutService.action(gameState, null, "ROOM_START_GAME", null, null, "Game started. mode=" + gameMode + " players=" + playerCount);
    }

    /**
     * 牌庫總管：先判斷卡池(Mode)，再依照人數(Count)去呼叫對應的發牌邏輯
     */
    private void assignHighRabbitPerceivedRoles(GameState gameState, Role configuredRole) {
        gameState.getHighRabbitPerceivedRoles().clear();
        Random random = new Random();
        List<PlayerState> players = gameState.getPlayers();

        players.stream()
                .filter(player -> player.getRole() == Role.HIGH_RABBIT)
                .forEach(highRabbit -> {
                    List<Role> candidateRoles = players.stream()
                            .filter(player -> !player.getUserId().equals(highRabbit.getUserId()))
                            .map(PlayerState::getRole)
                            .toList();
                    if (!candidateRoles.isEmpty()) {
                        Role perceivedRole = configuredRole != null
                                ? configuredRole
                                : candidateRoles.get(random.nextInt(candidateRoles.size()));
                        gameState.getHighRabbitPerceivedRoles().put(highRabbit.getUserId(), perceivedRole);
                    }
                });
    }

    private List<Role> validateCustomDeck(List<Role> customDeck, int playerCount) {
        if (customDeck.size() != playerCount || customDeck.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Custom deck must contain exactly " + playerCount + " roles.");
        }
        Set<Role> uniqueRoles = new HashSet<>(customDeck);
        if (uniqueRoles.size() != customDeck.size()) {
            throw new IllegalArgumentException("Custom deck cannot contain duplicate roles.");
        }
        if (customDeck.stream().filter(role -> role == Role.FATCAT).count() != 1) {
            throw new IllegalArgumentException("Custom deck must contain exactly one Fatcat.");
        }
        return new ArrayList<>(customDeck);
    }

    private void validateHostSettings(GameState gameState, List<Role> fatcatHintRoles, Role highRabbitRole) {
        Set<Role> presentRoles = gameState.getPlayers().stream().map(PlayerState::getRole).collect(java.util.stream.Collectors.toSet());
        if (fatcatHintRoles != null) {
            if (fatcatHintRoles.size() > 3 || new HashSet<>(fatcatHintRoles).size() != fatcatHintRoles.size()) {
                throw new IllegalArgumentException("Fatcat absent-role hints must contain up to three unique roles.");
            }
            for (Role role : fatcatHintRoles) {
                if (!isVolunteerRole(role) || presentRoles.contains(role)) {
                    throw new IllegalArgumentException("Fatcat hint role must be an absent volunteer role: " + role);
                }
            }
        }
        if (highRabbitRole != null && (!presentRoles.contains(Role.HIGH_RABBIT)
                || !presentRoles.contains(highRabbitRole) || highRabbitRole == Role.HIGH_RABBIT)) {
            throw new IllegalArgumentException("High Rabbit illusion must be another role present in the custom deck.");
        }
    }

    private boolean isVolunteerRole(Role role) {
        return List.of(Role.METHANE, Role.GUOGUO, Role.XIANGXIANG, Role.AC_CAT, Role.FORVKUSA,
                Role.HATONG, Role.KB, Role.SALTED_FISH, Role.XIAOXIANG, Role.MOCHI_BOSS,
                Role.GRASS_BEAN, Role.NANGONG, Role.CASTER, Role.ANDY, Role.CAN_MAN,
                Role.SINGLE_DOG, Role.STR).contains(role);
    }

    private List<Role> buildRoleDeck(String gameMode, int playerCount) {
        if ("OLD_HOME".equalsIgnoreCase(gameMode)) {
            return buildOldHomeDeck(playerCount); 
        } 
        return new ArrayList<>(); // 找不到卡池時防呆
    }
    private List<Role> buildOldHomeDeck(int playerCount) {
        List<Role> deck = new ArrayList<>();
        
        // 分靈體池
        List<Role> horcruxes = List.of(Role.LIVER_INDEX, Role.PINK_RABBIT, Role.EMPEROR, Role.NTHU_MATH, Role.MAGIC_MEOW, Role.PH_SERVICE, Role.RAT_MAN);
        // 同盟池
        List<Role> allies = List.of(Role.HIGH_RABBIT, Role.MEATBUN, Role.MUBAIMU, Role.CHEN, Role.XIAOEN, Role.NUKO, Role.SHUSHU, Role.BARK_KING);
        // 義勇軍池
        List<Role> army = List.of(Role.METHANE, Role.GUOGUO, Role.XIANGXIANG, Role.AC_CAT, Role.FORVKUSA, Role.HATONG, Role.KB, Role.SALTED_FISH, Role.XIAOXIANG, Role.MOCHI_BOSS, Role.GRASS_BEAN, Role.NANGONG, Role.CASTER, Role.ANDY, Role.CAN_MAN, Role.SINGLE_DOG, Role.STR);

        if (playerCount == 7) {
            deck.add(Role.FATCAT); // 1 肥貓本體
            deck.addAll(drawRandomRoles(horcruxes, 1)); // 1 分靈體
            deck.addAll(drawRandomRoles(allies, 1)); // 1 同盟
            deck.addAll(drawRandomRoles(army, 4)); // 4 義勇軍
            return deck;
        } 
        else if (playerCount == 10) {
            deck.add(Role.FATCAT); // 1 肥貓本體
            deck.addAll(drawRandomRoles(horcruxes, 2)); // 2 分靈體
            deck.addAll(drawRandomRoles(allies, 2)); // 2 同盟
            deck.addAll(drawRandomRoles(army, 5)); // 5 義勇軍
            return deck;
        }
        
        return new ArrayList<>();
    }


    private List<Role> drawRandomRoles(List<Role> pool, int count) {
        List<Role> shuffledPool = new ArrayList<>(pool);
        Collections.shuffle(shuffledPool); // 打亂池子
        return new ArrayList<>(shuffledPool.subList(0, count)); // 抽出前 N 個
    }


    private boolean requiresFatcat(String gameMode) {
        return !"CLASSIC".equalsIgnoreCase(gameMode);
    }

}
