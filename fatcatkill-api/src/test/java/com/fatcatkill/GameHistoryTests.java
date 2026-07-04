package com.fatcatkill;

import com.fatcatkill.controller.GameHistoryController;
import com.fatcatkill.entity.User;
import com.fatcatkill.enums.Camp;
import com.fatcatkill.enums.GamePhase;
import com.fatcatkill.enums.Role;
import com.fatcatkill.enums.RoomStatus;
import com.fatcatkill.model.GameState;
import com.fatcatkill.model.PlayerState;
import com.fatcatkill.repository.GameRecordRepository;
import com.fatcatkill.repository.UserRepository;
import com.fatcatkill.store.GameStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
    private GameHistoryController gameHistoryController;

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