package com.fatcatkill.dto;

import lombok.Data;

@Data
public class GameActionRequest {
    private String roomId;
    private Long playerId;
    private Long targetId;
    private String actionType;
}