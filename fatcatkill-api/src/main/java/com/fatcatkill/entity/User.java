package com.fatcatkill.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "users")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "games_played")
    private Integer gamesPlayed = 0;

    @Column(name = "games_won")
    private Integer gamesWon = 0;

    @Column(name = "session_token", length = 64)
    private String sessionToken;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}