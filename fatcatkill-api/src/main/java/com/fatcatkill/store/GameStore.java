package com.fatcatkill.store;

import com.fatcatkill.entity.GameRecord;
import com.fatcatkill.entity.PersistedGameState;
import com.fatcatkill.enums.Camp;
import com.fatcatkill.enums.Role;
import com.fatcatkill.enums.RoomStatus;
import com.fatcatkill.model.GameState;
import com.fatcatkill.repository.GameRecordRepository;
import com.fatcatkill.repository.GameStateRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GameStore {

    private final Map<String, GameState> activeGames = new ConcurrentHashMap<>();
    private final GameStateRepository repository;
    private final GameRecordRepository gameRecordRepository;
    private final ObjectMapper objectMapper;

    public GameStore() {
        this.repository = null;
        this.gameRecordRepository = null;
        this.objectMapper = new ObjectMapper();
    }

    @Autowired
    public GameStore(GameStateRepository repository, GameRecordRepository gameRecordRepository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.gameRecordRepository = gameRecordRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void clearUnfinishedPersistedGamesOnStartup() {
        if (repository == null) return;

        List<String> roomsToDelete = new ArrayList<>();
        for (PersistedGameState persisted : repository.findAll()) {
            if (shouldDropPersistedGame(persisted)) {
                roomsToDelete.add(persisted.getRoomId());
            }
        }

        for (String roomId : roomsToDelete) {
            activeGames.remove(roomId);
            repository.deleteById(roomId);
        }
    }

    private boolean shouldDropPersistedGame(PersistedGameState persisted) {
        if (persisted == null || persisted.getRoomId() == null) return false;
        try {
            GameState game = objectMapper.readValue(persisted.getStateJson(), GameState.class);
            return game == null || game.getStatus() != RoomStatus.FINISHED;
        } catch (RuntimeException ex) {
            return true;
        }
    }

    public synchronized void saveGame(GameState gameState) {
        if (gameState == null || gameState.getRoomId() == null) return;
        activeGames.put(gameState.getRoomId(), gameState);
        if (repository == null) return;

        if (gameState.getStatus() == RoomStatus.FINISHED && gameState.getWinnerCamp() == null) {
            gameState.setWinnerCamp(resolveWinnerCamp(gameState));
        }

        boolean shouldRecordHistory = gameState.getStatus() == RoomStatus.FINISHED
                && !gameState.isHistoryRecorded()
                && gameRecordRepository != null;
        if (shouldRecordHistory) {
            if (gameState.getGameId() == null || gameState.getGameId().isBlank()) {
                gameState.setGameId(UUID.randomUUID().toString());
            }
            gameState.setHistoryRecorded(true);
        }

        String stateJson = objectMapper.writeValueAsString(gameState);
        if (shouldRecordHistory && !gameRecordRepository.existsByGameId(gameState.getGameId())) {
            gameRecordRepository.save(buildHistoryRecord(gameState, stateJson));
        }

        PersistedGameState persisted = new PersistedGameState();
        persisted.setRoomId(gameState.getRoomId());
        persisted.setStateJson(stateJson);
        persisted.setUpdatedAt(LocalDateTime.now());
        repository.save(persisted);
    }

    public GameState getGame(String roomId) {
        if (roomId == null) return null;
        GameState cached = activeGames.get(roomId);
        if (cached != null || repository == null) return cached;

        return repository.findById(roomId)
                .map(persisted -> objectMapper.readValue(persisted.getStateJson(), GameState.class))
                .map(game -> {
                    activeGames.put(roomId, game);
                    return game;
                })
                .orElse(null);
    }

    public void removeGame(String roomId) {
        if (roomId == null) return;
        activeGames.remove(roomId);
        if (repository != null) repository.deleteById(roomId);
    }

    private GameRecord buildHistoryRecord(GameState game, String stateJson) {
        GameRecord record = new GameRecord();
        record.setGameId(game.getGameId());
        record.setRoomId(game.getRoomId());
        record.setGameMode(game.getGameMode());
        record.setWinnerCamp(game.getWinnerCamp());
        record.setRoundsPlayed(game.getCurrentRound());
        record.setPlayerCount(game.getPlayers() == null ? 0 : game.getPlayers().size());
        record.setParticipantAccountIds(participantAccountIds(game));
        record.setStartedAt(parseStartedAt(game.getStartedAt()));
        record.setEndTime(LocalDateTime.now());
        record.setFinalStateJson(stateJson);
        return record;
    }

    private String participantAccountIds(GameState game) {
        if (game.getPlayers() == null) return "";
        return game.getPlayers().stream()
                .map(player -> player.getAccountId())
                .filter(id -> id != null)
                .distinct()
                .sorted()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private Camp resolveWinnerCamp(GameState game) {
        return hasAliveFatcat(game) ? Camp.WOLF : Camp.VILLAGER;
    }

    private boolean hasAliveFatcat(GameState game) {
        return game.getPlayers() != null && game.getPlayers().stream()
                .anyMatch(player -> player.isAlive() && player.getRole() == Role.FATCAT);
    }

    private LocalDateTime parseStartedAt(String startedAt) {
        if (startedAt == null || startedAt.isBlank()) return null;
        try {
            return LocalDateTime.parse(startedAt);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
