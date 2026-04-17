USE `javasqlweb_db`;

CREATE TABLE IF NOT EXISTS `webauthn_request_tb` (
  `code` int(11) NOT NULL AUTO_INCREMENT COMMENT '自增值',
  `request_type` varchar(32) NOT NULL COMMENT '请求类型',
  `request_key` varchar(128) NOT NULL COMMENT '请求关联键',
  `request_json` text NOT NULL COMMENT '序列化后的请求',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`code`),
  UNIQUE KEY `uk_type_key` (`request_type`,`request_key`),
  KEY `idx_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
