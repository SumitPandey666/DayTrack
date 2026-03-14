package com.monkmode.ledger.controller;

import com.monkmode.ledger.enums.BlockStatus;
import com.monkmode.ledger.model.TimeBlock;
import com.monkmode.ledger.service.TimeBlockService;
import com.monkmode.ledger.repository.TimeBlockRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class LedgerUIController {

    private final TimeBlockService timeBlockService;
    private final TimeBlockRepository timeBlockRepository;
    private final HttpServletRequest request;
    private final String USER_ID = "monk-user-001";

    @GetMapping("/")
    public String getDashboard(Model model) {
        LocalDate today = LocalDate.now();
        populateModel(model, today);
        return "dashboard";
    }

    @PostMapping("/ui/blocks")
    public String createBlock(
            @RequestParam String category,
            @RequestParam String dayType,
            @RequestParam(required = false) String customDate,
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam String reason,
            Model model) {

        LocalDate date;
        if ("TODAY".equals(dayType)) {
            date = LocalDate.now();
        } else if ("TOMORROW".equals(dayType)) {
            date = LocalDate.now().plusDays(1);
        } else {
            date = LocalDate.parse(customDate);
        }

        LocalDateTime plannedStart = LocalDateTime.of(date, LocalTime.parse(startTime));
        LocalDateTime plannedEnd = LocalDateTime.of(date, LocalTime.parse(endTime));

        TimeBlock block = TimeBlock.builder()
                .userId(USER_ID)
                .category(category.toUpperCase())
                .status(BlockStatus.PLANNED)
                .plannedStart(plannedStart)
                .plannedEnd(plannedEnd)
                .reason(reason)
                .build();

        timeBlockService.createBlock(block);

        populateModel(model, date);
        model.addAttribute("toastMessage", "Block securely locked for " + date.toString());

        return "dashboard :: protocol-fragment";
    }

    @PostMapping("/ui/blocks/shift")
    public String handleShift(
            @RequestParam String startTime,
            @RequestParam int offsetMinutes,
            Model model) {

        LocalDate today = LocalDate.now();
        LocalDateTime shiftStart = LocalDateTime.of(today, LocalTime.parse(startTime));

        timeBlockService.applyRippleShift(USER_ID, shiftStart, offsetMinutes);

        populateModel(model, today);
        return "dashboard :: protocol-fragment";
    }

    @GetMapping("/ui/protocol")
    public String getProtocolForDate(@RequestParam("date") String dateString, Model model) {
        LocalDate targetDate = LocalDate.parse(dateString);
        populateModel(model, targetDate);
        return "dashboard :: protocol-fragment";
    }

    @PostMapping("/ui/blocks/{id}/status")
    public String updateBlockStatus(
            @PathVariable Long id,
            @RequestParam("status") String status,
            Model model) {

        TimeBlock updatedBlock = timeBlockService.updateBlockStatus(id, BlockStatus.valueOf(status));
        LocalDate targetDate = updatedBlock.getPlannedStart().toLocalDate();

        populateModel(model, targetDate);
        model.addAttribute("toastMessage", "Protocol updated to " + status);

        return "dashboard :: protocol-fragment";
    }

    @DeleteMapping("/ui/blocks/{id}")
    public String deleteBlock(@PathVariable Long id, Model model) {

        TimeBlock deletedBlock = timeBlockService.deleteBlock(id);
        LocalDate targetDate = deletedBlock.getPlannedStart().toLocalDate();

        populateModel(model, targetDate);
        model.addAttribute("toastMessage", "Block permanently deleted.");

        return "dashboard :: protocol-fragment";
    }

    @PostMapping("/ui/blocks/copy")
    public String copyProtocol(
            @RequestParam String sourceDate,
            @RequestParam String targetDate,
            Model model) {

        LocalDate source = LocalDate.parse(sourceDate);
        LocalDate target = LocalDate.parse(targetDate);

        int copiedCount = timeBlockService.copyProtocol(USER_ID, source, target);

        populateModel(model, target);
        model.addAttribute("toastMessage", "Successfully cloned " + copiedCount + " blocks to " + targetDate);

        return "dashboard :: protocol-fragment";
    }

    private void populateModel(Model model, LocalDate date) {
        double efficiencyScore = timeBlockService.calculateDailyEfficiency(USER_ID, date);
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        List<TimeBlock> blocks = timeBlockRepository.findBlocksByDay(USER_ID, startOfDay, endOfDay);
        List<String> activeCategories = timeBlockRepository.findDistinctCategories(USER_ID);

        if (activeCategories.isEmpty()) {
            activeCategories = List.of("DEEP_WORK", "DSA", "PROJECT", "ERRAND", "WASTED", "REST", "SLEEP");
        }

        model.addAttribute("efficiencyScore", efficiencyScore);
        model.addAttribute("currentDate", date.toString());
        model.addAttribute("blocks", blocks);
        model.addAttribute("categories", activeCategories);

        boolean isHtmxRequest = request.getHeader("HX-Request") != null;
        model.addAttribute("isHtmxRequest", isHtmxRequest);

        // NEW: Check if the viewed date is in the past
        model.addAttribute("isPastDate", date.isBefore(LocalDate.now()));
    }
}