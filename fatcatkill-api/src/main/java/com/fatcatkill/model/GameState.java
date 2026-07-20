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
    
    private List<PlayerState> players = new ArrayList<>();
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
    private Object lastVoteResult;
    private Object publicMessage;
    private Map<Long, Integer> delayedDeathRounds = new ConcurrentHashMap<>();
    private Map<Long, Integer> mubaimuDoomRounds = new ConcurrentHashMap<>();
    private Map<Long, Integer> barkKingDoomRounds = new ConcurrentHashMap<>();
    private Map<Long, Integer> drunkUntilRounds = new ConcurrentHashMap<>();
    private Map<Long, Object> privateMessages = new ConcurrentHashMap<>();
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


    public List<PlayerState> getPlayers() {
        if (players == null) players = new ArrayList<>();
        return players;
    }

    public void setPlayers(List<PlayerState> players) {
        this.players = players == null ? new ArrayList<>() : players;
    }

    public Map<String, Long> getNightActions() {
        if (nightActions == null) nightActions = new ConcurrentHashMap<>();
        return nightActions;
    }

    public void setNightActions(Map<String, Long> nightActions) {
        this.nightActions = nightActions == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(nightActions);
    }

    public List<Role> getFatcatAbsentVolunteerHintRoles() {
        if (fatcatAbsentVolunteerHintRoles == null) fatcatAbsentVolunteerHintRoles = new ArrayList<>();
        return fatcatAbsentVolunteerHintRoles;
    }

    public void setFatcatAbsentVolunteerHintRoles(List<Role> fatcatAbsentVolunteerHintRoles) {
        this.fatcatAbsentVolunteerHintRoles = fatcatAbsentVolunteerHintRoles == null ? new ArrayList<>() : fatcatAbsentVolunteerHintRoles;
    }

    public Map<Long, Role> getHighRabbitPerceivedRoles() {
        if (highRabbitPerceivedRoles == null) highRabbitPerceivedRoles = new ConcurrentHashMap<>();
        return highRabbitPerceivedRoles;
    }

    public void setHighRabbitPerceivedRoles(Map<Long, Role> highRabbitPerceivedRoles) {
        this.highRabbitPerceivedRoles = highRabbitPerceivedRoles == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(highRabbitPerceivedRoles);
    }

    public Map<Long, Integer> getDayVoteCounts() {
        if (dayVoteCounts == null) dayVoteCounts = new ConcurrentHashMap<>();
        return dayVoteCounts;
    }

    public void setDayVoteCounts(Map<Long, Integer> dayVoteCounts) {
        this.dayVoteCounts = dayVoteCounts == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(dayVoteCounts);
    }

    public Map<Long, Integer> getLastVoteCounts() {
        if (lastVoteCounts == null) lastVoteCounts = new ConcurrentHashMap<>();
        return lastVoteCounts;
    }

    public void setLastVoteCounts(Map<Long, Integer> lastVoteCounts) {
        this.lastVoteCounts = lastVoteCounts == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(lastVoteCounts);
    }

    public Map<Long, Integer> getDelayedDeathRounds() {
        if (delayedDeathRounds == null) delayedDeathRounds = new ConcurrentHashMap<>();
        return delayedDeathRounds;
    }

    public void setDelayedDeathRounds(Map<Long, Integer> delayedDeathRounds) {
        this.delayedDeathRounds = delayedDeathRounds == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(delayedDeathRounds);
    }

    public Map<Long, Integer> getMubaimuDoomRounds() {
        if (mubaimuDoomRounds == null) mubaimuDoomRounds = new ConcurrentHashMap<>();
        return mubaimuDoomRounds;
    }

    public void setMubaimuDoomRounds(Map<Long, Integer> mubaimuDoomRounds) {
        this.mubaimuDoomRounds = mubaimuDoomRounds == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(mubaimuDoomRounds);
    }

    public Map<Long, Integer> getBarkKingDoomRounds() {
        if (barkKingDoomRounds == null) barkKingDoomRounds = new ConcurrentHashMap<>();
        return barkKingDoomRounds;
    }

    public void setBarkKingDoomRounds(Map<Long, Integer> barkKingDoomRounds) {
        this.barkKingDoomRounds = barkKingDoomRounds == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(barkKingDoomRounds);
    }

    public Map<Long, Integer> getDrunkUntilRounds() {
        if (drunkUntilRounds == null) drunkUntilRounds = new ConcurrentHashMap<>();
        return drunkUntilRounds;
    }

    public void setDrunkUntilRounds(Map<Long, Integer> drunkUntilRounds) {
        this.drunkUntilRounds = drunkUntilRounds == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(drunkUntilRounds);
    }

    public Map<Long, Object> getPrivateMessages() {
        if (privateMessages == null) privateMessages = new ConcurrentHashMap<>();
        return privateMessages;
    }

    public void setPrivateMessages(Map<Long, Object> privateMessages) {
        this.privateMessages = privateMessages == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(privateMessages);
    }

    public Map<Long, Integer> getStrOriginalSeatNumbers() {
        if (strOriginalSeatNumbers == null) strOriginalSeatNumbers = new ConcurrentHashMap<>();
        return strOriginalSeatNumbers;
    }

    public void setStrOriginalSeatNumbers(Map<Long, Integer> strOriginalSeatNumbers) {
        this.strOriginalSeatNumbers = strOriginalSeatNumbers == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(strOriginalSeatNumbers);
    }

    public Map<Long, Integer> getStrTemporarySeatNumbers() {
        if (strTemporarySeatNumbers == null) strTemporarySeatNumbers = new ConcurrentHashMap<>();
        return strTemporarySeatNumbers;
    }

    public void setStrTemporarySeatNumbers(Map<Long, Integer> strTemporarySeatNumbers) {
        this.strTemporarySeatNumbers = strTemporarySeatNumbers == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(strTemporarySeatNumbers);
    }

    public Set<Integer> getStrSwappedSeatNumbers() {
        if (strSwappedSeatNumbers == null) strSwappedSeatNumbers = ConcurrentHashMap.newKeySet();
        return strSwappedSeatNumbers;
    }

    public void setStrSwappedSeatNumbers(Set<Integer> strSwappedSeatNumbers) {
        this.strSwappedSeatNumbers = ConcurrentHashMap.newKeySet();
        if (strSwappedSeatNumbers != null) this.strSwappedSeatNumbers.addAll(strSwappedSeatNumbers);
    }

    public Set<Long> getLastDayVoterIds() {
        if (lastDayVoterIds == null) lastDayVoterIds = ConcurrentHashMap.newKeySet();
        return lastDayVoterIds;
    }

    public void setLastDayVoterIds(Set<Long> lastDayVoterIds) {
        this.lastDayVoterIds = ConcurrentHashMap.newKeySet();
        if (lastDayVoterIds != null) this.lastDayVoterIds.addAll(lastDayVoterIds);
    }

    public Set<Long> getKbNominationTrapTriggeredIds() {
        if (kbNominationTrapTriggeredIds == null) kbNominationTrapTriggeredIds = ConcurrentHashMap.newKeySet();
        return kbNominationTrapTriggeredIds;
    }

    public void setKbNominationTrapTriggeredIds(Set<Long> kbNominationTrapTriggeredIds) {
        this.kbNominationTrapTriggeredIds = ConcurrentHashMap.newKeySet();
        if (kbNominationTrapTriggeredIds != null) this.kbNominationTrapTriggeredIds.addAll(kbNominationTrapTriggeredIds);
    }

    public Set<Long> getChenUsedPlayerIds() {
        if (chenUsedPlayerIds == null) chenUsedPlayerIds = ConcurrentHashMap.newKeySet();
        return chenUsedPlayerIds;
    }

    public void setChenUsedPlayerIds(Set<Long> chenUsedPlayerIds) {
        this.chenUsedPlayerIds = ConcurrentHashMap.newKeySet();
        if (chenUsedPlayerIds != null) this.chenUsedPlayerIds.addAll(chenUsedPlayerIds);
    }

    public Map<Long, Integer> getChenSkippedRounds() {
        if (chenSkippedRounds == null) chenSkippedRounds = new ConcurrentHashMap<>();
        return chenSkippedRounds;
    }

    public void setChenSkippedRounds(Map<Long, Integer> chenSkippedRounds) {
        this.chenSkippedRounds = chenSkippedRounds == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(chenSkippedRounds);
    }

    public Set<Long> getRatManCheckerIds() {
        if (ratManCheckerIds == null) ratManCheckerIds = ConcurrentHashMap.newKeySet();
        return ratManCheckerIds;
    }

    public void setRatManCheckerIds(Set<Long> ratManCheckerIds) {
        this.ratManCheckerIds = ConcurrentHashMap.newKeySet();
        if (ratManCheckerIds != null) this.ratManCheckerIds.addAll(ratManCheckerIds);
    }

    public Set<Long> getSaltedFishUsedPlayerIds() {
        if (saltedFishUsedPlayerIds == null) saltedFishUsedPlayerIds = ConcurrentHashMap.newKeySet();
        return saltedFishUsedPlayerIds;
    }

    public void setSaltedFishUsedPlayerIds(Set<Long> saltedFishUsedPlayerIds) {
        this.saltedFishUsedPlayerIds = ConcurrentHashMap.newKeySet();
        if (saltedFishUsedPlayerIds != null) this.saltedFishUsedPlayerIds.addAll(saltedFishUsedPlayerIds);
    }

    public Map<Long, Integer> getSaltedFishSkippedRounds() {
        if (saltedFishSkippedRounds == null) saltedFishSkippedRounds = new ConcurrentHashMap<>();
        return saltedFishSkippedRounds;
    }

    public void setSaltedFishSkippedRounds(Map<Long, Integer> saltedFishSkippedRounds) {
        this.saltedFishSkippedRounds = saltedFishSkippedRounds == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(saltedFishSkippedRounds);
    }

    public Set<Long> getFatcatKillBlockedPlayerIds() {
        if (fatcatKillBlockedPlayerIds == null) fatcatKillBlockedPlayerIds = ConcurrentHashMap.newKeySet();
        return fatcatKillBlockedPlayerIds;
    }

    public void setFatcatKillBlockedPlayerIds(Set<Long> fatcatKillBlockedPlayerIds) {
        this.fatcatKillBlockedPlayerIds = ConcurrentHashMap.newKeySet();
        if (fatcatKillBlockedPlayerIds != null) this.fatcatKillBlockedPlayerIds.addAll(fatcatKillBlockedPlayerIds);
    }

    public Set<Long> getNangongUsedPlayerIds() {
        if (nangongUsedPlayerIds == null) nangongUsedPlayerIds = ConcurrentHashMap.newKeySet();
        return nangongUsedPlayerIds;
    }

    public void setNangongUsedPlayerIds(Set<Long> nangongUsedPlayerIds) {
        this.nangongUsedPlayerIds = ConcurrentHashMap.newKeySet();
        if (nangongUsedPlayerIds != null) this.nangongUsedPlayerIds.addAll(nangongUsedPlayerIds);
    }

    public Set<Long> getFinalVoteEligiblePlayerIds() {
        if (finalVoteEligiblePlayerIds == null) finalVoteEligiblePlayerIds = ConcurrentHashMap.newKeySet();
        return finalVoteEligiblePlayerIds;
    }

    public void setFinalVoteEligiblePlayerIds(Set<Long> finalVoteEligiblePlayerIds) {
        this.finalVoteEligiblePlayerIds = ConcurrentHashMap.newKeySet();
        if (finalVoteEligiblePlayerIds != null) this.finalVoteEligiblePlayerIds.addAll(finalVoteEligiblePlayerIds);
    }

    public Set<Long> getFatcatKilledPlayerIds() {
        if (fatcatKilledPlayerIds == null) fatcatKilledPlayerIds = ConcurrentHashMap.newKeySet();
        return fatcatKilledPlayerIds;
    }

    public void setFatcatKilledPlayerIds(Set<Long> fatcatKilledPlayerIds) {
        this.fatcatKilledPlayerIds = ConcurrentHashMap.newKeySet();
        if (fatcatKilledPlayerIds != null) this.fatcatKilledPlayerIds.addAll(fatcatKilledPlayerIds);
    }

    public List<com.fatcatkill.model.GameLogEntry> getLogs() {
        if (logs == null) logs = new ArrayList<>();
        return logs;
    }

    public void setLogs(List<com.fatcatkill.model.GameLogEntry> logs) {
        this.logs = logs == null ? new ArrayList<>() : logs;
    }
    public void addLog(com.fatcatkill.model.GameLogEntry entry) {
        if (logs == null) logs = new ArrayList<>();
        logs.add(entry);
    }
}
