package com.fatcatkill;

import com.fatcatkill.model.GameState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameStateModelTests {

    @Test
    void collectionAccessorsRecoverFromNullValues() {
        GameState game = new GameState();
        game.setPlayers(null);
        game.setNightActions(null);
        game.setFatcatAbsentVolunteerHintRoles(null);
        game.setHighRabbitPerceivedRoles(null);
        game.setDayVoteCounts(null);
        game.setLastVoteCounts(null);
        game.setDelayedDeathRounds(null);
        game.setMubaimuDoomRounds(null);
        game.setBarkKingDoomRounds(null);
        game.setDrunkUntilRounds(null);
        game.setPrivateMessages(null);
        game.setStrOriginalSeatNumbers(null);
        game.setStrTemporarySeatNumbers(null);
        game.setStrSwappedSeatNumbers(null);
        game.setLastDayVoterIds(null);
        game.setKbNominationTrapTriggeredIds(null);
        game.setChenUsedPlayerIds(null);
        game.setChenSkippedRounds(null);
        game.setRatManCheckerIds(null);
        game.setSaltedFishUsedPlayerIds(null);
        game.setSaltedFishSkippedRounds(null);
        game.setFatcatKillBlockedPlayerIds(null);
        game.setNangongUsedPlayerIds(null);
        game.setFinalVoteEligiblePlayerIds(null);
        game.setFatcatKilledPlayerIds(null);
        game.setLogs(null);

        assertThat(game.getPlayers()).isEmpty();
        assertThat(game.getNightActions()).isEmpty();
        assertThat(game.getFatcatAbsentVolunteerHintRoles()).isEmpty();
        assertThat(game.getHighRabbitPerceivedRoles()).isEmpty();
        assertThat(game.getDayVoteCounts()).isEmpty();
        assertThat(game.getLastVoteCounts()).isEmpty();
        assertThat(game.getDelayedDeathRounds()).isEmpty();
        assertThat(game.getMubaimuDoomRounds()).isEmpty();
        assertThat(game.getBarkKingDoomRounds()).isEmpty();
        assertThat(game.getDrunkUntilRounds()).isEmpty();
        assertThat(game.getPrivateMessages()).isEmpty();
        assertThat(game.getStrOriginalSeatNumbers()).isEmpty();
        assertThat(game.getStrTemporarySeatNumbers()).isEmpty();
        assertThat(game.getStrSwappedSeatNumbers()).isEmpty();
        assertThat(game.getLastDayVoterIds()).isEmpty();
        assertThat(game.getKbNominationTrapTriggeredIds()).isEmpty();
        assertThat(game.getChenUsedPlayerIds()).isEmpty();
        assertThat(game.getChenSkippedRounds()).isEmpty();
        assertThat(game.getRatManCheckerIds()).isEmpty();
        assertThat(game.getSaltedFishUsedPlayerIds()).isEmpty();
        assertThat(game.getSaltedFishSkippedRounds()).isEmpty();
        assertThat(game.getFatcatKillBlockedPlayerIds()).isEmpty();
        assertThat(game.getNangongUsedPlayerIds()).isEmpty();
        assertThat(game.getFinalVoteEligiblePlayerIds()).isEmpty();
        assertThat(game.getFatcatKilledPlayerIds()).isEmpty();
        assertThat(game.getLogs()).isEmpty();
    }
}