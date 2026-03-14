package com.monkmode.ledger.service;

import com.monkmode.ledger.enums.BlockStatus;
import com.monkmode.ledger.exception.LedgerValidationException;
import com.monkmode.ledger.model.TimeBlock;
import com.monkmode.ledger.repository.TimeBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TimeBlockService {

    private final TimeBlockRepository timeBlockRepository;

    public TimeBlock createBlock(TimeBlock block) {
        if (block.getPlannedEnd().isBefore(block.getPlannedStart()) ||
                block.getPlannedEnd().isEqual(block.getPlannedStart())) {
            throw new LedgerValidationException("End time must be strictly after start time.");
        }

        boolean hasOverlap = timeBlockRepository.existsOverlappingBlock(
                block.getUserId(), block.getPlannedStart(), block.getPlannedEnd());

        if (hasOverlap) {
            throw new LedgerValidationException("This block overlaps with an existing schedule.");
        }

        return timeBlockRepository.save(block);
    }

    @Transactional
    public int applyRippleShift(String userId, LocalDateTime startTime, int offsetMinutes) {
        return timeBlockRepository.shiftPlannedBlocks(userId, startTime, offsetMinutes);
    }

    @Transactional
    public TimeBlock updateBlockStatus(Long id, BlockStatus status) {
        TimeBlock block = timeBlockRepository.findById(id)
                .orElseThrow(() -> new LedgerValidationException("Block not found."));

        block.setStatus(status);
        return timeBlockRepository.save(block);
    }

    // NEW METHOD: Deletes a block and returns it to the controller
    @Transactional
    public TimeBlock deleteBlock(Long id) {
        TimeBlock block = timeBlockRepository.findById(id)
                .orElseThrow(() -> new LedgerValidationException("Block not found."));

        timeBlockRepository.delete(block);
        return block;
    }

    public double calculateDailyEfficiency(String userId, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        List<TimeBlock> dailyBlocks = timeBlockRepository.findBlocksByDay(userId, startOfDay, endOfDay);

        long totalSleepMinutes = 0;
        long totalWastedMinutes = 0;

        for (TimeBlock block : dailyBlocks) {
            LocalDateTime start = block.getActualStart() != null ? block.getActualStart() : block.getPlannedStart();
            LocalDateTime end = block.getActualEnd() != null ? block.getActualEnd() : block.getPlannedEnd();

            long duration = Duration.between(start, end).toMinutes();

            if ("SLEEP".equalsIgnoreCase(block.getCategory())) {
                totalSleepMinutes += duration;
            } else if ("WASTED".equalsIgnoreCase(block.getCategory()) || block.getStatus() == BlockStatus.WASTED) {
                totalWastedMinutes += duration;
            }
        }

        long totalWakingMinutes = (24 * 60) - totalSleepMinutes;

        if (totalWakingMinutes <= 0) return 0.0;

        double efficiency = (double) (totalWakingMinutes - totalWastedMinutes) / totalWakingMinutes;

        return Math.round((efficiency * 100) * 100.0) / 100.0;
    }
}