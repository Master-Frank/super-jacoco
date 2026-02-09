CREATE TABLE diff_coverage_report (
  `id` int(10) NOT NULL AUTO_INCREMENT,
  `job_record_uuid` varchar(80) NOT NULL,
  `request_status` int(10) NOT NULL,
  `giturl` varchar(80) NOT NULL,
  `now_version` varchar(80) NOT NULL,
  `base_version` varchar(80) NOT NULL,
  `diffmethod` mediumtext,
  `type` int(11) NOT NULL DEFAULT '0',
  `report_url` varchar(300) NOT NULL DEFAULT '',
  `line_coverage` double(5,2) NOT NULL DEFAULT '-1.00',
  `branch_coverage` double(5,2) NOT NULL DEFAULT '-1.00',
  `err_msg` varchar(1000) NOT NULL DEFAULT '',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `sub_module` varchar(255) NOT NULL DEFAULT '',
  `from` int(10) NOT NULL DEFAULT '0',
  `now_local_path` varchar(500) NOT NULL DEFAULT '',
  `base_local_path` varchar(500) NOT NULL DEFAULT '',
  `log_file` varchar(255) NOT NULL DEFAULT '',
  PRIMARY KEY (`job_record_uuid`),
  KEY `idx_diff_coverage_report_id` (`id`)
);


CREATE TABLE diff_deploy_info (
  `id` int(10) NOT NULL AUTO_INCREMENT,
  `job_record_uuid` varchar(80) NOT NULL,
  `address` varchar(15) NOT NULL,
  `port` int(10) NOT NULL,
  `code_path` varchar(1000) NOT NULL DEFAULT '',
  `child_modules` varchar(1000) NOT NULL DEFAULT '',
  PRIMARY KEY (`job_record_uuid`),
  KEY `idx_diff_deploy_info_id` (`id`)
);



