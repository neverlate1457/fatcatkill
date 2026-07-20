package com.fatcatkill.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fatcatkill.enums.Role;
import lombok.Data;

@Data
public class PlayerState {
    private Long userId;
    private String username;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Long accountId;
    private boolean bot = false;
    private Integer seatNumber; 
    private Role role;          
    private boolean isAlive = true; 
    
    private Long votedTargetId; 
    private boolean voteConfirmed = false;
}
