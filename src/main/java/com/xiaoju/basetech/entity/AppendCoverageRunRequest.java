package com.xiaoju.basetech.entity;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class AppendCoverageRunRequest {

    private String runId;
    @NotBlank(message = "commitId不能为空")
    private String commitId;
    private String commitMessage;
    private String execObjectKey;
    @NotBlank(message = "xmlObjectKey不能为空")
    private String xmlObjectKey;
}
