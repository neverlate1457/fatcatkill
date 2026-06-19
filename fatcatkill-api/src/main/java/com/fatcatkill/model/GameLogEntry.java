package com.fatcatkill.model;

public class GameLogEntry {
    private String timestamp;
    private Long playerId;
    private String username;
    private String role;
    private String actionType;
    private Long targetId;
    private String targetName;
    private Long targetId2;
    private String targetName2;
    private String message;

    public GameLogEntry() {}

    public GameLogEntry(String timestamp, Long playerId, String username, String role, String actionType, Long targetId, Long targetId2, String message) {
        this.timestamp = timestamp;
        this.playerId = playerId;
        this.username = username;
        this.role = role;
        this.actionType = actionType;
        this.targetId = targetId;
        this.targetId2 = targetId2;
        this.message = message;
    }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }

    public Long getTargetId2() { return targetId2; }
    public void setTargetId2(Long targetId2) { this.targetId2 = targetId2; }

    public String getTargetName2() { return targetName2; }
    public void setTargetName2(String targetName2) { this.targetName2 = targetName2; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
