CREATE TABLE IF NOT EXISTS diff_coverage_report (
  id INT AUTO_INCREMENT,
  job_record_uuid VARCHAR(80) NOT NULL,
  request_status INT NOT NULL,
  giturl VARCHAR(255) NOT NULL,
  now_version VARCHAR(255) NOT NULL,
  base_version VARCHAR(255) NOT NULL,
  diffmethod CLOB,
  type INT NOT NULL DEFAULT 0,
  report_url VARCHAR(300) NOT NULL DEFAULT '',
  line_coverage DECIMAL(5,2) NOT NULL DEFAULT -1.00,
  branch_coverage DECIMAL(5,2) NOT NULL DEFAULT -1.00,
  err_msg VARCHAR(1000) NOT NULL DEFAULT '',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  sub_module VARCHAR(255) NOT NULL DEFAULT '',
  "from" INT NOT NULL DEFAULT 0,
  now_local_path VARCHAR(500) NOT NULL DEFAULT '',
  base_local_path VARCHAR(500) NOT NULL DEFAULT '',
  log_file VARCHAR(255) NOT NULL DEFAULT '',
  PRIMARY KEY (job_record_uuid)
);

DROP INDEX IF EXISTS idx_diff_coverage_report_id;
CREATE INDEX idx_diff_coverage_report_id ON diff_coverage_report(id);

CREATE TABLE IF NOT EXISTS diff_deploy_info (
  id INT AUTO_INCREMENT,
  job_record_uuid VARCHAR(80) NOT NULL,
  address VARCHAR(64) NOT NULL,
  port INT NOT NULL,
  code_path VARCHAR(1000) NOT NULL DEFAULT '',
  child_modules VARCHAR(1000) NOT NULL DEFAULT '',
  PRIMARY KEY (job_record_uuid)
);

DROP INDEX IF EXISTS idx_diff_deploy_info_id;
CREATE INDEX idx_diff_deploy_info_id ON diff_deploy_info(id);
