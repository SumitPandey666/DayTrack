package com.monkmode.ledger.repository;

import com.monkmode.ledger.model.TimeBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TimeBlockRepository extends JpaRepository<TimeBlock, Long> {

    @Modifying
    @Query(value = """
            UPDATE time_blocks 
            SET planned_start = DATE_ADD(planned_start, INTERVAL :offsetMinutes MINUTE), 
                planned_end = DATE_ADD(planned_end, INTERVAL :offsetMinutes MINUTE) 
            WHERE status = 'PLANNED' 
              AND user_id = :userId 
              AND planned_start >= :startTime
            """, nativeQuery = true)
    int shiftPlannedBlocks(@Param("userId") String userId,
                           @Param("startTime") LocalDateTime startTime,
                           @Param("offsetMinutes") int offsetMinutes);

    // Checks if any block overlaps with the requested time window
    @Query("""
            SELECT COUNT(t) > 0 FROM TimeBlock t 
            WHERE t.userId = :userId 
              AND t.plannedStart < :end 
              AND t.plannedEnd > :start
            """)
    boolean existsOverlappingBlock(@Param("userId") String userId,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);

    // Fetches all blocks for a specific day to calculate efficiency
    @Query("""
            SELECT t FROM TimeBlock t 
            WHERE t.userId = :userId 
              AND t.plannedStart >= :dayStart 
              AND t.plannedStart < :dayEnd
            """)
    List<TimeBlock> findBlocksByDay(@Param("userId") String userId,
                                    @Param("dayStart") LocalDateTime dayStart,
                                    @Param("dayEnd") LocalDateTime dayEnd);

    @Query("SELECT DISTINCT t.category FROM TimeBlock t WHERE t.userId = :userId")
    List<String> findDistinctCategories(@Param("userId") String userId);
}
