package com.fatcatkill.controller;

import com.fatcatkill.enums.RoomStatus;
import com.fatcatkill.model.GameState;
import com.fatcatkill.model.PlayerState;
import com.fatcatkill.service.GameLogService;
import com.fatcatkill.store.GameStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/room")
public class CreateMockRoomController {

    private final GameStore gameStore;
    private final GameLogService gameLogService;

    public CreateMockRoomController(GameStore gameStore, GameLogService gameLogService) {
        this.gameStore = gameStore;
        this.gameLogService = gameLogService;
    }

    // 新增參數 ?count=10，如果沒傳預設就是 6 人
    @PostMapping("/mock/{roomId}")
    public ResponseEntity<GameState> execute(
            @PathVariable String roomId, 
            @RequestParam(defaultValue = "6") int count) {
            
        GameState gameState = new GameState();
        gameState.setRoomId(roomId);
        gameState.setStatus(RoomStatus.WAITING);
        List<PlayerState> players = new ArrayList<>();
        
        // 根據傳入的 count 決定塞幾隻胖貓
        for (long i = 1; i <= count; i++) {
            PlayerState player = new PlayerState();
            player.setUserId(i);
            player.setUsername("胖貓測試員_" + i); // ⬅️ 這裡修復了：加上了 set
            players.add(player);
        }
        gameState.setPlayers(players);
        gameStore.saveGame(gameState);
        gameLogService.append(gameState, null, "CREATE_MOCK_ROOM", null, null, "Created mock room with " + count + " players.");
        return ResponseEntity.ok(gameState);
    }

    @PostMapping("/fill-bots/{roomId}")
    public ResponseEntity<GameState> fillBots(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "6") int count,
            @RequestBody(required = false) Map<String, Object> payload) {

        GameState gameState = new GameState();
        gameState.setRoomId(roomId);
        gameState.setStatus(RoomStatus.WAITING);
        if (payload != null && payload.get("playerId") instanceof Number hostId) {
            gameState.setHostId(hostId.longValue());
        }

        List<PlayerState> players = new ArrayList<>();
        Set<Long> usedIds = new HashSet<>();

        Object rawPlayers = payload == null ? null : payload.get("players");
        if (rawPlayers instanceof List<?> humanPlayers) {
            for (Object rawPlayer : humanPlayers) {
                if (!(rawPlayer instanceof Map<?, ?> humanData)) continue;
                Object rawUserId = humanData.get("userId");
                if (!(rawUserId instanceof Number userIdNumber)) continue;

                Long userId = userIdNumber.longValue();
                if (usedIds.contains(userId)) continue;

                String nickname = humanData.get("nickname") == null
                        ? "Player " + userId
                        : humanData.get("nickname").toString().trim();
                if (nickname.isEmpty()) {
                    nickname = "Player " + userId;
                }

                PlayerState player = new PlayerState();
                player.setUserId(userId);
                player.setUsername(nickname);
                players.add(player);
                usedIds.add(userId);

                if (players.size() >= count) break;
            }
        }

        long nextBotId = 1;
        while (players.size() < count) {
            while (usedIds.contains(nextBotId)) {
                nextBotId++;
            }

            PlayerState bot = new PlayerState();
            bot.setUserId(nextBotId);
            bot.setUsername("Bot " + nextBotId);
            players.add(bot);
            usedIds.add(nextBotId);
            nextBotId++;
        }

        gameState.setPlayers(players);
        gameStore.saveGame(gameState);
        gameLogService.append(gameState, null, "FILL_BOTS", null, null, "Filled room to " + count + " players with bots.");
        return ResponseEntity.ok(gameState);
    }
}
