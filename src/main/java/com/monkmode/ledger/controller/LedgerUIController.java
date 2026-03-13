package com.monkmode.ledger.controller;

import com.monkmode.ledger.enums.BlockStatus;
import com.monkmode.ledger.model.TimeBlock;
import com.monkmode.ledger.service.TimeBlockService;
import com.monkmode.ledger.repository.TimeBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class LedgerUIController {

    private final TimeBlockService timeBlockService;
    private final TimeBlockRepository timeBlockRepository; // Injecting just to read the list quickly
    private final String USER_ID = "monk-user-001";

    @GetMapping("/")
    public String getDashboard(Model model) {
        LocalDate today = LocalDate.now();
        populateModel(model, today);
        return "dashboard";
    }

    // Endpoint to handle the Nightly Orchestration form
    @PostMapping("/ui/blocks")
    public String createBlock(
            @RequestParam String category,
            @RequestParam String targetDate, // "TODAY" or "TOMORROW"
            @RequestParam String startTime,  // "09:00"
            @RequestParam String endTime,    // "11:00"
            @RequestParam String reason,
            Model model) {

        LocalDate date = targetDate.equals("TOMORROW") ? LocalDate.now().plusDays(1) : LocalDate.now();

        // Stitch the date and the time together in the backend
        LocalDateTime plannedStart = LocalDateTime.of(date, LocalTime.parse(startTime));
        LocalDateTime plannedEnd = LocalDateTime.of(date, LocalTime.parse(endTime));

        TimeBlock block = TimeBlock.builder()
                .userId(USER_ID)
                .category(category.toUpperCase()) // Keep it clean and uniform
                .status(BlockStatus.PLANNED)
                .plannedStart(plannedStart)
                .plannedEnd(plannedEnd)
                .reason(reason)
                .build();

        timeBlockService.createBlock(block);

        populateModel(model, LocalDate.now());
        return "dashboard :: block-list-fragment";
    }

    // Endpoint to handle the Ripple Shift form
    @PostMapping("/ui/blocks/shift")
    public String handleShift(
            @RequestParam String startTime, // e.g., "09:00"
            @RequestParam int offsetMinutes,
            Model model) {

        LocalDate today = LocalDate.now();
        LocalDateTime shiftStart = LocalDateTime.of(today, LocalTime.parse(startTime));

        timeBlockService.applyRippleShift(USER_ID, shiftStart, offsetMinutes);

        populateModel(model, today);
        return "dashboard :: block-list-fragment";
    }

    private void populateModel(Model model, LocalDate date) {
        double efficiencyScore = timeBlockService.calculateDailyEfficiency(USER_ID, date);
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        List<TimeBlock> blocks = timeBlockRepository.findBlocksByDay(USER_ID, startOfDay, endOfDay);

        // Inside populateModel(), fetch distinct strings instead of the Enum
        List<String> activeCategories = timeBlockRepository.findDistinctCategories(USER_ID);

        // Fallback defaults if the database is completely empty
        if (activeCategories.isEmpty()) {
            activeCategories = List.of("DEEP_WORK", "DSA", "PROJECT", "ERRAND", "WASTED", "REST", "SLEEP");
        }


        model.addAttribute("efficiencyScore", efficiencyScore);
        model.addAttribute("currentDate", date.toString());
        model.addAttribute("blocks", blocks);
        model.addAttribute("categories", activeCategories);
        // Pass enum values to populate the dropdown

    }
}