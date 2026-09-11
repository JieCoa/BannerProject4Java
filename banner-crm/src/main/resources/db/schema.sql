-- banner 业务建表脚本（CRM 系统使用）
CREATE DATABASE IF NOT EXISTS banner_db DEFAULT CHARACTER SET utf8mb4;
USE banner_db;

-- 业务线表（按商品类别划分）
CREATE TABLE IF NOT EXISTS business_info (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '业务id',
    biz_code    VARCHAR(32)  NOT NULL COMMENT '业务编码(英文,用于缓存key), 如 agri/digital/clothes/beauty',
    biz_name    VARCHAR(64)  NOT NULL COMMENT '业务名称, 如 助农产品',
    description VARCHAR(255) NULL COMMENT '业务描述',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1-启用 0-停用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_code (biz_code)
) ENGINE = InnoDB COMMENT = '业务线表';

-- banner 表
CREATE TABLE IF NOT EXISTS banner_info (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'banner id',
    biz_id      BIGINT       NOT NULL COMMENT '所属业务id',
    title       VARCHAR(128) NOT NULL COMMENT '宣传标题',
    image_url   VARCHAR(512) NOT NULL COMMENT '宣传图片地址',
    jump_url    VARCHAR(512) NOT NULL COMMENT '跳转URL: 直播间/活动页面/商品详情页',
    start_time  DATETIME     NOT NULL COMMENT '生效开始时间',
    end_time    DATETIME     NOT NULL COMMENT '生效结束时间',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '展示顺序,越小越靠前',
    version     BIGINT       NOT NULL DEFAULT 0 COMMENT '版本号(毫秒时间戳),用于消息乱序比较',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_biz_id (biz_id),
    KEY idx_end_time (end_time)
) ENGINE = InnoDB COMMENT = 'banner信息表';

-- 示例业务线
INSERT INTO business_info (id, biz_code, biz_name, description) VALUES
    (1, 'agri',    '助农产品', '农产品助农专区'),
    (2, 'digital', '手机数码', '手机数码专区'),
    (3, 'clothes', '服装',     '服装专区'),
    (4, 'beauty',  '化妆品',   '化妆品专区');

-- 示例 banner（覆盖不同时间段）
INSERT INTO banner_info (id, biz_id, title, image_url, jump_url, start_time, end_time, sort, version) VALUES
    (1, 1, '助农苹果节', 'https://cdn.example.com/banner/apple.png', 'https://live.douyin.com/10001', '2026-09-01 00:00:00', '2026-09-30 23:59:59', 1, 1757232000000),
    (2, 2, '新款手机首发', 'https://cdn.example.com/banner/phone.png', 'https://haohuo.jinritemai.com/item/20002', '2026-09-10 00:00:00', '2026-09-20 23:59:59', 1, 1757232000000),
    (3, 3, '秋季上新', 'https://cdn.example.com/banner/clothes.png', 'https://activity.douyin.com/fall2026', '2026-09-05 00:00:00', '2026-09-15 23:59:59', 2, 1757232000000),
    (4, 4, '美妆大促', 'https://cdn.example.com/banner/beauty.png', 'https://haohuo.jinritemai.com/item/30004', '2026-10-01 00:00:00', '2026-10-07 23:59:59', 1, 1757232000000);
