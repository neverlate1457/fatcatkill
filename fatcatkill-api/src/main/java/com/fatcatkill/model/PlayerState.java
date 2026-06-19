package com.fatcatkill.model;

import com.fatcatkill.enums.Role;
import lombok.Data;

@Data
public class PlayerState {
    private Long userId;
    private String username;
    private Integer seatNumber; 
    private Role role;          
    private boolean isAlive = true; 
    
    private Long votedTargetId; 
    private boolean voteConfirmed = false;
}
