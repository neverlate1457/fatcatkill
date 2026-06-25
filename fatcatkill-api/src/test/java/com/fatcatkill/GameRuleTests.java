package com.fatcatkill;

import com.fatcatkill.enums.GamePhase;
import com.fatcatkill.enums.Role;
import com.fatcatkill.enums.RoomStatus;
import com.fatcatkill.model.GameState;
import com.fatcatkill.model.PlayerState;
import com.fatcatkill.service.DayService;
import com.fatcatkill.service.GameHelperService;
import com.fatcatkill.service.NightService;
import com.fatcatkill.store.GameStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameRuleTests {

    private GameStore gameStore;
    private GameHelperService gameHelper;
    private DayService dayService;
    private NightService nightService;

    @BeforeEach
    void setUp() {
        gameStore = new GameStore();
        gameHelper = new GameHelperService(gameStore);
        dayService = new DayService(gameStore, gameHelper);
        nightService = new NightService(gameStore, gameHelper);
    }

    @Test
    void nthuMathAddsTwoToVolunteerVictoryCount() {
        GameState game = game("nthu", GamePhase.DAY_START,
                player(1, Role.FATCAT),
                player(2, Role.METHANE),
                player(3, Role.NTHU_MATH));

        assertThat(gameHelper.countAliveVolunteerFaction(game)).isEqualTo(3);
    }

    @Test
    void phServiceStolenSingleDogDelaysDeath() {
        GameState game = game("single-dog", GamePhase.DAY_START,
                player(1, Role.FATCAT),
                player(2, Role.PH_SERVICE));
        game.setPhServiceStolenRole(Role.SINGLE_DOG);

        gameHelper.setDead(game, 2L);

        assertThat(gameHelper.getPlayer(game, 2L).isAlive()).isTrue();
        assertThat(game.getDelayedDeathRounds()).containsEntry(2L, 2);
    }

    @Test
    void highRabbitIllusionNeverGrantsAbilityAccess() {
        GameState game = game("high-rabbit", GamePhase.METHANE_ACTION,
                player(1, Role.HIGH_RABBIT),
                player(2, Role.FATCAT));
        game.getHighRabbitPerceivedRoles().put(1L, Role.METHANE);

        assertThat(gameHelper.effectiveRole(game, gameHelper.getPlayer(game, 1L))).isEqualTo(Role.HIGH_RABBIT);
        assertThat(gameHelper.canActAs(game, gameHelper.getPlayer(game, 1L), Role.METHANE)).isFalse();
        assertThat(gameHelper.isHighRabbitIllusionOf(game, gameHelper.getPlayer(game, 1L), Role.METHANE)).isTrue();
        assertThat(gameHelper.hasActorForRole(game, Role.METHANE)).isFalse();
    }

    @Test
    void pinkRabbitDiesInsteadOfFatcatWhenSaltedFishStabs() {
        GameState game = game("pink", GamePhase.VOTING,
                player(1, Role.SALTED_FISH),
                player(2, Role.FATCAT),
                player(3, Role.PINK_RABBIT),
                player(4, Role.METHANE),
                player(5, Role.GUOGUO),
                player(6, Role.XIANGXIANG));
        gameStore.saveGame(game);

        dayService.saltedFishStab(game.getRoomId(), 1L, 2L);

        assertThat(gameHelper.getPlayer(game, 2L).isAlive()).isTrue();
        assertThat(gameHelper.getPlayer(game, 3L).isAlive()).isFalse();
        assertThat(game.getFatcatKillBlockedPlayerIds()).contains(2L);
    }

    @Test
    void deadPlayerCanUseFinalVote() {
        GameState game = game("final-vote", GamePhase.NOMINATION,
                player(1, Role.FATCAT),
                player(2, Role.METHANE),
                player(3, Role.GUOGUO));
        gameStore.saveGame(game);
        gameHelper.setDead(game, 2L);

        dayService.playerVote(game.getRoomId(), 2L, 1L);

        assertThat(gameHelper.getPlayer(game, 2L).getVotedTargetId()).isEqualTo(1L);
    }

    @Test
    void nangongIsOnceOnlyAndDrunkPersistsThroughNextNight() {
        GameState game = game("nangong", GamePhase.NANGONG_ACTION,
                player(1, Role.NANGONG),
                player(2, Role.MEATBUN));
        gameStore.saveGame(game);

        nightService.nangongAction(game.getRoomId(), 1L, 2L);

        assertThat(game.getNangongUsedPlayerIds()).contains(1L);
        assertThat(game.getDrunkUntilRounds()).containsEntry(1L, 2).containsEntry(2L, 2);

        game.setCurrentRound(2);
        game.setCurrentPhase(GamePhase.NANGONG_ACTION);
        assertThatThrownBy(() -> nightService.nangongAction(game.getRoomId(), 1L, 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already used");
    }

    private GameState game(String roomId, GamePhase phase, PlayerState... players) {
        GameState game = new GameState();
        game.setRoomId(roomId);
        game.setStatus(RoomStatus.PLAYING);
        game.setCurrentPhase(phase);
        game.setCurrentRound(1);
        game.setPlayers(new ArrayList<>(List.of(players)));
        return game;
    }

    private PlayerState player(long id, Role role) {
        PlayerState player = new PlayerState();
        player.setUserId(id);
        player.setUsername("Player " + id);
        player.setSeatNumber((int) id);
        player.setRole(role);
        player.setAlive(true);
        return player;
    }
}
