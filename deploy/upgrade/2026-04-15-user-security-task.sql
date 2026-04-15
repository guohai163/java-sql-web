USE `javasqlweb_db`;

ALTER TABLE `user_tb`
  ADD COLUMN `email` varchar(100) NULL COMMENT '用户邮箱' AFTER `user_name`,
  ADD COLUMN `account_status` varchar(45) NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态' AFTER `login_status`;

UPDATE `user_tb`
SET `email` = CONCAT(`user_name`, '@local.invalid')
WHERE `email` IS NULL OR `email` = '';

ALTER TABLE `user_tb`
  MODIFY COLUMN `email` varchar(100) NOT NULL COMMENT '用户邮箱';

ALTER TABLE `user_tb`
  ADD UNIQUE KEY `uk_user_name` (`user_name`),
  ADD UNIQUE KEY `uk_email` (`email`);

CREATE TABLE IF NOT EXISTS `user_security_task_tb` (
  `code` int(11) NOT NULL COMMENT '自增值',
  `task_uuid_hash` varchar(64) NOT NULL COMMENT '任务UUID哈希',
  `user_code` int(11) NOT NULL COMMENT '用户编号',
  `task_type` varchar(45) NOT NULL COMMENT '任务类型',
  `task_status` varchar(45) NOT NULL COMMENT '任务状态',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `used_time` datetime NULL COMMENT '使用时间',
  `created_by` varchar(45) NOT NULL COMMENT '创建人',
  `created_time` datetime NOT NULL COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `user_security_task_tb`
  ADD PRIMARY KEY (`code`),
  ADD UNIQUE KEY `uk_task_uuid_hash` (`task_uuid_hash`),
  ADD KEY `idx_user_task_pending` (`user_code`,`task_status`,`created_time`);

ALTER TABLE `user_security_task_tb`
  MODIFY `code` int(11) NOT NULL AUTO_INCREMENT;
