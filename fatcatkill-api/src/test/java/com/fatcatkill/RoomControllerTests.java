package com.fatcatkill;

import com.fatcatkill.controller.CreateMockRoomController;
import com.fatcatkill.controller.StartGameController;
import com.fatcatkill.model.GameState;
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
