package com.frank.superjacoco.dao;

import com.frank.superjacoco.entity.CoverageRunEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CoverageRunDao {

    int insert(@Param("item") CoverageRunEntity item);

    CoverageRunEntity selectById(@Param("runId") String runId);

    List<CoverageRunEntity> selectBySetAndCommit(@Param("coverageSetId") String coverageSetId,
                                                 @Param("commitId") String commitId);

    CoverageRunEntity selectLatestBySetId(@Param("coverageSetId") String coverageSetId);

    Integer countBySetId(@Param("coverageSetId") String coverageSetId);

    int updateStatus(@Param("runId") String runId,
                     @Param("status") String status,
                     @Param("errorMessage") String errorMessage);
}
