CREATE TABLE coverage_campaign (
  campaign_id VARCHAR(80) PRIMARY KEY,
  campaign_name VARCHAR(256) NOT NULL,
  git_url VARCHAR(512) NOT NULL,
  branch VARCHAR(256) NOT NULL,
  metric_scope VARCHAR(32) NOT NULL,
  from_type VARCHAR(32) NOT NULL,
  description TEXT,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_campaign_git_branch (git_url(255), branch(100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE coverage_set (
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
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_set_scope_key (scope_key(255)),
  INDEX idx_set_git_branch (git_url(255), branch(100)),
  INDEX idx_set_status (status),
  CONSTRAINT fk_set_campaign FOREIGN KEY (campaign_id) REFERENCES coverage_campaign(campaign_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE coverage_run (
  run_id VARCHAR(80) PRIMARY KEY,
  coverage_set_id VARCHAR(80) NOT NULL,
  commit_id VARCHAR(80) NOT NULL,
  commit_message TEXT,
  exec_object_key VARCHAR(1024),
  xml_object_key VARCHAR(1024) NOT NULL,
  line_coverage_rate DECIMAL(5,4),
  branch_coverage_rate DECIMAL(5,4),
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  error_message TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_run_set_commit (coverage_set_id, commit_id),
  INDEX idx_run_created (created_at),
  CONSTRAINT fk_run_set FOREIGN KEY (coverage_set_id) REFERENCES coverage_set(coverage_set_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE coverage_node_stats (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
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
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_node_set_type (coverage_set_id, node_type),
  INDEX idx_node_set_parent (coverage_set_id, parent_key(255)),
  INDEX idx_node_set_key (coverage_set_id, node_key(255)),
  UNIQUE KEY uk_node_set_key (coverage_set_id, node_key(255)),
  CONSTRAINT fk_node_set FOREIGN KEY (coverage_set_id) REFERENCES coverage_set(coverage_set_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE report_artifact (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  coverage_set_id VARCHAR(80) NOT NULL,
  run_id VARCHAR(80),
  commit_id VARCHAR(80),
  artifact_type VARCHAR(32) NOT NULL,
  object_key VARCHAR(1024) NOT NULL,
  metadata TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_artifact_set (coverage_set_id),
  INDEX idx_artifact_run (run_id),
  INDEX idx_artifact_type (artifact_type),
  CONSTRAINT fk_artifact_set FOREIGN KEY (coverage_set_id) REFERENCES coverage_set(coverage_set_id) ON DELETE CASCADE,
  CONSTRAINT fk_artifact_run FOREIGN KEY (run_id) REFERENCES coverage_run(run_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
