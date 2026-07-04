package com.fatcatkill;

import com.fatcatkill.enums.Role;
import com.fatcatkill.model.GameActionPayload;
import com.fatcatkill.model.InvalidGameActionPayloadException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameActionPayloadTests {

    @Test
    void parsesActionPayloadAndKeepsPrimaryLogTarget() {
        GameActionPayload action = GameActionPayload.from(Map.of(
                "roomId", "room-1",
                "playerId", 3,
                "actionType", "METHANE_CHECK",
                "targetRole", "METHANE",
                "targetId1", 4,
                "targetId2", 5
        ));

        assertThat(action.roomId()).isEqualTo("room-1");
        assertThat(action.playerId()).isEqualTo(3L);
        assertThat(action.targetRole()).isEqualTo(Role.METHANE);
        assertThat(action.primaryLogTargetId()).isEqualTo(4L);
    }

    @Test
    void missingRequiredFieldReturnsMessagePayloadException() {
        assertThatThrownBy(() -> GameActionPayload.from(Map.of(
                "roomId", "room-1",
                "actionType", "FATCAT_KILL"
        )))
                .isInstanceOf(InvalidGameActionPayloadException.class)
                .extracting(error -> ((InvalidGameActionPayloadException) error).getMessagePayload().getKey())
                .isEqualTo("backend.action.missingField");
    }

    @Test
    void invalidRoleReturnsMessagePayloadException() {
        assertThatThrownBy(() -> GameActionPayload.from(Map.of(
                "roomId", "room-1",
                "playerId", 1,
                "actionType", "PH_SERVICE_ACTION",
                "targetRole", "NOT_A_ROLE"
        )))
                .isInstanceOf(InvalidGameActionPayloadException.class)
                .extracting(error -> ((InvalidGameActionPayloadException) error).getMessagePayload().getKey())
                .isEqualTo("backend.action.invalidRole");
    }
}