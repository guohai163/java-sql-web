USE `javasqlweb_db`;

CREATE TABLE IF NOT EXISTS `oidc_login_state_tb` (
  `code` int(11) NOT NULL AUTO_INCREMENT COMMENT '自增值',
  `usage_type` varchar(32) NOT NULL COMMENT 'State 用途：admin 表示管理端授权，login 表示登录页授权',
  `state_key` varchar(128) NOT NULL COMMENT 'OIDC 授权请求中的 state 参数',
  `code_verifier` varchar(255) NOT NULL COMMENT '与 state 绑定的 PKCE code_verifier',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`code`),
  UNIQUE KEY `uk_usage_state` (`usage_type`,`state_key`),
  KEY `idx_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OIDC 登录/授权的一次性 PKCE state 存储表';
