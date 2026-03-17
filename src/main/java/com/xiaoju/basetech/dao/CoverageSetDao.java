package com.xiaoju.basetech.dao;

import com.xiaoju.basetech.entity.CoverageSetEntity;
import org.apache.ibatis.annotations.Param;

public interface CoverageSetDao {

    int insert(@Param("item") CoverageSetEntity item);

    CoverageSetEntity selectById(@Param("coverageSetId") String coverageSetId);

    CoverageSetEntity selectByScopeKey(@Param("scopeKey") String scopeKey);

    int updateCurrentSnapshot(@Param("coverageSetId") String coverageSetId,
                              @Param("currentCommitId") String currentCommitId,
                              @Param("currentSnapshotKey") String currentSnapshotKey);

    int updateCoverageRate(@Param("coverageSetId") String coverageSetId,
                           @Param("lineCoverageRate") Double lineCoverageRate,
                           @Param("branchCoverageRate") Double branchCoverageRate);
}
