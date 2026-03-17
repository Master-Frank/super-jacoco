package com.xiaoju.basetech.entity;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class CreateCoverageSetRequest {

    private String coverageSetId;
    private String campaignId;
    @NotBlank(message = "gitUrl不能为空")
    private String gitUrl;
    private String repoLocalPath;
    @NotBlank(message = "branch不能为空")
    private String branch;
    @NotBlank(message = "type不能为空")
    private String type;
    @NotBlank(message = "fromType不能为空")
    private String fromType;
}
