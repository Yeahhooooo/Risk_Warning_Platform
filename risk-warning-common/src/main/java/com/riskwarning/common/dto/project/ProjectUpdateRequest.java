package com.riskwarning.common.dto.project;

import lombok.Data;
import java.time.LocalDate;

/** Editable project metadata; ownership and assessment state are maintained by the system. */
@Data
public class ProjectUpdateRequest {
    private String name;
    private String type;
    private String description;
    private LocalDate startDate;
    private LocalDate plannedCompletionDate;
    private String industry;
    private String region;
    private String orientedUser;
}
