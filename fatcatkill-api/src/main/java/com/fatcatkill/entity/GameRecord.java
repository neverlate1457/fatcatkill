package com.fatcatkill.entity;

import com.fatcatkill.enums.Camp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "game_records")
public class GameRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", unique = true, length = 36)
    private String gameId;

    @Column(name = "room_id", nullable = false)
    private String roomId;

    @Column(name = "game_mode")
    private String gameMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "winner_camp", nullable = false)
    private Camp winnerCamp;

    @Column(name = "rounds_played")
    private Integer roundsPlayed;

    @Column(name = "player_count")
    private Integer playerCount;

    @Column(name = "participant_account_ids", columnDefinition = "TEXT")
    private String participantAccountIds;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime = LocalDateTime.now();

    @Lob
    @Column(name = "final_state_json", columnDefinition = "TEXT")
    private String finalStateJson;
}
