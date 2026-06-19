package com.fatcatkill.service;

import com.fatcatkill.model.GameState;
import com.fatcatkill.model.PlayerState;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class SystemOutService {

    public void action(GameState game, Long playerId, String actionType, Long targetId, Long targetId2, String message) {
        if (game == null) {
            System.out.println(format(null, null, null, actionType, targetId, targetId2, message));
            return;
        }

        PlayerState actor = findPlayer(game, playerId);
        PlayerState target = findPlayer(game, targetId);
        PlayerState target2 = findPlayer(game, targetId2);
        String detail = (withPlayerLabel("actor", actor, playerId)
                + withPlayerLabel("target", target, targetId)
                + withPlayerLabel("target2", target2, targetId2)
                + (message == null || message.isBlank() ? "" : " message=\"" + message + "\""))
                .trim();

        System.out.println(format(
                game.getRoomId(),
                game.getCurrentRound(),
                game.getCurrentPhase() == null ? null : game.getCurrentPhase().name(),
                actionType,
                targetId,
                targetId2,
                detail
        ));
    }

    public void room(String roomId, String actionType, String message) {
        System.out.println(format(roomId, null, null, actionType, null, null, message));
    }

    private String format(String roomId, Integer round, String phase, String actionType, Long targetId, Long targetId2, String detail) {
        StringBuilder builder = new StringBuilder("[FatCatKill] ");
        builder.append(Instant.now());
        if (roomId != null) builder.append(" room=").append(roomId);
        if (round != null) builder.append(" round=").append(round);
        if (phase != null) builder.append(" phase=").append(phase);
        builder.append(" action=").append(actionType == null ? "UNKNOWN" : actionType);
        if (targetId != null && (detail == null || !detail.contains("target="))) {
            builder.append(" targetId=").append(targetId);
        }
        if (targetId2 != null && (detail == null || !detail.contains("target2="))) {
            builder.append(" targetId2=").append(targetId2);
        }
        if (detail != null && !detail.isBlank()) builder.append(" ").append(detail);
        return builder.toString();
    }

    private PlayerState findPlayer(GameState game, Long playerId) {
        if (game == null || playerId == null || game.getPlayers() == null) return null;
        return game.getPlayers().stream()
                .filter(player -> playerId.equals(player.getUserId()))
                .findFirst()
                .orElse(null);
    }

    private String withPlayerLabel(String label, PlayerState player, Long fallbackId) {
        if (fallbackId == null) return "";
        if (player == null) return " " + label + "=" + fallbackId;
        String role = player.getRole() == null ? "UNKNOWN" : player.getRole().name();
        return " " + label + "=" + player.getUserId()
                + "(" + player.getUsername() + "/" + role + ")";
    }
}
