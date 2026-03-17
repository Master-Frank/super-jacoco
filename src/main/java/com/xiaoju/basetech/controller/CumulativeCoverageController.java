package com.xiaoju.basetech.controller;

import com.xiaoju.basetech.entity.*;
import com.xiaoju.basetech.service.CumulativeCoverageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/coverage/sets")
@Validated
public class CumulativeCoverageController {

    private final CumulativeCoverageService cumulativeCoverageService;

    @Autowired
    public CumulativeCoverageController(CumulativeCoverageService cumulativeCoverageService) {
        this.cumulativeCoverageService = cumulativeCoverageService;
    }

    @PostMapping
    public HttpResult<CoverageSetEntity> createSet(@RequestBody @Valid CreateCoverageSetRequest request) {
        return HttpResult.success(cumulativeCoverageService.createCoverageSet(request));
    }

    @PostMapping("/{setId}/runs")
    public HttpResult<CoverageRunEntity> appendRun(@PathVariable("setId") String setId,
                                                   @RequestBody @Valid AppendCoverageRunRequest request) {
        return HttpResult.success(cumulativeCoverageService.appendCoverageRun(setId, request));
    }

    @GetMapping("/{setId}/runs/{runId}")
    public HttpResult<CoverageRunEntity> getRun(@PathVariable("setId") String setId,
                                                @PathVariable("runId") String runId) {
        return HttpResult.success(cumulativeCoverageService.getCoverageRun(setId, runId));
    }

    @PostMapping("/{setId}/refresh")
    public HttpResult<Boolean> refresh(@PathVariable("setId") String setId) {
        cumulativeCoverageService.refreshCoverageSet(setId);
        return HttpResult.success(true);
    }

    @GetMapping("/{setId}")
    public HttpResult<CoverageSetOverviewResponse> getSet(@PathVariable("setId") String setId,
                                                          HttpServletResponse response) {
        response.setHeader("Cache-Control", "max-age=300");
        return HttpResult.success(cumulativeCoverageService.getCoverageSetOverview(setId));
    }

    @GetMapping("/{setId}/nodes")
    public HttpResult<CoverageNodeListResponse> getNodes(@PathVariable("setId") String setId,
                                                         @RequestParam(value = "level", required = false) String level,
                                                         @RequestParam(value = "parent_key", required = false) String parentKey,
                                                         @RequestParam(value = "sort_by", required = false) String sortBy,
                                                         @RequestParam(value = "order", required = false) String order,
                                                         @RequestParam(value = "page", required = false) Integer page,
                                                         @RequestParam(value = "page_size", required = false) Integer pageSize,
                                                         HttpServletResponse response) {
        response.setHeader("Cache-Control", "max-age=300");
        CoverageNodeQuery query = new CoverageNodeQuery();
        query.setLevel(level);
        query.setParentKey(parentKey);
        query.setSortBy(sortBy);
        query.setOrder(order);
        query.setPage(page);
        query.setPageSize(pageSize);
        return HttpResult.success(cumulativeCoverageService.queryNodes(setId, query));
    }

    @GetMapping("/{setId}/source")
    public HttpResult<CoverageSourceResponse> getSource(@PathVariable("setId") String setId,
                                                        @RequestParam("class_key") String classKey,
                                                        HttpServletRequest request,
                                                        HttpServletResponse response) {
        response.setHeader("Cache-Control", "max-age=3600");
        CoverageSourceResponse data = cumulativeCoverageService.querySource(setId, classKey);
        if (data != null && data.getEtag() != null) {
            response.setHeader("ETag", data.getEtag());
            String ifNoneMatch = request.getHeader("If-None-Match");
            if (data.getEtag().equals(ifNoneMatch)) {
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                return HttpResult.success();
            }
        }
        return HttpResult.success(data);
    }
}
