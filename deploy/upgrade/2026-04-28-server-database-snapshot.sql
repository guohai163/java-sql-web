CREATE TABLE IF NOT EXISTS `db_server_database_snapshot_tb` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '自增值',
  `server_code` int(11) NOT NULL COMMENT '实例编号',
  `database_name` varchar(128) NOT NULL COMMENT '库名',
  `synced_at` datetime NOT NULL COMMENT '同步时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_server_database` (`server_code`, `database_name`),
  KEY `idx_database_name` (`database_name`),
  KEY `idx_synced_at` (`synced_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
