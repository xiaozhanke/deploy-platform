-- deploy-tool 种子数据：仅覆盖 ADMIN 角色 + admin 账号 + 用户角色关联，保证管理员可登录
--
-- 默认账号 admin / 123456（密码字段已用 BCrypt 加密；前缀 {bcrypt} 提示 DelegatingPasswordEncoder 选编码器）
-- 业务表（file_record / server_record / deployment_record）保持为空，由用户登录后通过 UI 录入
--
-- 重复执行：用 INSERT IGNORE，已有相同 PK / unique key 的行会被跳过，不抛错

INSERT IGNORE INTO `platform_role`
  (`id`, `create_time`, `create_user`, `is_deleted`, `update_time`, `update_user`, `description`, `name`)
VALUES
  ('role-admin', '2026-05-26 13:39:14.000000', 'system', 0, '2026-05-26 13:39:14.000000', 'system', '管理员', 'ADMIN');

INSERT IGNORE INTO `platform_user`
  (`id`, `create_time`, `create_user`, `is_deleted`, `update_time`, `update_user`,
   `account_expired_time`, `avatar`, `display_name`, `email`, `failed_login_count`, `last_failed_login_time`,
   `password`, `password_last_changed_time`, `phone`, `status`, `username`)
VALUES
  ('7daa2b79-0ae7-4616-a05b-dfd8888bb2a2', '2026-05-26 13:39:18.685834', 'system', 0,
   '2026-05-26 13:39:18.685834', 'system',
   NULL, NULL, '管理员', NULL, 0, NULL,
   '{bcrypt}$2a$10$NfL1ApsVAgEJ7EfVs9RSKuQQQXGP8bRmZKm3F.xRgKK9wxiDNl4IG',
   '2026-05-26 13:39:18.518647', NULL, 'ACTIVE', 'admin');

INSERT IGNORE INTO `platform_user_role` (`platform_user_id`, `platform_role_id`)
VALUES ('7daa2b79-0ae7-4616-a05b-dfd8888bb2a2', 'role-admin');
