package com.fatcatkill.service;

import com.fatcatkill.enums.GamePhase;
import com.fatcatkill.enums.RoomStatus;
import com.fatcatkill.model.GameState;
import com.fatcatkill.model.PlayerState;
import com.fatcatkill.store.GameStore;
import com.fatcatkill.enums.Role;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;

@Service
public class GameHelperService {

    private final GameStore gameStore;

    public GameHelperService(GameStore gameStore) {
        this.gameStore = gameStore;
    }

    /**
     * 🛠️ 驗證遊戲狀態與當前階段
     */
    public GameState validateAndGetGame(String roomId, GamePhase... allowedPhases) {
        GameState game = gameStore.getGame(roomId);
        if (game == null || game.getStatus() != RoomStatus.PLAYING) {
            throw new IllegalStateException("遊戲未開始");
        }
        boolean phaseValid = false;
        for (GamePhase phase : allowedPhases) {
            if (game.getCurrentPhase() == phase) phaseValid = true;
        }
        if (!phaseValid) {
            throw new IllegalStateException("當前階段不允許此行動");
        }
        return game;
    }

    /**
     * 🛠️ 根據 ID 尋找玩家，並確認他是否還活著
     * (已將原本的 validateAlive 與 getAlivePlayer 合併為此方法)
     */
    public PlayerState getAlivePlayer(GameState game, Long playerId) {
        PlayerState p = game.getPlayers().stream()
                .filter(player -> player.getUserId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("找不到玩家 ID: " + playerId));

        if (!p.isAlive()) {
            throw new IllegalStateException("玩家 " + playerId + " 已死亡，無法被選定或執行動作！");
        }
        return p;
    }

    /**
     * 🛠️ 判定特定玩家是否存活 (回傳 boolean，不報錯)
     */
    public boolean isPlayerAlive(GameState game, Long playerId) {
        return game.getPlayers().stream()
                .filter(p -> p.getUserId().equals(playerId))
                .findFirst()
                .map(PlayerState::isAlive)
                .orElse(false);
    }

    /**
     * 🛠️ 直接將指定玩家設為死亡
     */
    public void setDead(GameState game, Long targetId) {
        setDead(game, targetId, true);
    }

    public void setDead(GameState game, Long targetId, boolean allowSingleDogDelay) {
        PlayerState target = game.getPlayers().stream()
                .filter(p -> p.getUserId().equals(targetId))
                .findFirst()
                .orElse(null);

        if (target == null || !target.isAlive()) return;

        if (allowSingleDogDelay && canActAs(game, target, Role.SINGLE_DOG)) {
            if (game.getDelayedDeathRounds() != null && !game.getDelayedDeathRounds().containsKey(targetId)) {
                game.getDelayedDeathRounds().put(targetId, game.getCurrentRound() + 1);
            }
            return;
        }

        target.setAlive(false);
        if (game.getFinalVoteEligiblePlayerIds() != null) {
            game.getFinalVoteEligiblePlayerIds().add(targetId);
        }
        if (target.getRole() == Role.FATCAT) {
            promoteMagicMeowOrEndGame(game);
        }
        if (game.getAndyCloudPlayerId() != null && game.getAndyCloudPlayerId().equals(targetId)) {
            game.getPlayers().stream()
                    .filter(p -> p.isAlive() && canActAs(game, p, Role.ANDY))
                    .findFirst()
                    .ifPresent(andy -> andy.setAlive(false));
        }
    }

    private void promoteMagicMeowOrEndGame(GameState game) {
        PlayerState successor = game.getPlayers().stream()
                .filter(player -> player.isAlive() && player.getRole() == Role.MAGIC_MEOW)
                .findFirst()
                .orElse(null);

        if (successor != null) {
            successor.setRole(Role.FATCAT);
            game.setFatcatKillerPlayerId(successor.getUserId());
            return;
        }

        game.setCurrentPhase(GamePhase.GAME_OVER);
        game.setStatus(RoomStatus.FINISHED);
    }

    public void processDelayedDeaths(GameState game) {
        if (game.getDelayedDeathRounds() == null || game.getDelayedDeathRounds().isEmpty()) return;

        Iterator<Map.Entry<Long, Integer>> iterator = game.getDelayedDeathRounds().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Integer> entry = iterator.next();
            if (entry.getValue() <= game.getCurrentRound()) {
                iterator.remove();
                setDead(game, entry.getKey(), false);
            }
        }
    }

    public void restoreStrSeatNumbers(GameState game) {
        if (game.getStrOriginalSeatNumbers() == null || game.getStrOriginalSeatNumbers().isEmpty()) {
            if (game.getStrTemporarySeatNumbers() != null) {
                game.getStrTemporarySeatNumbers().clear();
            }
            if (game.getStrSwappedSeatNumbers() != null) {
                game.getStrSwappedSeatNumbers().clear();
            }
            return;
        }

        game.getPlayers().stream()
                .filter(player -> game.getStrOriginalSeatNumbers().containsKey(player.getUserId()))
                .forEach(player -> player.setSeatNumber(game.getStrOriginalSeatNumbers().get(player.getUserId())));

        game.getStrOriginalSeatNumbers().clear();
        if (game.getStrTemporarySeatNumbers() != null) {
            game.getStrTemporarySeatNumbers().clear();
        }
        if (game.getStrSwappedSeatNumbers() != null) {
            game.getStrSwappedSeatNumbers().clear();
        }
    }

    public Integer getEffectiveSeatNumber(GameState game, PlayerState player) {
        if (game == null || player == null) return null;
        if (game.getStrTemporarySeatNumbers() != null
                && game.getStrTemporarySeatNumbers().containsKey(player.getUserId())) {
            return game.getStrTemporarySeatNumbers().get(player.getUserId());
        }
        return player.getSeatNumber();
    }
    /**
     * 🛠️ 輔助工具：驗證玩家是否存活 (若死亡或找不到則報錯)
     */
    public PlayerState validateAlive(GameState game, Long playerId) {
        PlayerState p = game.getPlayers().stream()
                .filter(player -> player.getUserId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("找不到玩家 ID: " + playerId));

        if (!p.isAlive()) {
            throw new IllegalStateException("玩家 " + playerId + " 已死亡，無法執行動作！");
        }
        return p;
    }

    /**
     * 🛠️ 輔助工具：驗證玩家存活，並且檢查他的角色身分是否正確
     */
    public PlayerState validateAndGetPlayer(GameState game, Long playerId, Role expectedRole) {
        PlayerState player = validateAlive(game, playerId);
        if (player.getRole() != expectedRole) {
            throw new IllegalArgumentException("玩家身分不符，預期角色: " + expectedRole);
        }
        return player;
    }
    /**
     * 🛠️ 輔助方法：判定該身分是否屬於「義勇軍 (好人) 陣營」
     */
    public PlayerState validateAndGetEffectivePlayer(GameState game, Long playerId, Role expectedRole) {
        PlayerState player = validateAlive(game, playerId);
        if (!canActAs(game, player, expectedRole)) {
            throw new IllegalArgumentException("Player cannot act as " + expectedRole);
        }
        return player;
    }

    public boolean hasActorForRole(GameState game, Role expectedRole) {
        return game.getPlayers().stream()
                .anyMatch(player -> player.isAlive() && canActAs(game, player, expectedRole));
    }

    public boolean canActAs(GameState game, PlayerState player, Role expectedRole) {
        if (player == null || expectedRole == null) return false;
        return effectiveRole(game, player) == expectedRole;
    }

    public Role effectiveRole(GameState game, PlayerState player) {
        if (player == null) return null;
        if (player.getRole() == Role.PH_SERVICE && game.getPhServiceStolenRole() != null) {
            return game.getPhServiceStolenRole();
        }
        return player.getRole();
    }

    public java.util.List<Role> volunteerArmyRoles() {
        return java.util.List.of(
            Role.METHANE, Role.GUOGUO, Role.XIANGXIANG, Role.AC_CAT,
            Role.FORVKUSA, Role.HATONG, Role.KB, Role.SALTED_FISH,
            Role.XIAOXIANG, Role.MOCHI_BOSS, Role.GRASS_BEAN, Role.NANGONG,
            Role.CASTER, Role.ANDY, Role.CAN_MAN, Role.SINGLE_DOG, Role.STR
        );
    }

    public boolean isVolunteerArmy(Role role) {
        return volunteerArmyRoles().contains(role);
    }
    
    /**
     * 判定該身分是否屬於「肥貓陣營」
     */
    public boolean isAntiFatcatAlliance(Role role) {
        return java.util.List.of(
            Role.HIGH_RABBIT, Role.MEATBUN, Role.MUBAIMU, Role.CHEN,
            Role.XIAOEN, Role.NUKO, Role.SHUSHU, Role.BARK_KING
        ).contains(role);
    }

    public boolean isFatcatFaction(Role role) {
        return java.util.List.of(
            Role.FATCAT, Role.LIVER_INDEX, Role.PINK_RABBIT, Role.EMPEROR,
            Role.NTHU_MATH, Role.MAGIC_MEOW, Role.PH_SERVICE, Role.RAT_MAN,
            Role.MEATBUN
        ).contains(role);
    }

    public boolean isDisplayedFatcatFaction(GameState game, Role viewerRole, PlayerState target) {
        Role targetRole = effectiveRole(game, target);
        if (targetRole == Role.RAT_MAN) return false;
        if (target.getRole() == Role.PH_SERVICE && game.getPhServiceStolenRole() != null) {
            return game.getCurrentRound() < 2;
        }
        if (targetRole == Role.CASTER && isFatcatHorcrux(viewerRole)) return true;
        return isFatcatFaction(targetRole);
    }

    public void rememberRatManChecker(GameState game, Long checkerId, PlayerState target) {
        if (target != null && target.getRole() == Role.RAT_MAN && game.getRatManCheckerIds() != null) {
            game.getRatManCheckerIds().add(checkerId);
        }
    }

    public boolean isFatcatWinFaction(Role role) {
        return role == Role.FATCAT || isFatcatHorcrux(role);
    }

    public boolean canUseFatcatKill(GameState game, PlayerState actor) {
        if (actor == null || !actor.isAlive()) return false;
        if (game.getFatcatKillerPlayerId() != null) {
            return game.getFatcatKillerPlayerId().equals(actor.getUserId());
        }
        return actor.getRole() == Role.FATCAT;
    }

    public boolean transferFatcatKillToMagicMeow(GameState game) {
        PlayerState successor = game.getPlayers().stream()
                .filter(player -> player.isAlive() && player.getRole() == Role.MAGIC_MEOW)
                .findFirst()
                .orElse(null);
        if (successor == null) return false;
        game.setFatcatKillerPlayerId(successor.getUserId());
        return true;
    }

    public boolean isFatcatAlive(GameState game) {
        return hasAliveRole(game, Role.FATCAT);
    }

    public long countAntiFatcatAlliance(GameState game) {
        long count = game.getPlayers().stream()
                .filter(PlayerState::isAlive)
                .filter(player -> isAntiFatcatAlliance(player.getRole()))
                .count();
        boolean hasNthuMathInGame = game.getPlayers().stream()
                .anyMatch(player -> player.getRole() == Role.NTHU_MATH);
        return hasNthuMathInGame ? count + 2 : count;
    }

    public long countAliveVolunteerFaction(GameState game) {
        long count = game.getPlayers().stream()
                .filter(PlayerState::isAlive)
                .filter(player -> isVolunteerArmy(player.getRole()) || isAntiFatcatAlliance(player.getRole()))
                .count();
        boolean hasNthuMathInGame = game.getPlayers().stream()
                .anyMatch(player -> player.getRole() == Role.NTHU_MATH);
        return hasNthuMathInGame ? count + 2 : count;
    }

    public boolean isDrunk(GameState game, Long playerId) {
        if (game == null || playerId == null || game.getDrunkUntilRounds() == null) return false;
        Integer untilRound = game.getDrunkUntilRounds().get(playerId);
        return untilRound != null && game.getCurrentRound() <= untilRound;
    }

    public boolean isSameDisplayedFaction(Role firstRole, Role secondRole) {
        return displayedFaction(firstRole).equals(displayedFaction(secondRole));
    }

    private String displayedFaction(Role role) {
        return isFatcatFaction(role) ? "FATCAT" : "VOLUNTEER";
    }

    public boolean isFatcatHorcrux(Role role) {
        return java.util.List.of(
            Role.LIVER_INDEX, Role.PINK_RABBIT, Role.EMPEROR, Role.NTHU_MATH,
            Role.MAGIC_MEOW, Role.PH_SERVICE, Role.RAT_MAN
        ).contains(role);
    }

    public boolean hasAliveRole(GameState game, Role role) {
        return game.getPlayers().stream()
                .anyMatch(player -> player.isAlive() && player.getRole() == role);
    }

    public boolean isSeenAsFatcatBy(Role viewerRole, Role targetRole) {
        return targetRole == Role.FATCAT
                || (targetRole == Role.CASTER && isFatcatHorcrux(viewerRole));
    }

    public boolean isSeenAsFatcatHorcruxBy(Role viewerRole, Role targetRole) {
        return isFatcatHorcrux(targetRole)
                || (targetRole == Role.CASTER && viewerRole == Role.FATCAT);
    }
    
    /**
     * 取得場上任意玩家（不檢查是否存活）
     */
    public PlayerState getPlayer(GameState game, Long playerId) {
        return game.getPlayers().stream()
                .filter(player -> player.getUserId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("找不到玩家 ID: " + playerId));
    }
    
}
