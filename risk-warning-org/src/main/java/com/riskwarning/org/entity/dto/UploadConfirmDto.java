package com.riskwarning.org.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadConfirmDto implements Serializable {

    private static final long serialVersionUID = 1123121L;

    private Long projectId;

    private Integer retryCount;

}
