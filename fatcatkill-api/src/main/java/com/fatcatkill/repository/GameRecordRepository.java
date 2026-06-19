package com.fatcatkill.repository;

import com.fatcatkill.entity.GameRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameRecordRepository extends JpaRepository<GameRecord, Long> {
    boolean existsByGameId(String gameId);
    Optional<GameRecord> findByGameId(String gameId);
    long countByGameId(String gameId);
}