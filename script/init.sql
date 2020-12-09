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
  `create_time` datetime NOT NULL COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `db_connect_config_tb`
  ADD PRIMARY KEY (`code`);

ALTER TABLE `db_connect_config_tb`
  MODIFY `code` int(11) NOT NULL AUTO_INCREMENT;

-- 增加测试库连接串
INSERT INTO `db_connect_config_tb` (`db_server_name`, `db_server_host`, `db_server_port`, `db_server_username`, `db_server_password`, `db_server_type`, `create_time`) VALUES
('test_sqlserver', 'testsql.db.guohai.org', '1433', 'sa', 'Passw0rd', 'mssql', now());

-- 使用都日志表
CREATE TABLE `db_query_log` (
  `code` int(11) NOT NULL COMMENT '自增值',
  `query_ip` varchar(45) NOT NULL COMMENT '查询人IP',
  `query_name` varchar(45) NOT NULL COMMENT '查询人',
  `query_sqlscript` varchar(8000) NOT NULL COMMENT '查询脚本',
  `query_time` datetime NOT NULL COMMENT '查询时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `db_query_log`
  ADD PRIMARY KEY (`code`);

ALTER TABLE `db_query_log`
  MODIFY `code` int(11) NOT NULL AUTO_INCREMENT;

-- 用户表
CREATE TABLE `user_tb` (
  `code` int(11) NOT NULL COMMENT '自增值',
  `user_name` varchar(45) NOT NULL COMMENT '用户名',
  `pass_word` varchar(45) NOT NULL COMMENT '密码',
  `token` varchar(45) NOT NULL COMMENT '登录临时令牌'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `user_tb`
  ADD PRIMARY KEY (`code`);

ALTER TABLE `user_tb`
  MODIFY `code` int(11) NOT NULL AUTO_INCREMENT;

INSERT INTO `user_tb` (`user_name`,`pass_word`,`token`) VALUES
('admin',md5(CONCAT(md5('admin'),'jsa')),'');

COMMIT;
