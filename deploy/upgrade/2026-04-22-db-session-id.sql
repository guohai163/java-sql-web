USE `javasqlweb_db`;

ALTER TABLE `db_query_log`
  ADD COLUMN `db_session_id` varchar(64) NULL COMMENT '目标库会话ID' AFTER `server_code`,
  ADD KEY `idx_server_session` (`server_code`,`db_session_id`);
