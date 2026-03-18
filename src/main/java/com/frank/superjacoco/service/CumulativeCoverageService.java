package com.frank.superjacoco.service;

import com.frank.superjacoco.entity.*;

public interface CumulativeCoverageService {

    CoverageSetEntity createCoverageSet(CreateCoverageSetRequest request);

    CoverageRunEntity appendCoverageRun(String coverageSetId, AppendCoverageRunRequest request);

    CoverageRunEntity getCoverageRun(String coverageSetId, String runId);

    void refreshCoverageSet(String coverageSetId);

    CoverageSetOverviewResponse getCoverageSetOverview(String coverageSetId);

    CoverageNodeListResponse queryNodes(String coverageSetId, CoverageNodeQuery query);

    CoverageSourceResponse querySource(String coverageSetId, String classKey);
}
