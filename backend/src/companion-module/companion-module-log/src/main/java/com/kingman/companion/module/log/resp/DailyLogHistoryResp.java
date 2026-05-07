package com.kingman.companion.module.log.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class DailyLogHistoryResp {

    private String logId;
    private LocalDate logDate;
    private int emotionScore;
    private List<String> emotionLabels;
    private boolean contactedEx;
    private String contactOutcome;
    private String notes;
    private String aiSuggestion;
}
