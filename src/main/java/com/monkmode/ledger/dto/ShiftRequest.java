package com.monkmode.ledger.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ShiftRequest {
    private LocalDateTime startTime;
    private int offsetMinutes;
}
