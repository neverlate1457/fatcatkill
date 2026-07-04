package com.fatcatkill;

import com.fatcatkill.controller.CreateMockRoomController;
import com.fatcatkill.controller.StartGameController;
import com.fatcatkill.model.GameState;
import com.fatcatkill.model.MessagePayload;
import com.fatcatkill.enums.Role;
import com.fatcatkill.store.GameStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RoomControllerTests {

    @Autowired
    private CreateMockRoomController controller;

    @Autowired
    private StartGameController startGameController;

    @Autowired
    private GameStore gameStore;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void mockRoomReturnsGameState() throws Exception {
        ResponseEntity<GameState> response = controller.execute("test-room", 7);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        objectMapper.writeValueAsString(response.getBody());
    }

    @Test
    void fillBotsReturnsGameState() throws Exception {
        ResponseEntity<GameState> response = controller.fillBots(
                "test-bots",
                7,
                Map.of("players", List.of(Map.of("userId", 123, "nickname", "Tester")))
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        objectMapper.writeValueAsString(response.getBody());
    }

    @Test
    void hostCanStartCustomizedGameWithControlledOutcomes() {
        controller.fillBots("host-custom", 7, Map.of(
                "playerId", 99,
                "hostMode", true,
                "players", List.of(Map.of("userId", 10, "nickname", "Human"))
        ));

        List<String> deck = List.of("FATCAT", "LIVER_INDEX", "HIGH_RABBIT",
                "METHANE", "GUOGUO", "XIANGXIANG", "AC_CAT");
        ResponseEntity<?> response = startGameController.execute(
                "host-custom",
                "OLD_HOME",
                Map.of(
                        "hostMode", true,
                        "customDeck", deck,
                        "fatcatHintRoles", List.of("FORVKUSA", "HATONG"),
                        "highRabbitRole", "METHANE"
                )
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        GameState game = (GameState) response.getBody();
        assertThat(game).isNotNull();
        assertThat(game.isHostMode()).isTrue();
        assertThat(game.getGameMode()).isEqualTo("CUSTOM");
        assertThat(game.getPlayers()).extracting(player -> player.getRole()).containsExactlyInAnyOrder(
                Role.FATCAT, Role.LIVER_INDEX, Role.HIGH_RABBIT, Role.METHANE,
                Role.GUOGUO, Role.XIANGXIANG, Role.AC_CAT
        );
        assertThat(game.getFatcatAbsentVolunteerHintRoles()).containsExactly(Role.FORVKUSA, Role.HATONG);
        Long rabbitId = game.getPlayers().stream().filter(player -> player.getRole() == Role.HIGH_RABBIT)
                .findFirst().orElseThrow().getUserId();
        assertThat(game.getHighRabbitPerceivedRoles()).containsEntry(rabbitId, Role.METHANE);
        assertThat(game.getPlayers()).noneMatch(player -> player.getUserId().equals(99L));
    }


    @Test
    void startCreatesWaitingRoomFromConnectedPlayers() {
        String roomId = "start-from-connected";
        ResponseEntity<?> response = startGameController.execute(
                roomId,
                "OLD_HOME",
                Map.of(
                        "playerId", 1,
                        "players", List.of(
                                Map.of("userId", 1, "nickname", "Host"),
                                Map.of("userId", 2, "nickname", "Player 2"),
                                Map.of("userId", 3, "nickname", "Player 3"),
                                Map.of("userId", 4, "nickname", "Player 4"),
                                Map.of("userId", 5, "nickname", "Player 5"),
                                Map.of("userId", 6, "nickname", "Player 6"),
                                Map.of("userId", 7, "nickname", "Player 7")
                        )
                )
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        GameState game = gameStore.getGame(roomId);
        assertThat(game).isNotNull();
        assertThat(game.getStatus()).isEqualTo(com.fatcatkill.enums.RoomStatus.PLAYING);
        assertThat(game.getPlayers()).hasSize(7);
        assertThat(game.getPlayers()).extracting(player -> player.getUsername()).contains("Host", "Player 7");
        assertThat(game.getPlayers()).anyMatch(player -> player.getRole() == Role.FATCAT);
    }

    @Test
    void failedStartDoesNotApplyPlayingState() {
        controller.fillBots("failed-start-clean", 7, Map.of());
        GameState before = (GameState) gameStore.getGame("failed-start-clean");
        before.getPlayers().forEach(player -> {
            player.setRole(Role.VILLAGER);
            player.setAlive(false);
            player.setSeatNumber(null);
            player.setVotedTargetId(99L);
            player.setVoteConfirmed(true);
        });
        gameStore.saveGame(before);

        ResponseEntity<?> response = startGameController.execute(
                "failed-start-clean",
                "OLD_HOME",
                Map.of()
        );

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        GameState after = gameStore.getGame("failed-start-clean");
        assertThat(after.getStatus()).isNotEqualTo(com.fatcatkill.enums.RoomStatus.PLAYING);
        assertThat(after.getPlayers()).allSatisfy(player -> {
            assertThat(player.isAlive()).isFalse();
            assertThat(player.getSeatNumber()).isNull();
            assertThat(player.getVotedTargetId()).isEqualTo(99L);
            assertThat(player.isVoteConfirmed()).isTrue();
        });
    }

    @Test
    void oldHomeGameCannotStartWithoutFatcat() {
        controller.fillBots("test-no-fatcat", 7, Map.of());

        ResponseEntity<?> response = startGameController.execute(
                "test-no-fatcat",
                "OLD_HOME",
                Map.of("roleAssignments", Map.of(
                        "1", "VILLAGER",
                        "2", "VILLAGER",
                        "3", "VILLAGER",
                        "4", "VILLAGER",
                        "5", "VILLAGER",
                        "6", "VILLAGER",
                        "7", "VILLAGER"
                ))
        );

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        MessagePayload message = (MessagePayload) body.get("message");
        assertThat(message.getKey()).isEqualTo("backend.room.fatcatMissing");
        assertThat(message.getFallback()).isEqualTo("Cannot start game: Fatcat role is missing.");
    }
}
