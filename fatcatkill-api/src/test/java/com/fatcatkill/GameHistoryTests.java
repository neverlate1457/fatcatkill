package com.fatcatkill;

import com.fatcatkill.enums.Camp;
import com.fatcatkill.enums.GamePhase;
import com.fatcatkill.enums.Role;
import com.fatcatkill.enums.RoomStatus;
import com.fatcatkill.model.GameState;
import com.fatcatkill.model.PlayerState;
import com.fatcatkill.repository.GameRecordRepository;
import com.fatcatkill.store.GameStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GameHistoryTests {

    @Autowired
    private GameStore gameStore;

    @Autowired
    private GameRecordRepository gameRecordRepository;

    @Test
    void finishedGameIsRecordedExactlyOnce() {
        String gameId = UUID.randomUUID().toString();
        GameState game = new GameState();
        game.setGameId(gameId);
        game.setRoomId("history-" + gameId);
        game.setGameMode("OLD_HOME");
        game.setStartedAt("2026-06-19T12:00:00");
        game.setCurrentRound(3);
        game.setCurrentPhase(GamePhase.GAME_OVER);
        game.setStatus(RoomStatus.FINISHED);
        game.setPlayers(List.of(player(1L, Role.FATCAT), player(2L, Role.METHANE)));

        gameStore.saveGame(game);
        gameStore.saveGame(game);

        assertThat(gameRecordRepository.countByGameId(gameId)).isEqualTo(1);
        var record = gameRecordRepository.findByGameId(gameId).orElseThrow();
        assertThat(record.getWinnerCamp()).isEqualTo(Camp.WOLF);
        assertThat(game.getWinnerCamp()).isEqualTo(Camp.WOLF);
        assertThat(record.getGameMode()).isEqualTo("OLD_HOME");
        assertThat(record.getRoundsPlayed()).isEqualTo(3);
        assertThat(record.getPlayerCount()).isEqualTo(2);
        assertThat(record.getFinalStateJson()).contains(gameId);
        assertThat(game.isHistoryRecorded()).isTrue();
    }

    private PlayerState player(Long id, Role role) {
        PlayerState player = new PlayerState();
        player.setUserId(id);
        player.setUsername("Player " + id);
        player.setRole(role);
        player.setAlive(true);
        return player;
    }
}