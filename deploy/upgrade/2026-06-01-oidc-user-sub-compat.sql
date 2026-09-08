USE `javasqlweb_db`;

ALTER TABLE `user_tb`
  ADD COLUMN IF NOT EXISTS `oidc_sub` VARCHAR(256) NULL COMMENT 'OIDC Subject 标识' AFTER `account_status`;
