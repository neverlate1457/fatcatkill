package com.fatcatkill.store;

import com.fatcatkill.entity.GameRecord;
import com.fatcatkill.entity.PersistedGameState;
import com.fatcatkill.enums.Camp;
import com.fatcatkill.enums.Role;
import com.fatcatkill.enums.RoomStatus;
import com.fatcatkill.model.GameState;
import com.fatcatkill.model.PlayerState;
import com.fatcatkill.repository.GameRecordRepository;
import com.fatcatkill.repository.GameStateRepository;
import com.fatcatkill.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GameStore {

    private final Map<String, GameState> activeGames = new ConcurrentHashMap<>();
    private final GameStateRepository repository;
    private final GameRecordRepository gameRecordRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public GameStore() {
        this.repository = null;
        this.gameRecordRepository = null;
        this.userRepository = null;
        this.objectMapper = new ObjectMapper();
    }

    @Autowired
    public GameStore(GameStateRepository repository, GameRecordRepository gameRecordRepository, UserRepository userRepository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.gameRecordRepository = gameRecordRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void clearPersistedActiveGamesOnStartup() {
        if (repository == null) return;
        activeGames.clear();
        repository.deleteAll();
    }

    public synchronized void saveGame(GameState gameState) {
        if (gameState == null || gameState.getRoomId() == null) return;

        if (gameState.getStatus() == RoomStatus.FINISHED && gameState.getWinnerCamp() == null) {
            gameState.setWinnerCamp(resolveWinnerCamp(gameState));
        }

        boolean finished = gameState.getStatus() == RoomStatus.FINISHED;
        if (finished) {
            activeGames.remove(gameState.getRoomId());
        } else {
            activeGames.put(gameState.getRoomId(), gameState);
        }

        if (repository == null) return;

        boolean shouldRecordHistory = finished
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
            updateUserStats(gameState);
        } else if (finished && gameState.isHistoryRecorded() && gameState.getGameId() != null && gameRecordRepository != null) {
            refreshHistoryRecord(gameState, stateJson);
        }

        if (finished) {
            repository.deleteById(gameState.getRoomId());
            return;
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

    public GameState publicGameState(GameState game) {
        if (game == null) return null;
        GameState copy = objectMapper.readValue(objectMapper.writeValueAsString(game), GameState.class);
        if (copy.getPlayers() != null) {
            copy.getPlayers().forEach(player -> player.setAccountId(null));
        }
        return copy;
    }

    public void removeGame(String roomId) {
        if (roomId == null) return;
        activeGames.remove(roomId);
        if (repository != null) repository.deleteById(roomId);
    }

    private GameRecord buildHistoryRecord(GameState game, String stateJson) {
        GameRecord record = new GameRecord();
        record.setGameId(game.getGameId());
        applyHistoryRecordState(record, game, stateJson);
        record.setEndTime(LocalDateTime.now());
        return record;
    }

    private void refreshHistoryRecord(GameState game, String stateJson) {
        gameRecordRepository.findByGameId(game.getGameId()).ifPresent(record -> {
            applyHistoryRecordState(record, game, stateJson);
            gameRecordRepository.save(record);
        });
    }

    private void applyHistoryRecordState(GameRecord record, GameState game, String stateJson) {
        record.setRoomId(game.getRoomId());
        record.setGameMode(game.getGameMode());
        record.setWinnerCamp(game.getWinnerCamp());
        record.setRoundsPlayed(game.getCurrentRound());
        record.setPlayerCount(game.getPlayers() == null ? 0 : game.getPlayers().size());
        record.setParticipantAccountIds(participantAccountIds(game));
        record.setStartedAt(parseStartedAt(game.getStartedAt()));
        record.setFinalStateJson(stateJson);
    }

    private String participantAccountIds(GameState game) {
        if (game.getPlayers() == null) return "";
        return game.getPlayers().stream()
                .map(PlayerState::getAccountId)
                .filter(id -> id != null)
                .distinct()
                .sorted()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private void updateUserStats(GameState game) {
        if (userRepository == null || game.getPlayers() == null || game.getWinnerCamp() == null) return;

        Map<Long, Boolean> accountWins = new LinkedHashMap<>();
        for (PlayerState player : game.getPlayers()) {
            Long accountId = player.getAccountId();
            if (accountId == null) continue;
            accountWins.merge(accountId, playerWon(player, game.getWinnerCamp()), Boolean::logicalOr);
        }

        for (Map.Entry<Long, Boolean> entry : accountWins.entrySet()) {
            userRepository.findById(entry.getKey()).ifPresent(user -> {
                user.setGamesPlayed(valueOrZero(user.getGamesPlayed()) + 1);
                if (Boolean.TRUE.equals(entry.getValue())) {
                    user.setGamesWon(valueOrZero(user.getGamesWon()) + 1);
                }
                userRepository.save(user);
            });
        }
    }

    private boolean playerWon(PlayerState player, Camp winnerCamp) {
        boolean fatcatWinFaction = isFatcatWinFaction(player.getRole());
        return winnerCamp == Camp.WOLF ? fatcatWinFaction : !fatcatWinFaction;
    }

    private boolean isFatcatWinFaction(Role role) {
        return role == Role.FATCAT
                || role == Role.LIVER_INDEX
                || role == Role.PINK_RABBIT
                || role == Role.EMPEROR
                || role == Role.NTHU_MATH
                || role == Role.MAGIC_MEOW
                || role == Role.PH_SERVICE
                || role == Role.RAT_MAN;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
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
