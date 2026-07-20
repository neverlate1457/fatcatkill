package com.fatcatkill.service;

import com.fatcatkill.enums.Camp;
import com.fatcatkill.enums.GamePhase;
import com.fatcatkill.enums.Role;
import com.fatcatkill.enums.RoomStatus;
import com.fatcatkill.model.GameState;
import com.fatcatkill.model.MessagePayload;
import com.fatcatkill.model.PlayerState;
import com.fatcatkill.store.GameStore;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Comparator;
import java.util.Map;

@Service
public class DayService {

    private final GameStore gameStore;
    private final GameHelperService gameHelper;

    public DayService(GameStore gameStore, GameHelperService gameHelper) {
        this.gameStore = gameStore;
        this.gameHelper = gameHelper;
    }

    private static final GamePhase[] DAY_SEQUENCE = {
            GamePhase.DAY_START,
            GamePhase.NOMINATION,
            GamePhase.VOTING,
            GamePhase.NIGHT_START
    };

    public void moveToNextPhase(GameState gameState) {
        if (gameState.getCurrentPhase() == GamePhase.GAME_OVER) {
            return;
        }

        GamePhase current = gameState.getCurrentPhase();
        int currentIndex = -1;

        for (int i = 0; i < DAY_SEQUENCE.length; i++) {
            if (DAY_SEQUENCE[i] == current) {
                currentIndex = i;
                break;
            }
        }

        for (int i = currentIndex + 1; i < DAY_SEQUENCE.length; i++) {
            GamePhase nextPhase = DAY_SEQUENCE[i];
            if (nextPhase == GamePhase.NIGHT_START) {
                gameState.setCurrentRound(gameState.getCurrentRound() + 1);
                gameState.setCurrentPhase(gameHelper.hasAliveStrActor(gameState)
                        ? GamePhase.STR_ACTION
                        : GamePhase.NIGHT_START);
                return;
            }

            gameState.setCurrentPhase(nextPhase);
            return;
        }
    }


    public void startVotingPhase(String roomId) {
        GameState gameState = gameStore.getGame(roomId);
        if (gameState == null) {
            throw localized("backend.game.notFound", "Game not found.");
        }

        if (gameState.getCurrentPhase() == GamePhase.DAY_START) {
            gameHelper.processDelayedDeaths(gameState);
            checkImmediateWinOnly(gameState);
            if (gameState.getCurrentPhase() == GamePhase.GAME_OVER) {
                gameStore.saveGame(gameState);
                return;
            }
            gameHelper.restoreStrSeatNumbers(gameState);
            clearDayVotingState(gameState);
            if (gameState.getLastDayVoterIds() != null) {
                gameState.getLastDayVoterIds().clear();
            }
            moveToNextPhase(gameState);
            gameStore.saveGame(gameState);
        }
    }

    public void playerVote(String roomId, Long voterId, Long targetId) {
        GameState gameState = gameStore.getGame(roomId);
        if (gameState == null || gameState.getStatus() != RoomStatus.PLAYING) {
            throw localized("backend.game.notActive", "Game is not active.");
        }
        if (gameState.getCurrentPhase() != GamePhase.NOMINATION && gameState.getCurrentPhase() != GamePhase.VOTING) {
            throw localized("backend.day.notVotingPhase", "Not a voting phase.");
        }

        PlayerState voter = getVotingPlayer(gameState, voterId);
        PlayerState target = gameHelper.getAlivePlayer(gameState, targetId);

        if (voter.isVoteConfirmed()) {
            throw localized("backend.day.voteAlreadyConfirmedCancelFirst", "Vote is already confirmed. Cancel before changing it.");
        }

        if (gameState.getCurrentPhase() == GamePhase.VOTING) {
            Long nominatedPlayerId = gameState.getNominatedPlayerId();
            if (nominatedPlayerId == null) {
                throw localized("backend.day.noNomineeForExecution", "No nominated player is available for the execution vote.");
            }
            if (!nominatedPlayerId.equals(targetId)) {
                throw localized("backend.day.executionVoteTargetOnly", "Execution votes can only be cast for the nominated player.");
            }
        }

        voter.setVotedTargetId(voter.getVotedTargetId() != null && voter.getVotedTargetId().equals(targetId) ? null : targetId);
        voter.setVoteConfirmed(false);
        gameStore.saveGame(gameState);
    }

    public void confirmVote(String roomId, Long voterId) {
        GameState gameState = validateVotingGame(roomId);
        PlayerState voter = getVotingPlayer(gameState, voterId);

        if (voter.isVoteConfirmed()) {
            throw localized("backend.day.voteAlreadyConfirmed", "Vote is already confirmed.");
        }
        if (voter.getVotedTargetId() == null) {
            throw localized("backend.day.chooseOrSkipBeforeConfirm", "Choose a player or skip before confirming.");
        }

        PlayerState target = gameHelper.getAlivePlayer(gameState, voter.getVotedTargetId());
        if (gameState.getCurrentPhase() == GamePhase.VOTING
                && !voter.getVotedTargetId().equals(gameState.getNominatedPlayerId())) {
            throw localized("backend.day.executionVoteTargetOnly", "Execution votes can only be cast for the nominated player.");
        }

        if (gameState.getCurrentPhase() == GamePhase.NOMINATION && triggerKbNominationTrap(gameState, voter, target)) {
            if (gameState.getCurrentPhase() != GamePhase.GAME_OVER) {
                tallyVotesIfEveryoneConfirmed(gameState);
            }
            gameStore.saveGame(gameState);
            return;
        }

        voter.setVoteConfirmed(true);
        tallyVotesIfEveryoneConfirmed(gameState);
        gameStore.saveGame(gameState);
    }

    public void cancelVote(String roomId, Long voterId) {
        GameState gameState = validateVotingGame(roomId);
        PlayerState voter = getVotingPlayer(gameState, voterId);
        voter.setVotedTargetId(null);
        voter.setVoteConfirmed(false);
        gameStore.saveGame(gameState);
    }

    public void skipVote(String roomId, Long voterId) {
        GameState gameState = validateVotingGame(roomId);
        PlayerState voter = getVotingPlayer(gameState, voterId);
        if (voter.isVoteConfirmed()) {
            throw localized("backend.day.voteAlreadyConfirmed", "Vote is already confirmed.");
        }
        voter.setVotedTargetId(null);
        voter.setVoteConfirmed(true);
        tallyVotesIfEveryoneConfirmed(gameState);
        gameStore.saveGame(gameState);
    }

    private GameState validateVotingGame(String roomId) {
        GameState gameState = gameStore.getGame(roomId);
        if (gameState == null || gameState.getStatus() != RoomStatus.PLAYING) {
            throw localized("backend.game.notActive", "Game is not active.");
        }
        if (gameState.getCurrentPhase() != GamePhase.NOMINATION && gameState.getCurrentPhase() != GamePhase.VOTING) {
            throw localized("backend.day.notVotingPhase", "Not a voting phase.");
        }
        return gameState;
    }

    private void tallyVotesIfEveryoneConfirmed(GameState gameState) {
        boolean everyoneConfirmed = gameState.getPlayers().stream()
                .filter(player -> player.isAlive() || gameState.getFinalVoteEligiblePlayerIds().contains(player.getUserId()))
                .allMatch(PlayerState::isVoteConfirmed);
        if (everyoneConfirmed) {
            if (gameState.getCurrentPhase() == GamePhase.NOMINATION) {
                tallyNominations(gameState);
            } else if (gameState.getCurrentPhase() == GamePhase.VOTING) {
                tallyExecutionVote(gameState);
            }
        }
    }

    public void tallyVotesAndNextPhase(String roomId) {
        GameState gameState = gameStore.getGame(roomId);
        if (gameState == null || gameState.getStatus() != RoomStatus.PLAYING) {
            throw localized("backend.game.notActive", "Game is not active.");
        }

        if (gameState.getCurrentPhase() == GamePhase.NOMINATION) {
            tallyNominations(gameState);
        } else if (gameState.getCurrentPhase() == GamePhase.VOTING) {
            tallyExecutionVote(gameState);
        } else {
            throw localized("backend.day.notVotingPhase", "Not a voting phase.");
        }

        gameStore.saveGame(gameState);
    }

    public String chenAction(String roomId, Long playerId, Long consentNeighborId) {
        GameState gameState = gameStore.getGame(roomId);
        if (gameState == null || gameState.getStatus() != RoomStatus.PLAYING) {
            throw localized("backend.game.notActive", "Game is not active.");
        }
        if (gameState.getCurrentPhase() != GamePhase.DAY_START) {
            throw localized("backend.chen.onlyDayDiscussion", "Chen can only declare this during the day discussion phase.");
        }

        PlayerState actor = gameHelper.validateAndGetPlayer(gameState, playerId, Role.CHEN);
        PlayerState consentNeighbor = gameHelper.getAlivePlayer(gameState, consentNeighborId);
        if (gameState.getChenUsedPlayerIds() != null && gameState.getChenUsedPlayerIds().contains(playerId)) {
            throw localized("backend.chen.alreadyUsed", "Chen has already used this action.");
        }

        List<PlayerState> aliveBySeat = gameState.getPlayers().stream()
                .filter(PlayerState::isAlive)
                .filter(player -> player.getSeatNumber() != null)
                .sorted(Comparator.comparing(PlayerState::getSeatNumber))
                .toList();
        int actorIndex = aliveBySeat.indexOf(actor);
        if (actorIndex < 0 || aliveBySeat.size() < 3) {
            throw localized("backend.chen.notEnoughSeatedPlayers", "Not enough seated alive players for Chen's action.");
        }

        PlayerState left = aliveBySeat.get((actorIndex - 1 + aliveBySeat.size()) % aliveBySeat.size());
        PlayerState right = aliveBySeat.get((actorIndex + 1) % aliveBySeat.size());

        PlayerState victim;
        if (left.getUserId().equals(consentNeighbor.getUserId())) {
            victim = right;
        } else if (right.getUserId().equals(consentNeighbor.getUserId())) {
            victim = left;
        } else {
            throw localized("backend.chen.consentMustBeNeighbor", "Consent player must be Chen's current left or right neighbor.");
        }

        gameState.setChenPendingKillPlayerId(victim.getUserId());
        gameState.getChenUsedPlayerIds().add(playerId);
        String publicFallback = actor.getUsername() + " publicly activated Chen's ability. Player "
                + victim.getUserId() + " will be kicked during tonight's settlement.";
        gameState.setPublicMessage(MessagePayload.of("backend.chen.publicActivated",
                Map.of("actor", actor.getUsername(), "targetId", victim.getUserId()), publicFallback));
        gameStore.saveGame(gameState);
        return "Chen action: player " + victim.getUserId() + " will be kicked during tonight's settlement.";
    }

    public String skipChenAction(String roomId, Long playerId) {
        GameState gameState = gameStore.getGame(roomId);
        if (gameState == null || gameState.getStatus() != RoomStatus.PLAYING) {
            throw localized("backend.game.notActive", "Game is not active.");
        }
        if (gameState.getCurrentPhase() != GamePhase.DAY_START) {
            throw localized("backend.chen.skipOnlyDayDiscussion", "Chen can only skip during the day discussion phase.");
        }

        gameHelper.validateAndGetPlayer(gameState, playerId, Role.CHEN);
        if (gameState.getChenUsedPlayerIds() != null && gameState.getChenUsedPlayerIds().contains(playerId)) {
            throw localized("backend.chen.alreadyUsed", "Chen has already used this action.");
        }

        if (gameState.getChenSkippedRounds() != null) {
            gameState.getChenSkippedRounds().put(playerId, gameState.getCurrentRound());
        }
        gameStore.saveGame(gameState);
        return "Chen action skipped for this day.";
    }

    private void tallyNominations(GameState gameState) {
        rememberDayVoters(gameState);
        Map<Long, Integer> voteCounts = collectVoteCounts(gameState);
        gameState.setDayVoteCounts(voteCounts);
        recordLastVoteCounts(gameState, voteCounts);

        Long topPlayerId = null;
        int topVotes = 0;
        boolean isTie = false;

        for (Map.Entry<Long, Integer> entry : voteCounts.entrySet()) {
            if (entry.getValue() > topVotes) {
                topVotes = entry.getValue();
                topPlayerId = entry.getKey();
                isTie = false;
            } else if (entry.getValue() == topVotes) {
                isTie = true;
            }
        }

        clearVotes(gameState);

        if (topPlayerId == null || isTie) {
            gameState.setNominatedPlayerId(null);
            gameState.setLastExiledPlayerId(null);
            gameState.setLastVoteResult(isTie
                    ? MessagePayload.of("backend.day.nominationTie", "Nomination tied. No player entered the execution vote.")
                    : MessagePayload.of("backend.day.noNominations", "No nominations were cast."));
            finishDayWithoutExecution(gameState);
            return;
        }

        gameHelper.getAlivePlayer(gameState, topPlayerId);
        gameState.setNominatedPlayerId(topPlayerId);
        gameState.setLastVoteResult(MessagePayload.of("backend.day.nominationWinner",
                Map.of("playerId", topPlayerId, "votes", topVotes),
                "Player " + topPlayerId + " won the nomination with " + topVotes + " votes."));
        gameState.setCurrentPhase(GamePhase.VOTING);
    }

    private void tallyExecutionVote(GameState gameState) {
        Long nominatedPlayerId = gameState.getNominatedPlayerId();
        if (nominatedPlayerId == null) {
            throw localized("backend.day.noNomineeForExecution", "No nominated player is available for the execution vote.");
        }

        rememberDayVoters(gameState);
        Map<Long, Integer> voteCounts = collectVoteCounts(gameState);
        gameState.setDayVoteCounts(voteCounts);
        recordLastVoteCounts(gameState, voteCounts);

        int yesVotes = voteCounts.getOrDefault(nominatedPlayerId, 0);
        int aliveCount = gameHelper.countAlivePlayers(gameState);
        int majority = aliveCount / 2 + 1;

        if (yesVotes >= majority) {
            PlayerState executedPlayer = gameHelper.getAlivePlayer(gameState, nominatedPlayerId);
            gameHelper.setDead(gameState, nominatedPlayerId);
            gameState.setLastExiledPlayerId(nominatedPlayerId);

            if (executedPlayer.getRole() == Role.NUKO && !executedPlayer.isAlive()) {
                gameState.setLastVoteResult(MessagePayload.of("backend.day.exiled",
                        Map.of("playerId", nominatedPlayerId, "votes", yesVotes, "majority", majority),
                        "Player " + nominatedPlayerId + " was exiled with " + yesVotes + "/" + majority + " required yes votes."));
                gameHelper.finishGame(gameState, Camp.WOLF);
                clearDayVotingState(gameState);
                gameState.getFinalVoteEligiblePlayerIds().clear();
                return;
            }

            clearDayVotingState(gameState);
            gameState.getFinalVoteEligiblePlayerIds().clear();
            gameState.setLastVoteResult(MessagePayload.of("backend.day.exiled",
                    Map.of("playerId", nominatedPlayerId, "votes", yesVotes, "majority", majority),
                    "Player " + nominatedPlayerId + " was exiled with " + yesVotes + "/" + majority + " required yes votes."));
            checkWinCondition(gameState);
            return;
        }

        gameState.setLastExiledPlayerId(null);
        clearDayVotingState(gameState);
        gameState.setLastVoteResult(MessagePayload.of("backend.day.executionFailed",
                Map.of("playerId", nominatedPlayerId, "votes", yesVotes, "majority", majority),
                "Execution failed: player " + nominatedPlayerId + " received " + yesVotes + "/" + majority + " required yes votes."));
        finishDayWithoutExecution(gameState);
    }

    private void recordLastVoteCounts(GameState gameState, Map<Long, Integer> voteCounts) {
        if (gameState.getLastVoteCounts() == null) {
            gameState.setLastVoteCounts(new HashMap<>());
        }
        gameState.getLastVoteCounts().clear();
        gameState.getLastVoteCounts().putAll(voteCounts);
    }

    private Map<Long, Integer> collectVoteCounts(GameState gameState) {
        return gameHelper.collectConfirmedVoteCounts(gameState);
    }

    private void rememberDayVoters(GameState gameState) {
        if (gameState.getLastDayVoterIds() == null) return;
        for (PlayerState player : gameState.getPlayers()) {
            if ((player.isAlive() || gameState.getFinalVoteEligiblePlayerIds().contains(player.getUserId()))
                    && player.isVoteConfirmed() && player.getVotedTargetId() != null) {
                gameState.getLastDayVoterIds().add(player.getUserId());
            }
        }
    }

    private boolean triggerKbNominationTrap(GameState gameState, PlayerState nominator, PlayerState target) {
        if (!gameHelper.canActAs(gameState, target, Role.KB)) return false;
        if (gameState.getKbNominationTrapTriggeredIds() == null) return false;
        if (gameState.getKbNominationTrapTriggeredIds().contains(target.getUserId())) return false;

        gameState.getKbNominationTrapTriggeredIds().add(target.getUserId());
        if (gameHelper.isVolunteerArmy(nominator.getRole()) || nominator.getRole() == Role.MEATBUN) {
            gameHelper.setDead(gameState, nominator.getUserId());
            nominator.setVotedTargetId(null);
            nominator.setVoteConfirmed(true);
            checkImmediateWinOnly(gameState);
            return true;
        }
        return false;
    }

    private void finishDayWithoutExecution(GameState gameState) {
        if (gameState.getCurrentPhase() == GamePhase.NOMINATION) {
            gameState.setCurrentPhase(GamePhase.VOTING);
        }
        moveToNextPhase(gameState);
        gameState.getFinalVoteEligiblePlayerIds().clear();
    }

    private void clearDayVotingState(GameState gameState) {
        clearVotes(gameState);
        gameState.setNominatedPlayerId(null);
        if (gameState.getDayVoteCounts() != null) {
            gameState.getDayVoteCounts().clear();
        }
    }

    private void clearVotes(GameState gameState) {
        for (PlayerState player : gameState.getPlayers()) {
            player.setVotedTargetId(null);
            player.setVoteConfirmed(false);
        }
    }

    private void checkWinCondition(GameState gameState) {
        long aliveFatcats = gameState.getPlayers().stream()
                .filter(p -> p.isAlive() && p.getRole() == Role.FATCAT)
                .count();

        long aliveVolunteerFaction = gameHelper.countAliveVolunteerFaction(gameState);

        if (aliveFatcats == 0) {
            gameHelper.finishGame(gameState, Camp.VILLAGER);
        } else if (aliveVolunteerFaction < 3) {
            gameHelper.finishGame(gameState, Camp.WOLF);
        } else {
            moveToNextPhase(gameState);
        }
    }

    private void checkImmediateWinOnly(GameState gameState) {
        long aliveFatcats = gameState.getPlayers().stream()
                .filter(p -> p.isAlive() && p.getRole() == Role.FATCAT)
                .count();

        long aliveVolunteerFaction = gameHelper.countAliveVolunteerFaction(gameState);

        if (aliveFatcats == 0) {
            gameHelper.finishGame(gameState, Camp.VILLAGER);
        } else if (aliveVolunteerFaction < 3) {
            gameHelper.finishGame(gameState, Camp.WOLF);
        }
    }

    private PlayerState getVotingPlayer(GameState game, Long playerId) {
        return gameHelper.validateVotingPlayer(game, playerId);
    }

    public String saltedFishStab(String roomId, Long playerId, Long targetId) {
        GameState gameState = gameStore.getGame(roomId);
        if (gameState == null || gameState.getStatus() != RoomStatus.PLAYING) {
            throw localized("backend.game.notActive", "Game is not active.");
        }
        if (gameState.getCurrentPhase() != GamePhase.VOTING) {
            throw localized("backend.saltedFish.stabOnlyExecutionVoting", "Salted Fish can only stab during the execution voting phase.");
        }

        PlayerState actor = gameHelper.getPlayer(gameState, playerId);
        if (!actor.isAlive() && !gameState.getFatcatKilledPlayerIds().contains(playerId)) {
            throw localized("backend.saltedFish.onlyAfterFatcatRemoved", "Salted Fish can only act after being removed by Fatcat.");
        }
        if (!gameHelper.canActAs(gameState, actor, Role.SALTED_FISH)) {
            throw localized("backend.saltedFish.onlyRoleCanUse", "Only Salted Fish can use this action.");
        }
        if (gameState.getSaltedFishUsedPlayerIds() != null && gameState.getSaltedFishUsedPlayerIds().contains(playerId)) {
            throw localized("backend.saltedFish.alreadyUsed", "Salted Fish has already used this action.");
        }
        if (playerId.equals(targetId)) {
            throw localized("backend.saltedFish.cannotStabSelf", "Salted Fish cannot stab himself.");
        }

        PlayerState target = gameHelper.getAlivePlayer(gameState, targetId);
        if (gameState.getSaltedFishUsedPlayerIds() != null) {
            gameState.getSaltedFishUsedPlayerIds().add(playerId);
        }
        String publicMessage = actor.getUsername() + " used Salted Fish Stab on " + target.getUsername() + ".";
        String publicMessageKey;
        boolean deathOccurred = false;

        if (gameHelper.isDrunk(gameState, playerId)) {
            publicMessage += " The action had no effect.";
            gameState.setPublicMessage(MessagePayload.of("backend.saltedFish.drunkNoEffect",
                    Map.of("actor", actor.getUsername(), "target", target.getUsername()), publicMessage));
            gameStore.saveGame(gameState);
            return publicMessage;
        }

        boolean isRabbitAlive = gameState.getPlayers().stream()
                .anyMatch(p -> p.getRole() == Role.PINK_RABBIT && p.isAlive());

        if (target.getRole() == Role.FATCAT) {
            if (isRabbitAlive) {
                PlayerState pinkRabbit = gameState.getPlayers().stream()
                        .filter(p -> p.getRole() == Role.PINK_RABBIT && p.isAlive())
                        .findFirst()
                        .orElse(null);
                if (pinkRabbit != null) {
                    gameHelper.setDead(gameState, pinkRabbit.getUserId());
                    deathOccurred = true;
                }
                if (gameState.getFatcatKillBlockedPlayerIds() != null) {
                    gameState.getFatcatKillBlockedPlayerIds().add(targetId);
                }
                publicMessage += " Pink Rabbit protected Fatcat and was removed. Fatcat will lose the next night kill.";
                publicMessageKey = "backend.saltedFish.pinkRabbit";
            } else {
                gameHelper.setDead(gameState, targetId);
                deathOccurred = true;
                publicMessage += " The target was Fatcat and was removed.";
                publicMessageKey = "backend.saltedFish.fatcatRemoved";
            }
        } else {
            publicMessage += " The target was not Fatcat. Nothing happened.";
            publicMessageKey = "backend.saltedFish.notFatcat";
        }

        gameState.setPublicMessage(MessagePayload.of(publicMessageKey,
                Map.of("actor", actor.getUsername(), "target", target.getUsername()), publicMessage));
        if (deathOccurred && gameState.getCurrentPhase() != GamePhase.GAME_OVER) {
            checkWinCondition(gameState);
        }
        gameStore.saveGame(gameState);
        return publicMessage;
    }

    public String skipSaltedFishStab(String roomId, Long playerId) {
        GameState gameState = gameStore.getGame(roomId);
        if (gameState == null || gameState.getStatus() != RoomStatus.PLAYING) {
            throw localized("backend.game.notActive", "Game is not active.");
        }
        if (gameState.getCurrentPhase() != GamePhase.VOTING) {
            throw localized("backend.saltedFish.skipOnlyExecutionVoting", "Salted Fish can only skip during the execution voting phase.");
        }

        PlayerState actor = gameHelper.getPlayer(gameState, playerId);
        if (!actor.isAlive() && !gameState.getFatcatKilledPlayerIds().contains(playerId)) {
            throw localized("backend.saltedFish.onlyAfterFatcatRemoved", "Salted Fish can only act after being removed by Fatcat.");
        }
        if (!gameHelper.canActAs(gameState, actor, Role.SALTED_FISH)) {
            throw localized("backend.saltedFish.onlyRoleCanSkip", "Only Salted Fish can skip this action.");
        }
        if (gameState.getSaltedFishUsedPlayerIds() != null && gameState.getSaltedFishUsedPlayerIds().contains(playerId)) {
            throw localized("backend.saltedFish.alreadyUsed", "Salted Fish has already used this action.");
        }

        if (gameState.getSaltedFishSkippedRounds() != null) {
            gameState.getSaltedFishSkippedRounds().put(playerId, gameState.getCurrentRound());
        }
        gameStore.saveGame(gameState);
        return "Salted Fish stab skipped for this vote.";
    }


    private LocalizedGameException localized(String key, String fallback) {
        return new LocalizedGameException(MessagePayload.of(key, fallback));
    }
}
