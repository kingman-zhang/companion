package com.kingman.companion.module.log.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserFeedbackReq {

    @NotBlank
    @Pattern(regexp = "BUG|SUGGESTION|COOPERATION")
    private String type;

    @NotBlank
    @Size(max = 1000)
    private String content;

    @Size(max = 100)
    private String contact;

    @Size(max = 120)
    private String sourcePage;
}
