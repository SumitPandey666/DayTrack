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
import java.time.LocalTime;
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

        long totalWakingMinutes = 0;
        long totalCompletedMinutes = 0;

        for (TimeBlock block : dailyBlocks) {
            // Use actual times if executed, otherwise fallback to planned times
            LocalDateTime start = block.getActualStart() != null ? block.getActualStart() : block.getPlannedStart();
            LocalDateTime end = block.getActualEnd() != null ? block.getActualEnd() : block.getPlannedEnd();

            long duration = Duration.between(start, end).toMinutes();

            // 1. Sleep doesn't count toward or against your waking efficiency
            if ("SLEEP".equalsIgnoreCase(block.getCategory())) {
                continue;
            }

            // 2. Every waking block you've logged adds to the total potential of the day
            totalWakingMinutes += duration;

            // 3. You only get points if you actually executed and completed it
            if (block.getStatus() == BlockStatus.COMPLETED) {
                totalCompletedMinutes += duration;
            }
        }

        // Prevent division by zero if the day is completely empty
        if (totalWakingMinutes == 0) return 0.0;

        // Calculate the progress
        double efficiency = (double) totalCompletedMinutes / totalWakingMinutes;

        // Return as a clean percentage rounded to 2 decimal places
        return Math.round((efficiency * 100) * 100.0) / 100.0;
    }

    // NEW METHOD: The Protocol Replication Engine
    @Transactional
    public int copyProtocol(String userId, LocalDate sourceDate, LocalDate targetDate) {
        LocalDateTime sourceStartOfDay = sourceDate.atStartOfDay();
        LocalDateTime sourceEndOfDay = sourceStartOfDay.plusDays(1);

        // Fetch all blocks from the source date
        List<TimeBlock> sourceBlocks = timeBlockRepository.findBlocksByDay(userId, sourceStartOfDay, sourceEndOfDay);

        if (sourceBlocks.isEmpty()) {
            throw new LedgerValidationException("No protocol found on the source date to copy.");
        }

        int copiedCount = 0;
        for (TimeBlock original : sourceBlocks) {
            // Extract just the time (e.g., 05:00 AM)
            LocalTime startTime = original.getPlannedStart().toLocalTime();
            LocalTime endTime = original.getPlannedEnd().toLocalTime();

            // Stitch it to the new target date
            LocalDateTime newStart = LocalDateTime.of(targetDate, startTime);
            LocalDateTime newEnd = LocalDateTime.of(targetDate, endTime);

            // Build a fresh clone
            TimeBlock newBlock = TimeBlock.builder()
                    .userId(userId)
                    .category(original.getCategory())
                    .status(BlockStatus.PLANNED) // Strictly reset to PLANNED
                    .plannedStart(newStart)
                    .plannedEnd(newEnd)
                    .reason(original.getReason())
                    .build();

            // Use our existing createBlock method so it still enforces overlap protections!
            createBlock(newBlock);
            copiedCount++;
        }

        return copiedCount;
    }
}