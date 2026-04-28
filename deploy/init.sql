-- 创建数据库： `javasqladmin_db`
CREATE DATABASE IF NOT EXISTS `javasqlweb_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `javasqlweb_db`;

-- 数据库连接表

CREATE TABLE `db_connect_config_tb` (
  `code` int(11) NOT NULL COMMENT '自增值',
  `db_server_name` varchar(45) NOT NULL COMMENT '服务器名',
  `db_server_host` varchar(45) NOT NULL COMMENT '服务器IP或域名',
  `db_server_port` varchar(45) NOT NULL COMMENT '服务器端口',
  `db_server_username` varchar(45) NOT NULL COMMENT '服务器用户名',
  `db_server_password` varchar(45) NOT NULL COMMENT '服务器密码',
  `db_server_type` varchar(45) NOT NULL COMMENT '服务器类型mssql/mysql',
  `db_ssl_mode` varchar(32) NOT NULL DEFAULT 'DEFAULT' COMMENT '连接安全模式',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `db_group` varchar(45) NOT NULL DEFAULT 'default' COMMENT '数据库分组'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `db_connect_config_tb`
  ADD PRIMARY KEY (`code`);

ALTER TABLE `db_connect_config_tb`
  MODIFY `code` int(11) NOT NULL AUTO_INCREMENT;

-- 服务器库名快照表
CREATE TABLE `db_server_database_snapshot_tb` (
  `id` bigint(20) NOT NULL COMMENT '自增值',
  `server_code` int(11) NOT NULL COMMENT '实例编号',
  `database_name` varchar(128) NOT NULL COMMENT '库名',
  `synced_at` datetime NOT NULL COMMENT '同步时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `db_server_database_snapshot_tb`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_server_database` (`server_code`,`database_name`),
  ADD KEY `idx_database_name` (`database_name`),
  ADD KEY `idx_synced_at` (`synced_at`);

ALTER TABLE `db_server_database_snapshot_tb`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;


-- 使用都日志表
CREATE TABLE `db_query_log` (
  `code` int(11) NOT NULL COMMENT '自增值',
  `query_ip` varchar(45) NOT NULL COMMENT '查询人IP',
  `query_name` varchar(45) NOT NULL COMMENT '查询人',
  `query_database` varchar(45) NOT NULL COMMENT '查询语句的库',
  `server_code` int(11) NULL COMMENT '查询目标实例',
  `db_session_id` varchar(64) NULL COMMENT '目标库会话ID',
  `query_sqlscript` varchar(8000) NOT NULL COMMENT '查询脚本',
  `query_consuming` int(11) NULL COMMENT '查询耗时',
  `result_row_count` int(11) NULL COMMENT '返回条数',
  `query_time` datetime NOT NULL COMMENT '查询时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `db_query_log`
  ADD PRIMARY KEY (`code`),
  ADD KEY `idx_server_session` (`server_code`,`db_session_id`);

ALTER TABLE `db_query_log`
  MODIFY `code` int(11) NOT NULL AUTO_INCREMENT;

CREATE TABLE `db_query_log_target_tb` (
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

-- 用户表
CREATE TABLE `user_tb` (
  `code` int(11) NOT NULL COMMENT '自增值',
  `user_name` varchar(45) NOT NULL COMMENT '用户名',
  `email` varchar(100) NOT NULL COMMENT '用户邮箱',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `pass_word` varchar(100) NOT NULL COMMENT '密码',
  `token` varchar(45) NOT NULL COMMENT '登录临时令牌',
  `auth_secret` VARCHAR(45) NULL COMMENT '二次验证密钥',
  `auth_status` varchar(45) NOT NULL DEFAULT 'UNBIND' COMMENT '密保绑定状态',
  `login_status` VARCHAR(45) NOT NULL DEFAULT 'LOGGING',
  `account_status` VARCHAR(45) NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态',
  `access_token_hash` VARCHAR(64) NULL COMMENT '长期访问令牌哈希',
  `access_token_expire_time` datetime NULL COMMENT '访问令牌过期时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `user_tb`
  ADD PRIMARY KEY (`code`),
  ADD UNIQUE KEY `uk_user_name` (`user_name`),
  ADD UNIQUE KEY `uk_email` (`email`);

ALTER TABLE `user_tb`
  MODIFY `code` int(11) NOT NULL AUTO_INCREMENT;

CREATE TABLE `user_security_task_tb` (
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

-- 用户组表
CREATE TABLE `usergroup` (
  `code` INT NOT NULL AUTO_INCREMENT COMMENT '编号',
  `group_name` VARCHAR(45) NOT NULL COMMENT '组名',
  `comment` VARCHAR(45) NULL COMMENT '备注',
  PRIMARY KEY (`code`));

-- 用户权限
CREATE TABLE `user_permissions` (
  `code` INT NOT NULL AUTO_INCREMENT,
  `user_code` INT NOT NULL COMMENT '用户编号',
  `group_code` INT NOT NULL COMMENT '组编号',
  PRIMARY KEY (`code`));

-- 数据库权限
CREATE TABLE `db_permissions` (
  `code` INT NOT NULL AUTO_INCREMENT,
  `db_code` INT NOT NULL COMMENT '数据库编号',
  `group_code` INT NOT NULL COMMENT '组编号',
  PRIMARY KEY (`code`));


-- 常用SQL
CREATE TABLE `guid_sql_tb` (
    `code` INT NOT NULL AUTO_INCREMENT,
    `category` VARCHAR(20) NOT NULL COMMENT '分类',
    `title` VARCHAR(100) NOT NULL COMMENT '标题',
    `script` VARCHAR(8000) NOT NULL COMMENT '语句',
    `server` VARCHAR(200) NOT NULL COMMENT '服务器',
    `database` VARCHAR(200) NOT NULL COMMENT '数据库',
    `create_date` datetime NOT NULL COMMENT '时间',
        PRIMARY KEY (`code`));

-- 应用启动时若 user_tb 中不存在 admin 用户，
-- server 会自动创建 admin@local.invalid / 随机密码 的管理员账号，
-- 并将明文随机密码打印到 server 控制台日志中，便于通过 docker logs 查看。

COMMIT;

-- WebAuth
CREATE TABLE `passkey_auths_tb` (
                                    `code` INT NOT NULL AUTO_INCREMENT,
                                    `user_name` VARCHAR(100) NOT NULL COMMENT '用户名',
                                    `user_handle` VARCHAR(200) NOT NULL COMMENT '用户标识',
                                    `credential_id` VARCHAR(200) NOT NULL COMMENT 'credential_id',
                                    `public_key` VARCHAR(200) NOT NULL COMMENT 'public_key',
                                    `user_agent` VARCHAR(200) NOT NULL COMMENT 'user agent',
                                    `create_date` datetime NOT NULL COMMENT '时间',
                                    PRIMARY KEY (`code`));

CREATE TABLE `webauthn_request_tb` (
                                     `code` INT NOT NULL AUTO_INCREMENT,
                                     `request_type` VARCHAR(32) NOT NULL COMMENT '请求类型',
                                     `request_key` VARCHAR(128) NOT NULL COMMENT '请求关联键',
                                     `request_json` TEXT NOT NULL COMMENT '序列化后的请求',
                                     `expire_time` datetime NOT NULL COMMENT '过期时间',
                                     `created_time` datetime NOT NULL COMMENT '创建时间',
                                     PRIMARY KEY (`code`),
                                     UNIQUE KEY `uk_type_key` (`request_type`,`request_key`),
                                     KEY `idx_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

--ALTER TABLE `javasqlweb_db`.`user_tb`
--ADD COLUMN `auth_secret` VARCHAR(45) NULL AFTER `token`,
--ADD COLUMN `auth_status` VARCHAR(45) NOT NULL DEFAULT 'UNBIND' AFTER `auth_secret`,
--ADD COLUMN `login_status` VARCHAR(45) NOT NULL DEFAULT 'LOGGING' AFTER `auth_status`;
-- ALTER TABLE `javasqlweb_db`.`db_query_log`
-- ADD COLUMN `query_database` VARCHAR(45) NULL AFTER `query_name`;
-- ALTER TABLE `javasqlweb_db`.`db_query_log`
-- ADD COLUMN `db_session_id` VARCHAR(64) NULL COMMENT '目标库会话ID' AFTER `server_code`,
-- ADD KEY `idx_server_session` (`server_code`,`db_session_id`);

-- ALTER TABLE `javasqlweb_db`.`db_connect_config_tb`
-- ADD COLUMN `db_ssl_mode` VARCHAR(32) NOT NULL DEFAULT 'DEFAULT' COMMENT '连接安全模式' AFTER `db_server_type`,
-- ADD COLUMN `db_group` VARCHAR(45) NOT NULL DEFAULT 'default' AFTER `create_time`;

-- ALTER TABLE `javasqlweb_db`.`user_tb`
-- MODIFY COLUMN `pass_word` VARCHAR(100) NOT NULL COMMENT '密码',
-- ADD COLUMN `access_token_hash` VARCHAR(64) NULL COMMENT '长期访问令牌哈希' AFTER `login_status`,
-- ADD COLUMN `access_token_expire_time` datetime NULL COMMENT '访问令牌过期时间' AFTER `access_token_hash`;
