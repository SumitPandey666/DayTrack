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

    // THE GUARDRAIL: Throws an exception if you try to touch yesterday
    private void enforceNotPast(LocalDate date) {
        if (date.isBefore(LocalDate.now())) {
            throw new LedgerValidationException("History is permanently locked. You cannot alter the past.");
        }
    }

    public TimeBlock createBlock(TimeBlock block) {
        enforceNotPast(block.getPlannedStart().toLocalDate());

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
        enforceNotPast(startTime.toLocalDate());
        return timeBlockRepository.shiftPlannedBlocks(userId, startTime, offsetMinutes);
    }

    @Transactional
    public TimeBlock updateBlockStatus(Long id, BlockStatus status) {
        TimeBlock block = timeBlockRepository.findById(id)
                .orElseThrow(() -> new LedgerValidationException("Block not found."));

        enforceNotPast(block.getPlannedStart().toLocalDate());

        block.setStatus(status);
        return timeBlockRepository.save(block);
    }

    @Transactional
    public TimeBlock deleteBlock(Long id) {
        TimeBlock block = timeBlockRepository.findById(id)
                .orElseThrow(() -> new LedgerValidationException("Block not found."));

        enforceNotPast(block.getPlannedStart().toLocalDate());

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
            LocalDateTime start = block.getActualStart() != null ? block.getActualStart() : block.getPlannedStart();
            LocalDateTime end = block.getActualEnd() != null ? block.getActualEnd() : block.getPlannedEnd();

            long duration = Duration.between(start, end).toMinutes();

            if ("SLEEP".equalsIgnoreCase(block.getCategory())) {
                continue;
            }

            totalWakingMinutes += duration;

            if (block.getStatus() == BlockStatus.COMPLETED) {
                totalCompletedMinutes += duration;
            }
        }

        if (totalWakingMinutes == 0) return 0.0;

        double efficiency = (double) totalCompletedMinutes / totalWakingMinutes;

        return Math.round((efficiency * 100) * 100.0) / 100.0;
    }

    @Transactional
    public int copyProtocol(String userId, LocalDate sourceDate, LocalDate targetDate) {
        enforceNotPast(targetDate); // Can copy FROM the past, but not paste INTO the past

        LocalDateTime sourceStartOfDay = sourceDate.atStartOfDay();
        LocalDateTime sourceEndOfDay = sourceStartOfDay.plusDays(1);

        List<TimeBlock> sourceBlocks = timeBlockRepository.findBlocksByDay(userId, sourceStartOfDay, sourceEndOfDay);

        if (sourceBlocks.isEmpty()) {
            throw new LedgerValidationException("No protocol found on the source date to copy.");
        }

        int copiedCount = 0;
        for (TimeBlock original : sourceBlocks) {
            LocalTime startTime = original.getPlannedStart().toLocalTime();
            LocalTime endTime = original.getPlannedEnd().toLocalTime();

            LocalDateTime newStart = LocalDateTime.of(targetDate, startTime);
            LocalDateTime newEnd = LocalDateTime.of(targetDate, endTime);

            TimeBlock newBlock = TimeBlock.builder()
                    .userId(userId)
                    .category(original.getCategory())
                    .status(BlockStatus.PLANNED)
                    .plannedStart(newStart)
                    .plannedEnd(newEnd)
                    .reason(original.getReason())
                    .build();

            createBlock(newBlock);
            copiedCount++;
        }

        return copiedCount;
    }
}