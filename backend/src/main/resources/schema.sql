-- 攻城助手数据库建表脚本
-- 数据库：gongcheng
-- 字符集：utf8mb4

CREATE DATABASE IF NOT EXISTS gongcheng
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE gongcheng;

-- 军团表
CREATE TABLE IF NOT EXISTS legion (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(50)  NOT NULL COMMENT '军团名称',
    code            VARCHAR(32)  NOT NULL COMMENT '军团口令',
    owner_user_id   VARCHAR(64)  DEFAULT NULL COMMENT '创建人（军团所有人）用户ID',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_code (code),
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='军团表';

-- 成员表
CREATE TABLE IF NOT EXISTS member (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    legion_id       BIGINT        NOT NULL COMMENT '所属军团ID',
    user_id         VARCHAR(64)   DEFAULT NULL COMMENT '对应用户ID',
    role            VARCHAR(20)   DEFAULT 'member' COMMENT '角色：owner/admin/member',
    api_url         VARCHAR(1024) NOT NULL COMMENT '数据接口链接',
    role_name       VARCHAR(100)  DEFAULT NULL COMMENT '角色名称',
    progress        VARCHAR(100)  DEFAULT NULL COMMENT '进度',
    atk             INT           DEFAULT NULL COMMENT '攻击力',
    gun_dmg_bonus   VARCHAR(20)   DEFAULT NULL COMMENT '枪械伤害加成',
    crit_dmg_bonus  VARCHAR(20)   DEFAULT NULL COMMENT '暴击伤害加成',
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_legion_id (legion_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成员表';
