package com.monkmode.ledger.service;

import com.monkmode.ledger.enums.BlockStatus;
import com.monkmode.ledger.model.TimeBlock;
import com.monkmode.ledger.repository.TimeBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final TimeBlockRepository timeBlockRepository;

    // Simple container to hold historical data for the UI
    public static class TrendData {
        private final String period;
        private final double efficiency;

        public TrendData(String period, double efficiency) {
            this.period = period;
            this.efficiency = efficiency;
        }
        public String getPeriod() { return period; }
        public double getEfficiency() { return efficiency; }
    }

    public double calculateEfficiencyBetween(String userId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        List<TimeBlock> blocks = timeBlockRepository.findBlocksBetween(userId, start, end);
        return computeEfficiency(blocks);
    }

    // NEW: Calculate the last N weeks of efficiency
    public List<TrendData> getWeeklyTrend(String userId, int weeksBack) {
        List<TrendData> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd");

        for (int i = 0; i < weeksBack; i++) {
            LocalDate startOfWeek = today.minusWeeks(i).with(ChronoField.DAY_OF_WEEK, 1);
            LocalDate endOfWeek = startOfWeek.plusDays(6);

            double eff = calculateEfficiencyBetween(userId, startOfWeek, endOfWeek);
            String label = startOfWeek.format(formatter) + " - " + endOfWeek.format(formatter);

            trend.add(new TrendData(label, eff));
        }
        return trend; // Returns from newest week to oldest
    }

    // NEW: Calculate the last N months of efficiency
    public List<TrendData> getMonthlyTrend(String userId, int monthsBack) {
        List<TrendData> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM yyyy");

        for (int i = 0; i < monthsBack; i++) {
            LocalDate startOfMonth = today.minusMonths(i).withDayOfMonth(1);
            LocalDate endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth());

            double eff = calculateEfficiencyBetween(userId, startOfMonth, endOfMonth);
            String label = startOfMonth.format(formatter);

            trend.add(new TrendData(label, eff));
        }
        return trend; // Returns from newest month to oldest
    }

    private double computeEfficiency(List<TimeBlock> blocks) {
        long totalWakingMinutes = 0;
        long totalCompletedMinutes = 0;

        for (TimeBlock block : blocks) {
            long duration = Duration.between(block.getPlannedStart(), block.getPlannedEnd()).toMinutes();
            if ("SLEEP".equalsIgnoreCase(block.getCategory())) continue;

            totalWakingMinutes += duration;
            if (block.getStatus() == BlockStatus.COMPLETED) {
                totalCompletedMinutes += duration;
            }
        }

        if (totalWakingMinutes == 0) return 0.0;
        double efficiency = (double) totalCompletedMinutes / totalWakingMinutes;
        return Math.round((efficiency * 100) * 100.0) / 100.0;
    }

    public String[] findGoldenAndDangerZones(String userId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        List<TimeBlock> blocks = timeBlockRepository.findBlocksBetween(userId, start, end);

        int[] completedMinutesByHour = new int[24];
        int[] wastedMinutesByHour = new int[24];

        for (TimeBlock block : blocks) {
            int hourOfDay = block.getPlannedStart().getHour();
            int duration = (int) Duration.between(block.getPlannedStart(), block.getPlannedEnd()).toMinutes();

            if (block.getStatus() == BlockStatus.COMPLETED && !"SLEEP".equalsIgnoreCase(block.getCategory())) {
                completedMinutesByHour[hourOfDay] += duration;
            } else if (block.getStatus() == BlockStatus.WASTED || "WASTED".equalsIgnoreCase(block.getCategory())) {
                wastedMinutesByHour[hourOfDay] += duration;
            }
        }

        int maxCompleted = -1, maxWasted = -1;
        int bestStartHour = 0, worstStartHour = 0;

        for (int i = 0; i <= 21; i++) {
            int currentCompleted = completedMinutesByHour[i] + completedMinutesByHour[i+1] + completedMinutesByHour[i+2];
            int currentWasted = wastedMinutesByHour[i] + wastedMinutesByHour[i+1] + wastedMinutesByHour[i+2];

            if (currentCompleted > maxCompleted) {
                maxCompleted = currentCompleted;
                bestStartHour = i;
            }
            if (currentWasted > maxWasted) {
                maxWasted = currentWasted;
                worstStartHour = i;
            }
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm a");
        String goldenZone = maxCompleted > 0
                ? LocalTime.of(bestStartHour, 0).format(formatter) + " - " + LocalTime.of(bestStartHour + 3, 0).format(formatter)
                : "Insufficient Data";

        String dangerZone = maxWasted > 0
                ? LocalTime.of(worstStartHour, 0).format(formatter) + " - " + LocalTime.of(worstStartHour + 3, 0).format(formatter)
                : "No Wasted Time!";

        return new String[]{goldenZone, dangerZone};
    }
}