
SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";

--
-- 数据库： `javasqladmin_db`
--
CREATE DATABASE IF NOT EXISTS `javasqladmin_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `javasqladmin_db`;

-- --------------------------------------------------------

--
-- 表的结构 `db_connect_config_tb`
--

CREATE TABLE `db_connect_config_tb` (
  `code` int(11) NOT NULL,
  `db_server_name` varchar(45) NOT NULL,
  `db_server_host` varchar(45) NOT NULL,
  `db_server_port` varchar(45) NOT NULL,
  `db_server_username` varchar(45) NOT NULL,
  `db_server_password` varchar(45) NOT NULL,
  `db_server_type` varchar(45) NOT NULL,
  `create_time` datetime NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- --------------------------------------------------------

--
-- 表的结构 `db_query_log`
--

CREATE TABLE `db_query_log` (
  `code` int(11) NOT NULL,
  `query_ip` varchar(45) NOT NULL,
  `query_name` varchar(45) NOT NULL,
  `query_sqlscript` varchar(8000) NOT NULL,
  `query_time` datetime NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


--
-- 转储表的索引
--

--
-- 表的索引 `db_connect_config_tb`
--
ALTER TABLE `db_connect_config_tb`
  ADD PRIMARY KEY (`code`);

--
-- 表的索引 `db_query_log`
--
ALTER TABLE `db_query_log`
  ADD PRIMARY KEY (`code`);

--
-- 在导出的表使用AUTO_INCREMENT
--

--
-- 使用表AUTO_INCREMENT `db_connect_config_tb`
--
ALTER TABLE `db_connect_config_tb`
  MODIFY `code` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;

--
-- 使用表AUTO_INCREMENT `db_query_log`
--
ALTER TABLE `db_query_log`
  MODIFY `code` int(11) NOT NULL AUTO_INCREMENT;
COMMIT;
