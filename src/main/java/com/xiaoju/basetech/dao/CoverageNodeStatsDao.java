package com.xiaoju.basetech.dao;

import com.xiaoju.basetech.entity.CoverageNodeStatsEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CoverageNodeStatsDao {

    int deleteBySetId(@Param("coverageSetId") String coverageSetId);

    int batchInsert(@Param("items") List<CoverageNodeStatsEntity> items);

    List<CoverageNodeStatsEntity> queryBySetAndType(@Param("coverageSetId") String coverageSetId,
                                                    @Param("nodeType") String nodeType,
                                                    @Param("parentKey") String parentKey,
                                                    @Param("offset") Integer offset,
                                                    @Param("limit") Integer limit);

    Integer countBySetAndType(@Param("coverageSetId") String coverageSetId,
                              @Param("nodeType") String nodeType,
                              @Param("parentKey") String parentKey);
}
