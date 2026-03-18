package com.frank.superjacoco.dao;

import com.frank.superjacoco.entity.ReportArtifactEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ReportArtifactDao {

    int insert(@Param("item") ReportArtifactEntity item);

    List<ReportArtifactEntity> queryByRunId(@Param("runId") String runId);
}
