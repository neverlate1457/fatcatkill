package com.fatcatkill.model;

import com.fatcatkill.enums.Camp;
import com.fatcatkill.enums.GamePhase;
import com.fatcatkill.enums.RoomStatus;
import com.fatcatkill.enums.Role;
import lombok.Data;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class GameState {
    private String roomId;         
    private Long hostId;           
    private RoomStatus status = RoomStatus.WAITING;
    
    private GamePhase currentPhase = GamePhase.NIGHT_START;
    private Integer currentRound = 1; 
    
    private List<PlayerState> players;
    private String gameMode;
    private String gameId;
    private String startedAt;
    private boolean historyRecorded;
    private Camp winnerCamp;
    private boolean hostMode;
    private List<Role> hostConfiguredFatcatHintRoles;
    private Role hostConfiguredHighRabbitRole;
    private Long hostConfiguredMethaneHallucinationTargetId;
    private Map<String, Long> nightActions = new ConcurrentHashMap<>();

    private Long methaneHallucinationTargetId;
    private String guoguoHint;
    // 上一日被陶片流放的玩家 ID (用於 AC 貓技能查詢)
    private Long lastExiledPlayerId;
    private Long nominatedPlayerId;
    private Long mochiBossPendingCheckPlayerId;
    private Long fatcatKillerPlayerId;
    private Long andyCloudPlayerId;
    private Long chenPendingKillPlayerId;
    private Role phServiceStolenRole;
    private List<Role> fatcatAbsentVolunteerHintRoles = new ArrayList<>();
    private Map<Long, Role> highRabbitPerceivedRoles = new ConcurrentHashMap<>();
    private Integer xiaoenRedirectRound;
    private Map<Long, Integer> dayVoteCounts = new ConcurrentHashMap<>();
    private Map<Long, Integer> lastVoteCounts = new ConcurrentHashMap<>();
    private String lastVoteResult;
    private String publicMessage;
    private Map<Long, Integer> delayedDeathRounds = new ConcurrentHashMap<>();
    private Map<Long, Integer> mubaimuDoomRounds = new ConcurrentHashMap<>();
    private Map<Long, Integer> barkKingDoomRounds = new ConcurrentHashMap<>();
    private Map<Long, Integer> drunkUntilRounds = new ConcurrentHashMap<>();
    private Map<Long, String> privateMessages = new ConcurrentHashMap<>();
    private Map<Long, Integer> strOriginalSeatNumbers = new ConcurrentHashMap<>();
    private Map<Long, Integer> strTemporarySeatNumbers = new ConcurrentHashMap<>();
    private Set<Integer> strSwappedSeatNumbers = ConcurrentHashMap.newKeySet();
    private Set<Long> lastDayVoterIds = ConcurrentHashMap.newKeySet();
    private Set<Long> kbNominationTrapTriggeredIds = ConcurrentHashMap.newKeySet();
    private Set<Long> chenUsedPlayerIds = ConcurrentHashMap.newKeySet();
    private Map<Long, Integer> chenSkippedRounds = new ConcurrentHashMap<>();
    private Set<Long> ratManCheckerIds = ConcurrentHashMap.newKeySet();
    private Set<Long> saltedFishUsedPlayerIds = ConcurrentHashMap.newKeySet();
    private Map<Long, Integer> saltedFishSkippedRounds = new ConcurrentHashMap<>();
    private Set<Long> fatcatKillBlockedPlayerIds = ConcurrentHashMap.newKeySet();
    private Set<Long> nangongUsedPlayerIds = ConcurrentHashMap.newKeySet();
    private Set<Long> finalVoteEligiblePlayerIds = ConcurrentHashMap.newKeySet();
    private Set<Long> fatcatKilledPlayerIds = ConcurrentHashMap.newKeySet();

    // 紀錄此局的事件日誌（測試/審計用途）
    private List<com.fatcatkill.model.GameLogEntry> logs = new ArrayList<>();

    public void addLog(com.fatcatkill.model.GameLogEntry entry) {
        if (logs == null) logs = new ArrayList<>();
        logs.add(entry);
    }
}

