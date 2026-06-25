package com.fatcatkill;

import com.fatcatkill.controller.CreateMockRoomController;
import com.fatcatkill.controller.StartGameController;
import com.fatcatkill.model.GameState;
import com.fatcatkill.enums.Role;
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
        assertThat(response.getBody()).asString().contains("Fatcat role is missing");
    }
}
