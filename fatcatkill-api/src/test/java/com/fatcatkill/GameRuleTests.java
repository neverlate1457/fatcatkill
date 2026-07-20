package com.fatcatkill;

import com.fatcatkill.dto.GameActionRequest;
import com.fatcatkill.controller.ActionController;
import com.fatcatkill.controller.TallyVotesController;
import com.fatcatkill.controller.PlayerVoteController;
import com.fatcatkill.enums.Camp;
import com.fatcatkill.enums.GamePhase;
import com.fatcatkill.enums.Role;
import com.fatcatkill.enums.RoomStatus;
import com.fatcatkill.model.GameState;
import com.fatcatkill.model.MessagePayload;
import com.fatcatkill.model.PlayerState;
import com.fatcatkill.service.BotService;
import com.fatcatkill.service.GameActionDispatcher;
import com.fatcatkill.service.DayService;
import com.fatcatkill.service.GameHelperService;
import com.fatcatkill.service.GameLogService;
import com.fatcatkill.service.LocalizedGameException;
import com.fatcatkill.service.NightService;
import com.fatcatkill.service.SystemOutService;
import com.fatcatkill.store.GameStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameRuleTests {

    private GameStore gameStore;
    private GameHelperService gameHelper;
    private DayService dayService;
    private NightService nightService;
    private BotService botService;
    private GameLogService gameLogService;
    private TallyVotesController tallyVotesController;
    private PlayerVoteController playerVoteController;
    private ActionController actionController;

    @BeforeEach
    void setUp() {
        gameStore = new GameStore();
        gameHelper = new GameHelperService(gameStore);
        dayService = new DayService(gameStore, gameHelper);
        nightService = new NightService(gameStore, gameHelper);
        SystemOutService systemOutService = new SystemOutService();
        gameLogService = new GameLogService(gameStore, systemOutService);
        botService = new BotService(gameStore, nightService, dayService, gameHelper, gameLogService, systemOutService);
        tallyVotesController = new TallyVotesController(dayService, gameStore, gameLogService, botService);
        playerVoteController = new PlayerVoteController(dayService, gameStore, gameLogService, botService);
        actionController = new ActionController(gameStore, botService, systemOutService, gameHelper, new GameActionDispatcher(nightService, dayService));
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
                player(3, Role.GUOGUO),
                player(4, Role.HATONG));
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
    void kbTrapMarksDeadNominatorVoteComplete() {
        GameState game = game("kb-trap-confirm", GamePhase.NOMINATION,
                player(1, Role.FATCAT),
                player(2, Role.METHANE),
                player(3, Role.KB),
                player(4, Role.GUOGUO),
                player(5, Role.FORVKUSA),
                player(6, Role.HATONG));
        gameStore.saveGame(game);

        voteAndConfirm(game, 1L, 2L);
        skipVote(game, 3L);
        voteAndConfirm(game, 4L, 1L);
        voteAndConfirm(game, 5L, 1L);
        skipVote(game, 6L);
        voteAndConfirm(game, 2L, 3L);

        PlayerState nominator = gameHelper.getPlayer(game, 2L);
        assertThat(nominator.isAlive()).isFalse();
        assertThat(nominator.isVoteConfirmed()).isFalse();
        assertThat(nominator.getVotedTargetId()).isNull();
        assertThat(game.getCurrentPhase()).isEqualTo(GamePhase.VOTING);
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
                player(3, Role.GUOGUO),
                player(4, Role.HATONG));

        gameHelper.setDead(game, 1L);

        assertThat(game.getStatus()).isEqualTo(RoomStatus.FINISHED);
        assertThat(game.getCurrentPhase()).isEqualTo(GamePhase.GAME_OVER);
        assertThat(game.getWinnerCamp()).isEqualTo(Camp.VILLAGER);
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

    @Test
    void botAutoFastForwardsWhenOnlyBotsAreAlive() {
        GameState game = game("bot-fast-forward", GamePhase.DAY_START,
                botPlayer(1, Role.FATCAT),
                botPlayer(2, Role.METHANE),
                botPlayer(3, Role.GUOGUO),
                botPlayer(4, Role.HATONG));
        gameStore.saveGame(game);

        int steps = botService.autoPlay(game.getRoomId(), 99L);

        assertThat(steps).isEqualTo(1);
        assertThat(game.getStatus()).isEqualTo(RoomStatus.FINISHED);
        assertThat(game.getCurrentPhase()).isEqualTo(GamePhase.GAME_OVER);
        assertThat(game.getWinnerCamp()).isEqualTo(Camp.WOLF);
        assertThat(game.getLogs()).anySatisfy(log -> assertThat(log.getActionType()).isEqualTo("BOT_ONLY_FAST_FORWARD"));
    }

    @Test
    void voteControllerFastForwardsWhenLastHumanDies() {
        GameState game = game("vote-bot-only-after-exile", GamePhase.VOTING,
                botPlayer(1, Role.FATCAT),
                player(2, Role.METHANE),
                botPlayer(3, Role.GUOGUO),
                botPlayer(4, Role.FORVKUSA),
                botPlayer(5, Role.HATONG));
        game.setNominatedPlayerId(2L);
        gameStore.saveGame(game);

        GameActionRequest vote = new GameActionRequest();
        vote.setRoomId(game.getRoomId());
        vote.setTargetId(2L);
        vote.setPlayerId(2L);
        playerVoteController.skip(vote);
        vote.setPlayerId(5L);
        playerVoteController.skip(vote);
        for (long voterId : List.of(1L, 3L)) {
            vote.setPlayerId(voterId);
            playerVoteController.execute(vote);
            playerVoteController.confirm(vote);
        }
        vote.setPlayerId(4L);
        playerVoteController.execute(vote);
        ResponseEntity<?> response = playerVoteController.confirm(vote);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        GameState responseGame = (GameState) response.getBody();
        assertThat(responseGame.getStatus()).isEqualTo(RoomStatus.FINISHED);
        assertThat(responseGame.getCurrentPhase()).isEqualTo(GamePhase.GAME_OVER);
        assertThat(responseGame.getLogs()).anySatisfy(log -> assertThat(log.getActionType()).isEqualTo("BOT_ONLY_FAST_FORWARD"));
        assertThat(gameStore.getGame(game.getRoomId())).isNull();
    }
    @Test
    void humanNamedBotDoesNotTriggerBotOnlyFastForward() {
        PlayerState humanNamedBot = player(1, Role.FATCAT);
        humanNamedBot.setUsername("Bot 1");
        GameState game = game("human-named-bot", GamePhase.DAY_START,
                humanNamedBot,
                botPlayer(2, Role.METHANE),
                botPlayer(3, Role.GUOGUO),
                botPlayer(4, Role.HATONG));
        gameStore.saveGame(game);

        int steps = botService.autoPlay(game.getRoomId(), 99L);

        assertThat(steps).isEqualTo(1);
        assertThat(game.getStatus()).isEqualTo(RoomStatus.PLAYING);
    }
    @Test
    void autoPlayBotControllerReturnsFinalStateWhenOnlyBotsRemain() {
        GameState game = game("controller-bot-fast-forward", GamePhase.DAY_START,
                botPlayer(1, Role.FATCAT),
                botPlayer(2, Role.METHANE),
                botPlayer(3, Role.GUOGUO));
        gameStore.saveGame(game);

        ResponseEntity<?> response = actionController.autoPlayBot(Map.of(
                "roomId", game.getRoomId(),
                "playerId", 99L
        ));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(((GameState) response.getBody()).getCurrentPhase()).isEqualTo(GamePhase.GAME_OVER);
        assertThat(gameStore.getGame(game.getRoomId())).isNull();
    }
    @Test
    void actionControllerLogsAbilityThatEndsGame() {
        PlayerState saltedFish = player(1, Role.SALTED_FISH);
        saltedFish.setAlive(false);
        GameState game = game("action-controller-finishes-game", GamePhase.VOTING,
                saltedFish,
                player(2, Role.FATCAT),
                player(3, Role.METHANE),
                player(4, Role.GUOGUO),
                player(5, Role.HATONG));
        game.getFatcatKilledPlayerIds().add(1L);
        gameStore.saveGame(game);

        ResponseEntity<?> response = actionController.executeAction(Map.of(
                "roomId", game.getRoomId(),
                "playerId", 1L,
                "actionType", "SALTED_FISH_STAB",
                "targetId", 2L
        ));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(game.getStatus()).isEqualTo(RoomStatus.FINISHED);
        assertThat(gameStore.getGame(game.getRoomId())).isNull();
        assertThat(game.getLogs().get(game.getLogs().size() - 1).getActionType())
                .isEqualTo("SALTED_FISH_STAB");
        assertThat(game.getLogs().get(game.getLogs().size() - 1).getMessageKey())
                .isEqualTo("backend.saltedFish.fatcatRemoved");
    }
    @Test
    void voteConfirmControllerDoesNotFailWhenVoteEndsGame() {
        GameState game = game("controller-confirm-finishes-game", GamePhase.VOTING,
                player(1, Role.FATCAT),
                player(2, Role.METHANE),
                player(3, Role.GUOGUO),
                player(4, Role.HATONG));
        game.setNominatedPlayerId(1L);
        gameStore.saveGame(game);

        voteAndConfirm(game, 1L, 1L);
        voteAndConfirm(game, 2L, 1L);
        voteAndConfirm(game, 3L, 1L);
        dayService.playerVote(game.getRoomId(), 4L, 1L);

        GameActionRequest finalConfirm = new GameActionRequest();
        finalConfirm.setRoomId(game.getRoomId());
        finalConfirm.setPlayerId(4L);
        finalConfirm.setTargetId(1L);

        ResponseEntity<?> response = playerVoteController.confirm(finalConfirm);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(game.getStatus()).isEqualTo(RoomStatus.FINISHED);
        assertThat(gameStore.getGame(game.getRoomId())).isNull();
        assertThat(game.getLogs().get(game.getLogs().size() - 1).getMessageKey())
                .isEqualTo("backend.day.voteConfirmedLog");
    }
    @Test
    void dayControllersWriteLocalizedLogPayloads() {
        GameState game = game("day-controller-log-keys", GamePhase.DAY_START,
                player(1, Role.FATCAT),
                player(2, Role.METHANE),
                player(3, Role.GUOGUO),
                player(4, Role.HATONG));
        gameStore.saveGame(game);

        ResponseEntity<?> nominationResponse = tallyVotesController.startNomination(game.getRoomId());

        assertThat(nominationResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(game.getLogs().get(game.getLogs().size() - 1).getMessageKey())
                .isEqualTo("backend.day.nominationStarted");

        GameState votingGame = game("day-controller-vote-log-keys", GamePhase.NOMINATION,
                player(1, Role.FATCAT),
                player(2, Role.METHANE),
                player(3, Role.GUOGUO),
                player(4, Role.HATONG));
        gameStore.saveGame(votingGame);

        GameActionRequest vote = new GameActionRequest();
        vote.setRoomId(votingGame.getRoomId());
        vote.setPlayerId(1L);
        vote.setTargetId(2L);
        playerVoteController.execute(vote);
        playerVoteController.confirm(vote);

        assertThat(votingGame.getLogs().get(votingGame.getLogs().size() - 1).getMessageKey())
                .isEqualTo("backend.day.voteConfirmedLog");
        assertThat(votingGame.getLogs().get(votingGame.getLogs().size() - 1).getMessageParams())
                .containsEntry("phase", GamePhase.NOMINATION.name());
    }

    private String messageKey(ResponseEntity<?> response) {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        return ((MessagePayload) body.get("message")).getKey();
    }

    private String lastVoteResultKey(GameState game) {
        return ((MessagePayload) game.getLastVoteResult()).getKey();
    }

    @Test
    void actionControllerMissingPayloadReturnsMessagePayload() {
        ResponseEntity<?> actionResponse = actionController.executeAction(null);
        ResponseEntity<?> botResponse = actionController.autoPlayBot(null);

        assertThat(actionResponse.getStatusCode().is4xxClientError()).isTrue();
        assertThat(botResponse.getStatusCode().is4xxClientError()).isTrue();
        assertThat(messageKey(actionResponse)).isEqualTo("backend.action.missingPayload");
        assertThat(messageKey(botResponse)).isEqualTo("backend.action.missingPayload");
    }

    @Test
    void voteControllerMissingPayloadReturnsMessagePayload() {
        ResponseEntity<?> response = playerVoteController.execute(null);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        MessagePayload message = (MessagePayload) body.get("message");
        assertThat(message.getKey()).isEqualTo("backend.action.missingPayload");
    }
    @Test
    void voteControllerValidatesRequiredFieldsBeforeService() {
        GameActionRequest missingTarget = new GameActionRequest();
        missingTarget.setRoomId("vote-missing-target");
        missingTarget.setPlayerId(1L);

        ResponseEntity<?> response = playerVoteController.execute(missingTarget);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        MessagePayload message = (MessagePayload) body.get("message");
        assertThat(message.getKey()).isEqualTo("backend.action.missingField");
        assertThat(message.getParams()).containsEntry("field", "targetId");
    }

    @Test
    void voteConfirmDoesNotRequireTargetIdPayload() {
        GameState game = game("vote-confirm-no-target", GamePhase.NOMINATION,
                player(1L, Role.FATCAT), player(2L, Role.METHANE));
        game.getPlayers().get(0).setVotedTargetId(2L);
        gameStore.saveGame(game);
        GameActionRequest request = new GameActionRequest();
        request.setRoomId(game.getRoomId());
        request.setPlayerId(1L);

        ResponseEntity<?> response = playerVoteController.confirm(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }
    @Test
    void botAutoInvalidPlayerIdReturnsFieldSpecificMessagePayload() {
        ResponseEntity<?> response = actionController.autoPlayBot(Map.of(
                "roomId", "bot-auto-invalid-payload",
                "playerId", "not-a-number"
        ));

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        MessagePayload message = (MessagePayload) body.get("message");
        assertThat(message.getKey()).isEqualTo("backend.action.expectedNumericId");
        assertThat(message.getParams()).containsEntry("field", "playerId");
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

    private PlayerState botPlayer(long id, Role role) {
        PlayerState player = player(id, role);
        player.setUsername("Bot " + id);
        player.setBot(true);
        return player;
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
