package com.fatcatkill;

import com.fatcatkill.controller.GameHistoryController;
import com.fatcatkill.controller.GameLogController;
import com.fatcatkill.entity.GameRecord;
import com.fatcatkill.entity.User;
import com.fatcatkill.enums.Camp;
import com.fatcatkill.enums.GamePhase;
import com.fatcatkill.enums.Role;
import com.fatcatkill.enums.RoomStatus;
import com.fatcatkill.model.GameState;
import com.fatcatkill.model.PlayerState;
import com.fatcatkill.repository.GameRecordRepository;
import com.fatcatkill.repository.GameStateRepository;
import com.fatcatkill.repository.UserRepository;
import com.fatcatkill.store.GameStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GameHistoryTests {

    @Autowired
    private GameStore gameStore;

    @Autowired
    private GameRecordRepository gameRecordRepository;

    @Autowired
    private GameStateRepository gameStateRepository;

    @Autowired
    private GameHistoryController gameHistoryController;

    @Autowired
    private GameLogController gameLogController;

    @Autowired
    private UserRepository userRepository;

    @Test
    void finishedGameIsRecordedExactlyOnce() {
        String gameId = UUID.randomUUID().toString();
        GameState game = finishedGame("history-" + gameId, gameId, null);
        game.setStartedAt("2026-06-19T12:00:00");
        game.setCurrentRound(3);

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

    @Test
    void finishedGameDoesNotRemainAsPersistedActiveRoom() {
        String gameId = UUID.randomUUID().toString();
        GameState game = finishedGame("history-active-cleanup-" + gameId, gameId, null);

        gameStore.saveGame(game);

        assertThat(gameRecordRepository.countByGameId(gameId)).isEqualTo(1);
        assertThat(gameStateRepository.findById(game.getRoomId())).isEmpty();
        assertThat(gameStore.getGame(game.getRoomId())).isNull();
    }

    @Test
    void historyRequiresValidSessionToken() {
        User user = savedUser("history-user");

        ResponseEntity<?> missingSession = gameHistoryController.listHistory(null, null);
        assertThat(missingSession.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<?> wrongSession = gameHistoryController.listHistory(String.valueOf(user.getId()), "wrong-token");
        assertThat(wrongSession.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<?> ok = gameHistoryController.listHistory(String.valueOf(user.getId()), user.getSessionToken());
        assertThat(ok.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(ok.getBody()).isInstanceOf(List.class);
    }

    @Test
    void historyDetailRequiresValidSessionTokenAndParticipation() {
        User user = savedUser("history-detail-user");
        User otherUser = savedUser("history-detail-other");
        String gameId = UUID.randomUUID().toString();
        gameStore.saveGame(finishedGame("history-detail-" + gameId, gameId, user.getId()));

        ResponseEntity<?> denied = gameHistoryController.getHistory(gameId, String.valueOf(user.getId()), "wrong-token");
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<?> notParticipant = gameHistoryController.getHistory(gameId, String.valueOf(otherUser.getId()), otherUser.getSessionToken());
        assertThat(notParticipant.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<?> allowed = gameHistoryController.getHistory(gameId, String.valueOf(user.getId()), user.getSessionToken());
        assertThat(allowed.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(allowed.getBody()).isInstanceOf(Map.class);
    }

    @Test
    void historyListShowsOnlyParticipantRecords() {
        User user = savedUser("history-list-user");
        User otherUser = savedUser("history-list-other");

        String ownGameId = UUID.randomUUID().toString();
        gameStore.saveGame(finishedGame("history-list-own-" + ownGameId, ownGameId, user.getId()));

        String otherGameId = UUID.randomUUID().toString();
        gameStore.saveGame(finishedGame("history-list-other-" + otherGameId, otherGameId, otherUser.getId()));

        ResponseEntity<?> response = gameHistoryController.listHistory(String.valueOf(user.getId()), user.getSessionToken());

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) response.getBody();
        assertThat(records).extracting(record -> record.get("gameId")).contains(ownGameId);
        assertThat(records).extracting(record -> record.get("gameId")).doesNotContain(otherGameId);
    }

    @Test
    void finishedGameUpdatesParticipantUserStatsOnce() {
        User winner = savedUser("history-stats-winner");
        User loser = savedUser("history-stats-loser");
        String gameId = UUID.randomUUID().toString();
        GameState game = finishedGame("history-stats-" + gameId, gameId, winner.getId());
        game.getPlayers().get(1).setAccountId(loser.getId());

        gameStore.saveGame(game);
        gameStore.saveGame(game);

        User updatedWinner = userRepository.findById(winner.getId()).orElseThrow();
        User updatedLoser = userRepository.findById(loser.getId()).orElseThrow();
        assertThat(updatedWinner.getGamesPlayed()).isEqualTo(1);
        assertThat(updatedWinner.getGamesWon()).isEqualTo(1);
        assertThat(updatedLoser.getGamesPlayed()).isEqualTo(1);
        assertThat(updatedLoser.getGamesWon()).isEqualTo(0);
    }
    @Test
    void finishedGameRefreshesHistoryFinalStateWithoutDuplicatingStats() {
        User winner = savedUser("history-refresh-winner");
        String gameId = UUID.randomUUID().toString();
        GameState game = finishedGame("history-refresh-" + gameId, gameId, winner.getId());

        gameStore.saveGame(game);
        game.addLog(new com.fatcatkill.model.GameLogEntry(
                "2026-07-15T12:00:00Z",
                1L,
                "Player 1",
                Role.FATCAT.name(),
                "POST_FINISH_LOG",
                null,
                null,
                "log after finish"
        ));
        gameStore.saveGame(game);

        assertThat(gameRecordRepository.countByGameId(gameId)).isEqualTo(1);
        User updatedWinner = userRepository.findById(winner.getId()).orElseThrow();
        assertThat(updatedWinner.getGamesPlayed()).isEqualTo(1);
        assertThat(updatedWinner.getGamesWon()).isEqualTo(1);
        assertThat(gameRecordRepository.findByGameId(gameId).orElseThrow().getFinalStateJson())
                .contains("POST_FINISH_LOG");
    }


    @Test
    void historyDetailRedactsAccountIdsFromFinalState() {
        User user = savedUser("history-redact-user");
        String gameId = UUID.randomUUID().toString();
        gameStore.saveGame(finishedGame("history-redact-" + gameId, gameId, user.getId()));

        ResponseEntity<?> response = gameHistoryController.getHistory(gameId, String.valueOf(user.getId()), user.getSessionToken());

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(String.valueOf(body.get("finalState"))).doesNotContain("accountId");
    }


    @Test
    void historyDetailRedactsNestedAccountCredentialsFromFinalState() {
        User user = savedUser("history-redact-nested-user");
        String gameId = UUID.randomUUID().toString();
        GameRecord record = new GameRecord();
        record.setGameId(gameId);
        record.setRoomId("history-redact-nested-" + gameId);
        record.setGameMode("OLD_HOME");
        record.setWinnerCamp(Camp.WOLF);
        record.setRoundsPlayed(1);
        record.setPlayerCount(1);
        record.setParticipantAccountIds(String.valueOf(user.getId()));
        record.setStartedAt(LocalDateTime.now());
        record.setEndTime(LocalDateTime.now());
        record.setFinalStateJson("{\"players\":[{\"userId\":1,\"accountId\":123,\"sessionToken\":\"secret\",\"nested\":{\"accountId\":456,\"sessionToken\":\"nested-secret\"}}]}");
        gameRecordRepository.save(record);

        ResponseEntity<?> response = gameHistoryController.getHistory(gameId, String.valueOf(user.getId()), user.getSessionToken());

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        String finalState = String.valueOf(body.get("finalState"));
        assertThat(finalState).doesNotContain("accountId");
        assertThat(finalState).doesNotContain("sessionToken");
        assertThat(finalState).doesNotContain("secret");
    }

    @Test
    void historyDetailDoesNotReturnRawMalformedFinalState() {
        User user = savedUser("history-malformed-user");
        String gameId = UUID.randomUUID().toString();
        GameRecord record = new GameRecord();
        record.setGameId(gameId);
        record.setRoomId("history-malformed-" + gameId);
        record.setGameMode("OLD_HOME");
        record.setWinnerCamp(Camp.WOLF);
        record.setRoundsPlayed(1);
        record.setPlayerCount(1);
        record.setParticipantAccountIds(String.valueOf(user.getId()));
        record.setStartedAt(LocalDateTime.now());
        record.setEndTime(LocalDateTime.now());
        record.setFinalStateJson("{\"players\":[{\"accountId\":123,\"sessionToken\":\"secret\"}]");
        gameRecordRepository.save(record);

        ResponseEntity<?> response = gameHistoryController.getHistory(gameId, String.valueOf(user.getId()), user.getSessionToken());

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("finalState")).isEqualTo("");
    }

    @Test
    void logsExportLoadsFinishedGameFromHistoryAndHandlesPlayersWithoutRoleOrSeat() {
        String gameId = UUID.randomUUID().toString();
        GameState game = new GameState();
        game.setGameId(gameId);
        game.setRoomId("logs-null-safe-" + gameId);
        game.setStatus(RoomStatus.FINISHED);
        PlayerState player = new PlayerState();
        player.setUserId(1L);
        player.setUsername(null);
        player.setAlive(true);
        player.setBot(true);
        game.setPlayers(List.of(player));
        gameStore.saveGame(game);

        assertThat(gameStore.getGame(game.getRoomId())).isNull();

        ResponseEntity<?> response = gameLogController.getLogs(game.getRoomId());

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(String.valueOf(body.get("players"))).contains("role=").contains("bot=true");
    }

    private User savedUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "-" + System.nanoTime());
        user.setPassword("pbkdf2$fake");
        user.setSessionToken("history-token-" + UUID.randomUUID());
        userRepository.save(user);
        return user;
    }

    private GameState finishedGame(String roomId, String gameId, Long accountId) {
        GameState game = new GameState();
        game.setGameId(gameId);
        game.setRoomId(roomId);
        game.setGameMode("OLD_HOME");
        game.setCurrentRound(1);
        game.setCurrentPhase(GamePhase.GAME_OVER);
        game.setStatus(RoomStatus.FINISHED);
        PlayerState participant = player(1L, Role.FATCAT);
        participant.setAccountId(accountId);
        game.setPlayers(List.of(participant, player(2L, Role.METHANE)));
        return game;
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
