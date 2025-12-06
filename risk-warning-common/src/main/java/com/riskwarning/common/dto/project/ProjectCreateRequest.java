package com.riskwarning.common.dto.project;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectCreateRequest {
    private Long enterpriseId;
    private String name;
    private String type;
    private String description;
    private LocalDate startDate;
    private LocalDate plannedCompletionDate;
    private String industry;
    private String region;
    private String orientedUser;

}
