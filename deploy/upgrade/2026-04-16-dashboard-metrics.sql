USE `javasqlweb_db`;

ALTER TABLE `user_tb`
  ADD COLUMN `create_time` datetime NULL COMMENT '创建时间' AFTER `email`;

UPDATE `user_tb`
SET `create_time` = NOW()
WHERE `create_time` IS NULL;

ALTER TABLE `user_tb`
  MODIFY COLUMN `create_time` datetime NOT NULL COMMENT '创建时间';

ALTER TABLE `db_query_log`
  ADD COLUMN `server_code` int(11) NULL COMMENT '查询目标实例' AFTER `query_database`,
  ADD COLUMN `result_row_count` int(11) NULL COMMENT '返回条数' AFTER `query_consuming`;

CREATE TABLE IF NOT EXISTS `db_query_log_target_tb` (
  `code` int(11) NOT NULL COMMENT '自增值',
  `query_log_code` int(11) NOT NULL COMMENT '查询日志编号',
  `database_name` varchar(100) NOT NULL COMMENT '数据库名',
  `table_name` varchar(200) NOT NULL COMMENT '表名'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `db_query_log_target_tb`
  ADD PRIMARY KEY (`code`),
  ADD KEY `idx_query_log_code` (`query_log_code`),
  ADD KEY `idx_database_table` (`database_name`,`table_name`);

ALTER TABLE `db_query_log_target_tb`
  MODIFY `code` int(11) NOT NULL AUTO_INCREMENT;
