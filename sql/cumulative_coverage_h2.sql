CREATE TABLE IF NOT EXISTS coverage_campaign (
  campaign_id VARCHAR(80) PRIMARY KEY,
  campaign_name VARCHAR(256) NOT NULL,
  git_url VARCHAR(512) NOT NULL,
  branch VARCHAR(256) NOT NULL,
  metric_scope VARCHAR(32) NOT NULL,
  from_type VARCHAR(32) NOT NULL,
  description CLOB,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_campaign_git_branch ON coverage_campaign(git_url, branch);

CREATE TABLE IF NOT EXISTS coverage_set (
  coverage_set_id VARCHAR(80) PRIMARY KEY,
  campaign_id VARCHAR(80),
  git_url VARCHAR(512) NOT NULL,
  repo_local_path VARCHAR(1024),
  branch VARCHAR(256) NOT NULL,
  type VARCHAR(32) NOT NULL,
  from_type VARCHAR(32) NOT NULL,
  scope_key VARCHAR(512) NOT NULL,
  current_commit_id VARCHAR(80),
  current_snapshot_key VARCHAR(1024),
  line_coverage_rate DECIMAL(5,4),
  branch_coverage_rate DECIMAL(5,4),
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_set_scope_key ON coverage_set(scope_key);
CREATE INDEX IF NOT EXISTS idx_set_git_branch ON coverage_set(git_url, branch);
CREATE INDEX IF NOT EXISTS idx_set_status ON coverage_set(status);

CREATE TABLE IF NOT EXISTS coverage_run (
  run_id VARCHAR(80) PRIMARY KEY,
  coverage_set_id VARCHAR(80) NOT NULL,
  commit_id VARCHAR(80) NOT NULL,
  commit_message CLOB,
  exec_object_key VARCHAR(1024),
  xml_object_key VARCHAR(1024) NOT NULL,
  line_coverage_rate DECIMAL(5,4),
  branch_coverage_rate DECIMAL(5,4),
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  error_message CLOB,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_run_set_commit ON coverage_run(coverage_set_id, commit_id);
CREATE INDEX IF NOT EXISTS idx_run_created ON coverage_run(created_at);

CREATE TABLE IF NOT EXISTS coverage_node_stats (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  coverage_set_id VARCHAR(80) NOT NULL,
  commit_id VARCHAR(80) NOT NULL,
  node_type VARCHAR(16) NOT NULL,
  node_key VARCHAR(1024) NOT NULL,
  parent_key VARCHAR(1024) NOT NULL DEFAULT '',
  display_name VARCHAR(1024) NOT NULL,
  source_file VARCHAR(1024),
  line_missed INT NOT NULL DEFAULT 0,
  line_covered INT NOT NULL DEFAULT 0,
  branch_missed INT NOT NULL DEFAULT 0,
  branch_covered INT NOT NULL DEFAULT 0,
  instruction_missed INT NOT NULL DEFAULT 0,
  instruction_covered INT NOT NULL DEFAULT 0,
  complexity_missed INT NOT NULL DEFAULT 0,
  complexity_covered INT NOT NULL DEFAULT 0,
  method_missed INT NOT NULL DEFAULT 0,
  method_covered INT NOT NULL DEFAULT 0,
  class_missed INT NOT NULL DEFAULT 0,
  class_covered INT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_node_set_key ON coverage_node_stats(coverage_set_id, node_key);
CREATE INDEX IF NOT EXISTS idx_node_set_type ON coverage_node_stats(coverage_set_id, node_type);
CREATE INDEX IF NOT EXISTS idx_node_set_parent ON coverage_node_stats(coverage_set_id, parent_key);

CREATE TABLE IF NOT EXISTS report_artifact (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  coverage_set_id VARCHAR(80) NOT NULL,
  run_id VARCHAR(80),
  commit_id VARCHAR(80),
  artifact_type VARCHAR(32) NOT NULL,
  object_key VARCHAR(1024) NOT NULL,
  metadata CLOB,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_artifact_set ON report_artifact(coverage_set_id);
CREATE INDEX IF NOT EXISTS idx_artifact_run ON report_artifact(run_id);
CREATE INDEX IF NOT EXISTS idx_artifact_type ON report_artifact(artifact_type);
