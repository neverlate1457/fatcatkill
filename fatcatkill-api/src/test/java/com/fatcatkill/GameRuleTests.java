package com.fatcatkill;

import com.fatcatkill.enums.GamePhase;
import com.fatcatkill.enums.Role;
import com.fatcatkill.enums.RoomStatus;
import com.fatcatkill.model.GameState;
import com.fatcatkill.model.MessagePayload;
import com.fatcatkill.model.PlayerState;
import com.fatcatkill.service.DayService;
import com.fatcatkill.service.GameHelperService;
import com.fatcatkill.service.LocalizedGameException;
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
                .isInstanceOf(LocalizedGameException.class)
                .extracting(error -> ((LocalizedGameException) error).getMessagePayload().getKey())
                .isEqualTo("backend.night.nangongAlreadyUsed");
    }

    @Test
    void emperorRevealRecordsRatManChecker() {
        GameState game = game("emperor-rat", GamePhase.NIGHT_START,
                player(1, Role.EMPEROR),
                player(2, Role.RAT_MAN),
                player(3, Role.FATCAT));
        gameStore.saveGame(game);

        String hint = nightService.emperorRevealAction(game.getRoomId(), 1L);

        assertThat(hint).contains("Emperor reveal");
        assertThat(game.getRatManCheckerIds()).contains(1L);
    }

    @Test
    void nominationWinnerNeedsExecutionMajorityToDie() {
        GameState game = game("day-execution-fails", GamePhase.NOMINATION,
                player(1, Role.FATCAT),
                player(2, Role.METHANE),
                player(3, Role.GUOGUO),
                player(4, Role.FORVKUSA),
                player(5, Role.HATONG));
        gameStore.saveGame(game);

        voteAndConfirm(game, 1L, 2L);
        voteAndConfirm(game, 2L, 2L);
        skipVote(game, 3L);
        skipVote(game, 4L);
        skipVote(game, 5L);

        assertThat(game.getCurrentPhase()).isEqualTo(GamePhase.VOTING);
        assertThat(game.getNominatedPlayerId()).isEqualTo(2L);
        assertThat(lastVoteResultKey(game)).isEqualTo("backend.day.nominationWinner");
        assertThat(game.getLastVoteCounts()).containsEntry(2L, 2);

        voteAndConfirm(game, 1L, 2L);
        skipVote(game, 2L);
        skipVote(game, 3L);
        skipVote(game, 4L);
        skipVote(game, 5L);

        assertThat(gameHelper.getPlayer(game, 2L).isAlive()).isTrue();
        assertThat(game.getLastExiledPlayerId()).isNull();
        assertThat(lastVoteResultKey(game)).isEqualTo("backend.day.executionFailed");
        assertThat(game.getLastVoteCounts()).containsEntry(2L, 1);
    }

    @Test
    void executionVoteKillsOnlyWhenMajorityConfirmsNominee() {
        GameState game = game("day-execution-passes", GamePhase.NOMINATION,
                player(1, Role.FATCAT),
                player(2, Role.METHANE),
                player(3, Role.GUOGUO),
                player(4, Role.FORVKUSA),
                player(5, Role.HATONG));
        gameStore.saveGame(game);

        voteAndConfirm(game, 1L, 2L);
        voteAndConfirm(game, 2L, 2L);
        skipVote(game, 3L);
        skipVote(game, 4L);
        skipVote(game, 5L);

        voteAndConfirm(game, 1L, 2L);
        skipVote(game, 2L);
        voteAndConfirm(game, 3L, 2L);
        voteAndConfirm(game, 4L, 2L);
        skipVote(game, 5L);

        assertThat(gameHelper.getPlayer(game, 2L).isAlive()).isFalse();
        assertThat(game.getLastExiledPlayerId()).isEqualTo(2L);
        assertThat(lastVoteResultKey(game)).isEqualTo("backend.day.exiled");
        assertThat(game.getLastVoteCounts()).containsEntry(2L, 3);
        assertThat(game.getStatus()).isEqualTo(RoomStatus.PLAYING);
    }

    @Test
    void fatcatKillOnMochiBossTriggersCheckInsteadOfDeath() {
        GameState game = game("mochi-pending", GamePhase.NIGHT_START,
                player(1, Role.FATCAT),
                player(2, Role.MOCHI_BOSS),
                player(3, Role.MEATBUN));
        game.setCurrentRound(2);
        gameStore.saveGame(game);

        nightService.fatcatKill(game.getRoomId(), 1L, 2L);

        assertThat(gameHelper.getPlayer(game, 2L).isAlive()).isTrue();
        assertThat(game.getMochiBossPendingCheckPlayerId()).isEqualTo(2L);
        assertThat(game.getCurrentPhase()).isEqualTo(GamePhase.MOCHI_BOSS_ACTION);
        assertThat(game.getFatcatKilledPlayerIds()).doesNotContain(2L);
    }

    @Test
    void canManDrinkProtectsFatcatKillTarget() {
        GameState game = game("canman-protect", GamePhase.NIGHT_START,
                player(1, Role.FATCAT),
                player(2, Role.CAN_MAN),
                player(3, Role.MEATBUN));
        game.setCurrentRound(2);
        gameStore.saveGame(game);

        nightService.fatcatKill(game.getRoomId(), 1L, 3L);
        game.setCurrentPhase(GamePhase.CAN_MAN_ACTION);
        nightService.canManAction(game.getRoomId(), 2L, 3L);

        assertThat(gameHelper.getPlayer(game, 3L).isAlive()).isTrue();
        assertThat(game.getFatcatKilledPlayerIds()).doesNotContain(3L);
        assertThat(game.getCurrentPhase()).isEqualTo(GamePhase.DAY_START);
    }

    @Test
    void liverDebuffOnFatcatActorCancelsNightKill() {
        GameState game = game("liver-cancels-fatcat", GamePhase.NIGHT_START,
                player(1, Role.FATCAT),
                player(2, Role.LIVER_INDEX),
                player(3, Role.MEATBUN));
        game.setCurrentRound(2);
        gameStore.saveGame(game);

        nightService.fatcatKill(game.getRoomId(), 1L, 3L);
        game.setCurrentPhase(GamePhase.LIVER_INDEX_ACTION);
        nightService.liverHeroAction(game.getRoomId(), 2L, 1L);

        assertThat(gameHelper.getPlayer(game, 3L).isAlive()).isTrue();
        assertThat(game.getFatcatKilledPlayerIds()).doesNotContain(3L);
        assertThat(game.getCurrentPhase()).isEqualTo(GamePhase.DAY_START);
    }

    @Test
    void barkKingNoShowsNightAbilityAndDiesNextNight() {
        GameState game = game("bark-no-show", GamePhase.CAN_MAN_ACTION,
                player(1, Role.FATCAT),
                player(2, Role.CAN_MAN),
                player(3, Role.BARK_KING));
        game.setCurrentRound(2);
        gameStore.saveGame(game);

        nightService.canManAction(game.getRoomId(), 2L, 3L);

        assertThat(gameHelper.getPlayer(game, 3L).isAlive()).isTrue();
        assertThat(game.getBarkKingDoomRounds()).containsEntry(3L, 3);

        game.setCurrentRound(3);
        game.setCurrentPhase(GamePhase.CAN_MAN_ACTION);
        nightService.canManAction(game.getRoomId(), 2L, 1L);

        assertThat(gameHelper.getPlayer(game, 3L).isAlive()).isFalse();
        assertThat(game.getBarkKingDoomRounds()).doesNotContainKey(3L);
    }

    @Test
    void fatcatDeathPromotesMagicMeowAndGameContinues() {
        GameState game = game("magic-meow-promotes", GamePhase.DAY_START,
                player(1, Role.FATCAT),
                player(2, Role.MAGIC_MEOW),
                player(3, Role.METHANE));

        gameHelper.setDead(game, 1L);

        assertThat(gameHelper.getPlayer(game, 1L).isAlive()).isFalse();
        assertThat(gameHelper.getPlayer(game, 2L).getRole()).isEqualTo(Role.FATCAT);
        assertThat(game.getFatcatKillerPlayerId()).isEqualTo(2L);
        assertThat(game.getStatus()).isEqualTo(RoomStatus.PLAYING);
        assertThat(game.getCurrentPhase()).isEqualTo(GamePhase.DAY_START);
    }

    @Test
    void fatcatDeathWithoutMagicMeowEndsGame() {
        GameState game = game("fatcat-no-successor", GamePhase.DAY_START,
                player(1, Role.FATCAT),
                player(2, Role.METHANE),
                player(3, Role.GUOGUO));

        gameHelper.setDead(game, 1L);

        assertThat(game.getStatus()).isEqualTo(RoomStatus.FINISHED);
        assertThat(game.getCurrentPhase()).isEqualTo(GamePhase.GAME_OVER);
    }

    @Test
    void saltedFishRemovingFatcatUsesMagicMeowSuccessor() {
        GameState game = game("salted-fish-magic", GamePhase.VOTING,
                player(1, Role.SALTED_FISH),
                player(2, Role.FATCAT),
                player(3, Role.MAGIC_MEOW),
                player(4, Role.GUOGUO),
                player(5, Role.FORVKUSA),
                player(6, Role.HATONG));
        gameStore.saveGame(game);

        dayService.saltedFishStab(game.getRoomId(), 1L, 2L);

        assertThat(gameHelper.getPlayer(game, 2L).isAlive()).isFalse();
        assertThat(gameHelper.getPlayer(game, 3L).getRole()).isEqualTo(Role.FATCAT);
        assertThat(game.getFatcatKillerPlayerId()).isEqualTo(3L);
        assertThat(game.getStatus()).isEqualTo(RoomStatus.PLAYING);
        assertThat(game.getCurrentPhase()).isNotEqualTo(GamePhase.GAME_OVER);
    }

    private String lastVoteResultKey(GameState game) {
        return ((MessagePayload) game.getLastVoteResult()).getKey();
    }
    private void voteAndConfirm(GameState game, long voterId, long targetId) {
        dayService.playerVote(game.getRoomId(), voterId, targetId);
        dayService.confirmVote(game.getRoomId(), voterId);
    }

    private void skipVote(GameState game, long voterId) {
        dayService.skipVote(game.getRoomId(), voterId);
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
