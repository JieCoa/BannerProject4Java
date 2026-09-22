CREATE DATABASE IF NOT EXISTS banner_db DEFAULT CHARACTER SET utf8mb4;
USE banner_db;

CREATE TABLE IF NOT EXISTS business_info (
    id BIGINT NOT NULL AUTO_INCREMENT,
    biz_code VARCHAR(32) NOT NULL,
    biz_name VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    status TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_biz_code (biz_code)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS banner_info (
    id BIGINT NOT NULL AUTO_INCREMENT,
    biz_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    image_url VARCHAR(512) NOT NULL,
    jump_url VARCHAR(512) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    sort INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_banner_biz (biz_id), KEY idx_banner_end (end_time)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS banner_user_shard (
    id BIGINT NOT NULL AUTO_INCREMENT,
    banner_id BIGINT NOT NULL,
    shard_no INT NOT NULL,
    user_ids VARCHAR(12000) NOT NULL COMMENT '逗号分隔，最多1000个userId',
    version BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_banner_shard (banner_id, shard_no),
    KEY idx_banner_user_shard_banner (banner_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS banner_change_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    banner_id BIGINT NOT NULL,
    operate_type VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event_id (event_id),
    UNIQUE KEY uk_outbox_banner_version (banner_id, version),
    KEY idx_outbox_pending (status, next_retry_time)
) ENGINE=InnoDB COMMENT='Banner变更事件Outbox';

INSERT IGNORE INTO business_info (id, biz_code, biz_name, description) VALUES
    (1, 'agri', '助农产品', '农产品助农专区'),
    (2, 'digital', '手机数码', '手机数码专区'),
    (3, 'clothes', '服装', '服装专区'),
    (4, 'beauty', '化妆品', '化妆品专区');
