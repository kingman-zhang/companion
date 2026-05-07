package com.kingman.companion.module.log.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class DailyLogReq {

    @NotNull
    @Min(1) @Max(10)
    private Integer emotionScore;

    @NotEmpty
    private List<String> emotionLabels;

    private boolean contactedEx;

    /** 仅 contactedEx=true 时有意义 */
    private String contactOutcome;

    @Size(max = 500)
    private String notes;
}
