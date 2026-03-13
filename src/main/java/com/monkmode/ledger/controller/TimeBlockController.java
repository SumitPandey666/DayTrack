package com.monkmode.ledger.controller;

import com.monkmode.ledger.dto.ShiftRequest;
import com.monkmode.ledger.model.TimeBlock;
import com.monkmode.ledger.service.TimeBlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/ledger/blocks")
@RequiredArgsConstructor
public class TimeBlockController {

    private final TimeBlockService timeBlockService;

    // 1. Create a block to test with
    @PostMapping
    public ResponseEntity<TimeBlock> createBlock(@RequestBody TimeBlock block) {
        // Hardcoding a dummy user ID just to get the flow working tonight
        block.setUserId("monk-user-001");
        return ResponseEntity.ok(timeBlockService.createBlock(block));
    }

    // 2. The Ripple Shift Engine
    @PostMapping("/shift")
    public ResponseEntity<String> shiftBlocks(
            @RequestHeader(value = "X-User-Id", defaultValue = "monk-user-001") String userId,
            @RequestBody ShiftRequest request) {

        int updatedCount = timeBlockService.applyRippleShift(
                userId,
                request.getStartTime(),
                request.getOffsetMinutes()
        );

        return ResponseEntity.ok("Successfully shifted " + updatedCount + " planned blocks.");
    }

    // 3. The Dopamine Metric (Efficiency Score)
    @GetMapping("/efficiency")
    public ResponseEntity<Double> getDailyEfficiency(
            @RequestHeader(value = "X-User-Id", defaultValue = "monk-user-001") String userId,
            @RequestParam("date") String dateString) {

        // Expecting date in format YYYY-MM-DD
        LocalDate date = LocalDate.parse(dateString);
        double score = timeBlockService.calculateDailyEfficiency(userId, date);

        return ResponseEntity.ok(score);
    }
}
