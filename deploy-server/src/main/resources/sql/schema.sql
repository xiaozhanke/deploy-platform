-- deploy-tool 数据库初始化脚本（MySQL 8.0+，utf8mb4）
--
-- 平时由 JPA `ddl-auto: update` 在应用启动时自动维护，本文件用于：
-- 1. 全新环境手动 `source` 重建一份干净的库
-- 2. 作为表结构文档，对照 entity 排查 schema 漂移
--
-- 表按外键依赖正序排列：被依赖的先建。DROP 时关 FOREIGN_KEY_CHECKS 避免因 FK 顺序失败。

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `deployment_record`;
DROP TABLE IF EXISTS `platform_user_role`;
DROP TABLE IF EXISTS `platform_user`;
DROP TABLE IF EXISTS `platform_role`;
DROP TABLE IF EXISTS `server_record`;
DROP TABLE IF EXISTS `file_record`;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE `file_record` (
  `id` varchar(255) NOT NULL COMMENT '文件 Id',
  `create_time` datetime(6) NOT NULL COMMENT '创建时间',
  `create_user` varchar(255) NOT NULL COMMENT '创建用户',
  `is_deleted` bit(1) DEFAULT NULL COMMENT '已删除',
  `update_time` datetime(6) NOT NULL COMMENT '更新时间',
  `update_user` varchar(255) NOT NULL COMMENT '更新用户',
  `architecture` enum('AARCH64','ARM','UNKNOWN','X64','X86') DEFAULT NULL COMMENT '芯片架构',
  `artifact_id` varchar(255) DEFAULT NULL COMMENT '构件 Id',
  `content_type` varchar(255) DEFAULT NULL COMMENT '内容类型',
  `description` varchar(255) DEFAULT NULL COMMENT '文件描述',
  `file_name` varchar(255) NOT NULL COMMENT '文件名',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小（字节）',
  `group_id` varchar(255) DEFAULT NULL COMMENT '文件分组 Id',
  `relative_path` varchar(255) NOT NULL COMMENT '文件相对路径',
  `scope` enum('APPLICATION_BACKEND','APPLICATION_FRONTEND','CONFIGURATION','ENVIRONMENT') DEFAULT NULL COMMENT '使用范围',
  `version` varchar(255) DEFAULT NULL COMMENT '版本',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件记录表';

CREATE TABLE `server_record` (
  `id` varchar(255) NOT NULL COMMENT '服务器 Id',
  `create_time` datetime(6) NOT NULL COMMENT '创建时间',
  `create_user` varchar(255) NOT NULL COMMENT '创建用户',
  `is_deleted` bit(1) DEFAULT NULL COMMENT '已删除',
  `update_time` datetime(6) NOT NULL COMMENT '更新时间',
  `update_user` varchar(255) NOT NULL COMMENT '更新用户',
  `auth_type` enum('KEY','KEY_WITH_PASS','PASSWORD') NOT NULL COMMENT '认证方式',
  `cipher_algorithms` varchar(255) DEFAULT NULL COMMENT '加密算法',
  `compression_enabled` bit(1) DEFAULT NULL COMMENT '是否启用压缩',
  `connection_timeout` int DEFAULT NULL COMMENT '连接超时时间（毫秒）',
  `description` varchar(255) DEFAULT NULL COMMENT '服务器描述',
  `home_dir` varchar(255) NOT NULL COMMENT '主目录',
  `host` varchar(255) NOT NULL COMMENT '主机地址',
  `kex_algorithms` varchar(255) DEFAULT NULL COMMENT '密钥交换算法',
  `mac_algorithms` varchar(255) DEFAULT NULL COMMENT 'MAC 算法',
  `name` varchar(255) NOT NULL COMMENT '服务器名称',
  `password` varchar(255) DEFAULT NULL COMMENT '密码（如果使用密码认证）',
  `port` int NOT NULL COMMENT '端口号',
  `port_forwarding_enabled` bit(1) DEFAULT NULL COMMENT '是否启用端口转发',
  `private_key_password` varchar(255) DEFAULT NULL COMMENT '私钥密码（如果私钥有密码保护）',
  `private_key_path` varchar(255) DEFAULT NULL COMMENT '私钥路径（如果使用密钥认证）',
  `server_host_key_algorithms` varchar(255) DEFAULT NULL COMMENT '服务器主机密钥算法',
  `strict_host_key_checking` bit(1) DEFAULT NULL COMMENT '是否启用严格的主机密钥检查',
  `username` varchar(255) NOT NULL COMMENT '用户名',
  `x11forwarding_enabled` bit(1) DEFAULT NULL COMMENT '是否启用 X11 转发',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务器记录表';

CREATE TABLE `platform_role` (
  `id` varchar(255) NOT NULL COMMENT '角色 Id',
  `create_time` datetime(6) NOT NULL COMMENT '创建时间',
  `create_user` varchar(255) NOT NULL COMMENT '创建用户',
  `is_deleted` bit(1) DEFAULT NULL COMMENT '已删除',
  `update_time` datetime(6) NOT NULL COMMENT '更新时间',
  `update_user` varchar(255) NOT NULL COMMENT '更新用户',
  `description` varchar(255) DEFAULT NULL COMMENT '角色描述',
  `name` varchar(255) NOT NULL COMMENT '角色名',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_role_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

CREATE TABLE `platform_user` (
  `id` varchar(255) NOT NULL COMMENT '用户 Id',
  `create_time` datetime(6) NOT NULL COMMENT '创建时间',
  `create_user` varchar(255) NOT NULL COMMENT '创建用户',
  `is_deleted` bit(1) DEFAULT NULL COMMENT '已删除',
  `update_time` datetime(6) NOT NULL COMMENT '更新时间',
  `update_user` varchar(255) NOT NULL COMMENT '更新用户',
  `account_expired_time` datetime(6) DEFAULT NULL COMMENT '账户过期时间',
  `avatar` tinytext COMMENT '头像',
  `display_name` varchar(255) NOT NULL COMMENT '用户显示名',
  `email` varchar(255) DEFAULT NULL COMMENT '电子邮箱',
  `failed_login_count` int DEFAULT NULL COMMENT '连续登录失败次数',
  `last_failed_login_time` datetime(6) DEFAULT NULL COMMENT '最后尝试登录失败时间',
  `password` varchar(255) NOT NULL COMMENT '密码',
  `password_last_changed_time` datetime(6) DEFAULT NULL COMMENT '密码最后修改时间',
  `phone` varchar(255) DEFAULT NULL COMMENT '手机号码',
  `status` enum('ACTIVE','DISABLED','INITIALIZED','LOCKED') NOT NULL COMMENT '用户状态',
  `username` varchar(255) NOT NULL COMMENT '用户名',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

CREATE TABLE `platform_user_role` (
  `platform_user_id` varchar(255) NOT NULL,
  `platform_role_id` varchar(255) NOT NULL,
  KEY `fk_platform_user_role_role` (`platform_role_id`),
  KEY `fk_platform_user_role_user` (`platform_user_id`),
  CONSTRAINT `fk_platform_user_role_user` FOREIGN KEY (`platform_user_id`) REFERENCES `platform_user` (`id`),
  CONSTRAINT `fk_platform_user_role_role` FOREIGN KEY (`platform_role_id`) REFERENCES `platform_role` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';

CREATE TABLE `deployment_record` (
  `id` varchar(255) NOT NULL COMMENT '部署 Id',
  `create_time` datetime(6) NOT NULL COMMENT '创建时间',
  `create_user` varchar(255) NOT NULL COMMENT '创建用户',
  `is_deleted` bit(1) DEFAULT NULL COMMENT '已删除',
  `update_time` datetime(6) NOT NULL COMMENT '更新时间',
  `update_user` varchar(255) NOT NULL COMMENT '更新用户',
  `active_profiles` varchar(255) DEFAULT NULL COMMENT '激活的配置文件',
  `application_type` enum('BACKEND','FRONTEND') NOT NULL COMMENT '应用类型',
  `deploy_time` datetime(6) NOT NULL COMMENT '部署时间',
  `deployment_config_path` varchar(255) DEFAULT NULL COMMENT '配置文件夹路径',
  `deployment_path` varchar(255) NOT NULL COMMENT '部署路径',
  `error_message` varchar(255) DEFAULT NULL COMMENT '错误信息',
  `last_start_time` datetime(6) DEFAULT NULL COMMENT '最后启动时间',
  `last_stop_time` datetime(6) DEFAULT NULL COMMENT '最后停止时间',
  `port` int DEFAULT NULL COMMENT '部署端口',
  `process_id` varchar(255) DEFAULT NULL COMMENT '进程 Id',
  `program_args` varchar(255) DEFAULT NULL COMMENT '程序参数',
  `is_running` bit(1) DEFAULT NULL COMMENT '是否正在运行',
  `status` enum('DEPLOYING','FAILED','SUCCESS') NOT NULL COMMENT '部署状态',
  `file_record_id` varchar(255) NOT NULL COMMENT '文件记录',
  `server_record_id` varchar(255) NOT NULL COMMENT '服务器记录',
  PRIMARY KEY (`id`),
  KEY `fk_deployment_record_file_record` (`file_record_id`),
  KEY `fk_deployment_record_server_record` (`server_record_id`),
  CONSTRAINT `fk_deployment_record_file_record` FOREIGN KEY (`file_record_id`) REFERENCES `file_record` (`id`),
  CONSTRAINT `fk_deployment_record_server_record` FOREIGN KEY (`server_record_id`) REFERENCES `server_record` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部署记录表';
