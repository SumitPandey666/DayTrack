package com.monkmode.ledger.controller;

import com.monkmode.ledger.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final String USER_ID = "monk-user-001";

    @GetMapping("/analytics")
    public String getAnalyticsDashboard(Model model) {
        LocalDate today = LocalDate.now();

        LocalDate startOfWeek = today.with(ChronoField.DAY_OF_WEEK, 1);
        LocalDate endOfWeek = today.with(ChronoField.DAY_OF_WEEK, 7);
        LocalDate startOfMonth = today.withDayOfMonth(1);
        LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());

        double weeklyEfficiency = analyticsService.calculateEfficiencyBetween(USER_ID, startOfWeek, endOfWeek);
        double monthlyEfficiency = analyticsService.calculateEfficiencyBetween(USER_ID, startOfMonth, endOfMonth);

        // Fetch Historical Trends (Last 5 weeks, Last 6 months)
        List<AnalyticsService.TrendData> weeklyTrend = analyticsService.getWeeklyTrend(USER_ID, 5);
        List<AnalyticsService.TrendData> monthlyTrend = analyticsService.getMonthlyTrend(USER_ID, 6);

        String[] zones = analyticsService.findGoldenAndDangerZones(USER_ID, today.minusDays(30), today);

        model.addAttribute("weeklyEfficiency", weeklyEfficiency);
        model.addAttribute("monthlyEfficiency", monthlyEfficiency);
        model.addAttribute("weeklyTrend", weeklyTrend);
        model.addAttribute("monthlyTrend", monthlyTrend);
        model.addAttribute("goldenZone", zones[0]);
        model.addAttribute("dangerZone", zones[1]);

        return "analytics";
    }
}