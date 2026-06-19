package com.fatcatkill.repository;

import com.fatcatkill.entity.PersistedGameState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameStateRepository extends JpaRepository<PersistedGameState, String> {
}
