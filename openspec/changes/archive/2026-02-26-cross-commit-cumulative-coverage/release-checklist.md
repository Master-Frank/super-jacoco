## Rollout Checklist

- [ ] DB migration applied: `sql/cumulative_coverage.sql`
- [ ] New mapper XML files loaded in runtime package scan
- [ ] `reports/` path has write permission for object files
- [ ] Smoke test `POST /api/v1/coverage/sets` + `POST /api/v1/coverage/sets/{setId}/runs`
- [ ] Smoke test `GET /api/v1/coverage/sets/{setId}` / `nodes` / `source`
- [ ] Validate async run transitions (`PENDING -> PROCESSING -> COMPLETED|FAILED`)
- [ ] Validate lifecycle cleanup job is running on schedule

## Rollback Plan

1. Disable new APIs at gateway or service routing level.
2. Stop async run producers for cumulative coverage.
3. Keep legacy `/cov/*` APIs serving existing features.
4. Revert application package to previous stable build.
5. Preserve `coverage_*` tables for forensic analysis; avoid data-destructive rollback.

## Data Repair Plan

1. For failed runs, call `POST /api/v1/coverage/sets/{setId}/refresh`.
2. If snapshot files are corrupted, re-upload source `jacoco.xml.gz` and re-trigger run.
3. Rebuild node stats by refreshing latest run for the set.
