package com.fatcatkill;

import com.fatcatkill.controller.CreateMockRoomController;
import com.fatcatkill.controller.GetRoomController;
import com.fatcatkill.controller.DebugController;
import com.fatcatkill.controller.StartGameController;
import com.fatcatkill.entity.User;
import com.fatcatkill.model.GameState;
import com.fatcatkill.model.MessagePayload;
import com.fatcatkill.model.PlayerState;
import com.fatcatkill.repository.UserRepository;
import com.fatcatkill.service.GameLogService;
import com.fatcatkill.service.SystemOutService;
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
    private GetRoomController getRoomController;

    @Autowired
    private DebugController debugController;

    @Autowired
    private GameStore gameStore;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    void mockRoomReturnsGameState() throws Exception {
        ResponseEntity<?> response = controller.execute("test-room", 7);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        objectMapper.writeValueAsString(response.getBody());
    }

    @Test
    void debugActionsValidatePayloadBeforeCasting() {
        ResponseEntity<?> missingPayload = debugController.setRole(null);
        ResponseEntity<?> invalidPlayerId = debugController.forceAcCat(Map.of(
                "roomId", "debug-invalid-payload",
                "playerId", "not-a-number"
        ));

        assertThat(missingPayload.getStatusCode().is4xxClientError()).isTrue();
        assertThat(messagePayload(missingPayload).getKey()).isEqualTo("backend.action.missingPayload");
        assertThat(invalidPlayerId.getStatusCode().is4xxClientError()).isTrue();
        assertThat(messagePayload(invalidPlayerId).getKey()).isEqualTo("backend.action.expectedNumericId");
        assertThat(messagePayload(invalidPlayerId).getParams()).containsEntry("field", "playerId");
    }
    @Test
    void mockRoomIsRejectedWhenDebugActionsAreDisabled() {
        GameStore isolatedStore = new GameStore();
        SystemOutService systemOutService = new SystemOutService();
        CreateMockRoomController lockedController = new CreateMockRoomController(
                isolatedStore,
                new GameLogService(isolatedStore, systemOutService),
                null,
                false
        );

        ResponseEntity<?> response = lockedController.execute("locked-mock-room", 7);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        MessagePayload message = (MessagePayload) body.get("message");
        assertThat(message.getKey()).isEqualTo("backend.debug.disabled");
        assertThat(isolatedStore.getGame("locked-mock-room")).isNull();
    }

    @Test
    void fillBotsReturnsGameState() throws Exception {
        ResponseEntity<?> response = controller.fillBots(
                "test-bots",
                7,
                Map.of("players", List.of(Map.of("userId", 123, "nickname", "Tester")))
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        objectMapper.writeValueAsString(response.getBody());
    }

    @Test
    void fillBotsBindsAccountOnlyWhenSessionTokenMatches() {
        User user = new User();
        user.setUsername("room-account-" + System.nanoTime());
        user.setUsernameKey(user.getUsername().toLowerCase(java.util.Locale.ROOT));
        user.setPassword("pbkdf2$test");
        user.setSessionToken("valid-room-token");
        user = userRepository.save(user);

        ResponseEntity<?> invalidTokenResponse = controller.fillBots(
                "account-invalid-token",
                7,
                Map.of("players", List.of(Map.of(
                        "userId", 1,
                        "nickname", "Human",
                        "accountId", user.getId(),
                        "sessionToken", "wrong-token"
                )))
        );
        ResponseEntity<?> validTokenResponse = controller.fillBots(
                "account-valid-token",
                7,
                Map.of("players", List.of(Map.of(
                        "userId", 1,
                        "nickname", "Human",
                        "accountId", user.getId(),
                        "sessionToken", "valid-room-token"
                )))
        );

        assertThat(invalidTokenResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(validTokenResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(gameStore.getGame("account-invalid-token").getPlayers().get(0).getAccountId()).isNull();
        assertThat(gameStore.getGame("account-valid-token").getPlayers().get(0).getAccountId()).isEqualTo(user.getId());
    }
    @Test
    void fillBotsCannotOverwritePlayingGame() {
        String roomId = "fill-bots-playing-guard";
        ResponseEntity<?> initial = controller.fillBots(roomId, 7, Map.of());
        assertThat(initial.getStatusCode().is2xxSuccessful()).isTrue();
        GameState game = gameStore.getGame(roomId);
        game.setStatus(com.fatcatkill.enums.RoomStatus.PLAYING);
        String originalName = game.getPlayers().get(0).getUsername();
        gameStore.saveGame(game);

        ResponseEntity<?> denied = controller.fillBots(roomId, 7, Map.of("players", List.of(Map.of("userId", 1, "nickname", "Replacement"))));

        assertThat(denied.getStatusCode().is4xxClientError()).isTrue();
        GameState after = gameStore.getGame(roomId);
        assertThat(after.getStatus()).isEqualTo(com.fatcatkill.enums.RoomStatus.PLAYING);
        assertThat(after.getPlayers().get(0).getUsername()).isEqualTo(originalName);
    }
    @Test
    void getRoomDoesNotExposeAccountIdsOrMutateStoredState() {
        controller.fillBots("room-public-copy", 7, Map.of());
        GameState stored = gameStore.getGame("room-public-copy");
        stored.getPlayers().get(0).setAccountId(123L);
        gameStore.saveGame(stored);

        ResponseEntity<GameState> response = getRoomController.getRoom("room-public-copy");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getPlayers().get(0).getAccountId()).isNull();
        assertThat(objectMapper.writeValueAsString(response.getBody())).doesNotContain("accountId");
        assertThat(gameStore.getGame("room-public-copy").getPlayers().get(0).getAccountId()).isEqualTo(123L);
    }

    @Test
    void getRoomFastForwardsWhenOnlyBotsRemain() {
        GameState game = new GameState();
        game.setRoomId("room-only-bots-get");
        game.setStatus(com.fatcatkill.enums.RoomStatus.PLAYING);
        game.setCurrentPhase(com.fatcatkill.enums.GamePhase.DAY_START);
        game.setPlayers(List.of(
                botPlayer(1L, Role.FATCAT),
                botPlayer(2L, Role.METHANE),
                botPlayer(3L, Role.GUOGUO)
        ));
        gameStore.saveGame(game);

        ResponseEntity<GameState> response = getRoomController.getRoom(game.getRoomId());

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(com.fatcatkill.enums.RoomStatus.FINISHED);
        assertThat(response.getBody().getCurrentPhase()).isEqualTo(com.fatcatkill.enums.GamePhase.GAME_OVER);
        assertThat(response.getBody().getLogs()).anySatisfy(log -> assertThat(log.getActionType()).isEqualTo("BOT_ONLY_FAST_FORWARD"));
        assertThat(gameStore.getGame(game.getRoomId())).isNull();
    }
    @Test
    void deleteRoomRequiresHostWhenHostIdIsKnown() {
        controller.fillBots("delete-host-guard", 7, Map.of("playerId", 1));

        ResponseEntity<?> denied = getRoomController.deleteRoom("delete-host-guard", Map.of("playerId", 2));

        assertThat(denied.getStatusCode().value()).isEqualTo(403);
        assertThat(gameStore.getGame("delete-host-guard")).isNotNull();

        ResponseEntity<?> allowed = getRoomController.deleteRoom("delete-host-guard", Map.of("playerId", 1));

        assertThat(allowed.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(gameStore.getGame("delete-host-guard")).isNull();
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
    void customDeckInvalidRoleReturnsMessagePayload() {
        controller.fillBots("custom-invalid-role", 7, Map.of(
                "playerId", 99,
                "hostMode", true,
                "players", List.of(Map.of("userId", 10, "nickname", "Human"))
        ));

        ResponseEntity<?> response = startGameController.execute(
                "custom-invalid-role",
                "OLD_HOME",
                Map.of(
                        "hostMode", true,
                        "customDeck", List.of("FATCAT", "NOT_A_ROLE", "METHANE", "GUOGUO", "XIANGXIANG", "AC_CAT", "FORVKUSA")
                )
        );

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(messagePayload(response).getKey()).isEqualTo("backend.action.invalidRole");
        assertThat(messagePayload(response).getParams()).containsEntry("role", "NOT_A_ROLE");
    }
    @Test
    void customDeckRejectsUnsupportedClassicRoles() {
        controller.fillBots("custom-classic-guard", 7, Map.of(
                "playerId", 99,
                "hostMode", true,
                "players", List.of(Map.of("userId", 10, "nickname", "Human"))
        ));

        ResponseEntity<?> response = startGameController.execute(
                "custom-classic-guard",
                "OLD_HOME",
                Map.of(
                        "hostMode", true,
                        "customDeck", List.of("FATCAT", "WITCH", "METHANE", "GUOGUO", "XIANGXIANG", "AC_CAT", "FORVKUSA")
                )
        );

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        MessagePayload message = (MessagePayload) body.get("message");
        assertThat(message.getKey()).isEqualTo("backend.room.customDeckUnsupportedRole");
        assertThat(gameStore.getGame("custom-classic-guard").getStatus()).isEqualTo(com.fatcatkill.enums.RoomStatus.WAITING);
    }
    private PlayerState botPlayer(Long userId, Role role) {
        PlayerState player = new PlayerState();
        player.setUserId(userId);
        player.setUsername("Bot " + userId);
        player.setBot(true);
        player.setRole(role);
        player.setAlive(true);
        return player;
    }
    private MessagePayload messagePayload(ResponseEntity<?> response) {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        return (MessagePayload) body.get("message");
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
