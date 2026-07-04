package com.fatcatkill.service;

import com.fatcatkill.model.GameLogEntry;
import com.fatcatkill.model.GameState;
import com.fatcatkill.model.MessagePayload;
import com.fatcatkill.model.PlayerState;
import com.fatcatkill.store.GameStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class GameLogService {

    private final GameStore gameStore;
    private final SystemOutService systemOutService;

    public GameLogService(GameStore gameStore, SystemOutService systemOutService) {
        this.gameStore = gameStore;
        this.systemOutService = systemOutService;
    }

    public void append(GameState game, Long playerId, String actionType, Long targetId, Long targetId2, String message) {
        MessagePayload payload = message == null
                ? MessagePayload.of(null, null)
                : MessagePayload.of("backend.raw", Map.of("text", message), message);
        appendPayload(game, playerId, actionType, targetId, targetId2, payload);
    }

    public void appendPayload(GameState game, Long playerId, String actionType, Long targetId, Long targetId2, MessagePayload message) {
        if (game == null) return;

        PlayerState actor = playerId == null ? null : game.getPlayers().stream()
                .filter(player -> player.getUserId().equals(playerId))
                .findFirst()
                .orElse(null);

        String username = actor == null ? null : actor.getUsername();
        String roleName = actor == null || actor.getRole() == null ? null : actor.getRole().name();
        String fallback = message == null ? null : message.getFallback();

        GameLogEntry entry = new GameLogEntry(
                Instant.now().toString(),
                playerId,
                username,
                roleName,
                actionType,
                targetId,
                targetId2,
                fallback
        );
        if (message != null) {
            entry.setMessageKey(message.getKey());
            entry.setMessageParams(message.getParams());
            entry.setMessageFallback(message.getFallback());
        }
        entry.setTargetName(findUsername(game, targetId));
        entry.setTargetName2(findUsername(game, targetId2));
        game.addLog(entry);
        gameStore.saveGame(game);
        systemOutService.action(game, playerId, actionType, targetId, targetId2, fallback);
    }

    private String findUsername(GameState game, Long playerId) {
        if (game == null || playerId == null) return null;
        return game.getPlayers().stream()
                .filter(player -> player.getUserId().equals(playerId))
                .map(PlayerState::getUsername)
                .findFirst()
                .orElse(null);
    }
}

