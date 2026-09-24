-- ============================================================================
-- 精酿啤酒工坊 · 酿造运营平台 建表 + 种子数据
-- 库名 brewery_craft，由 mysql 镜像的 docker-entrypoint 在【镜像构建期】执行一次。
--
-- 五个业务模块：
--   1. fermenter                发酵罐（罐号/容量/材质/温控区间/冷却方式/状态/当前批次）
--   2. batch                    啤酒批次（计划→糖化→煮沸→发酵→熟成→已灌装/报废）
--        batch_temp_reading     【@ElementCollection + @MapKeyColumn】逐日温度
--        batch_gravity_reading  【@ElementCollection + @MapKeyColumn】逐日糖度
--   3. recipe / recipe_item     配方 + 原料用量明细
--        recipe_item_slot        Recipe.items 这张 Map 的 key 表（@OneToMany + @MapKeyColumn）
--   4. ingredient               原料库存（麦芽/啤酒花/酵母/辅料/水处理剂）
--   5. bottling / shipment      灌装单 + 出货单
--
-- 种子数据对照：
--   [M1] 七种罐体状态齐活    → MASHING×1 BOILING×1 FERMENTING×3 CONDITIONING×2 CLEANING×1 IDLE×3 DISABLED×1，共 12 罐
--   [M1] 液位按产量/罐容     → FK-02 75%（BC-2026-102 1500L / 2000L）
--   [M2] 批次七种状态齐活    → PLAN×2 MASH×1 BOIL×1 FERMENT×3 CONDITION×2 PACKAGED×3 SCRAPPED×1，共 13 批
--   [M3] Map 用量明细        → 每个配方 4~6 个投放阶段，key 各不相同（BOIL_60 / DRYHOP_1 …）
--   [M4] 低库存标红          → HOP-CIT 18<25、YEAST-W34 24<30、ADJ-CORI 9<12 共 3 条
--   [M5] 金额 = 数量 × 单价  → 12 张出货单，PAID 3 / SHIPPED 4 / PENDING 5
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS batch_temp_reading;
DROP TABLE IF EXISTS batch_gravity_reading;
DROP TABLE IF EXISTS recipe_item_slot;
DROP TABLE IF EXISTS recipe_item;
DROP TABLE IF EXISTS shipment;
DROP TABLE IF EXISTS bottling;
DROP TABLE IF EXISTS ingredient;
DROP TABLE IF EXISTS batch;
DROP TABLE IF EXISTS recipe;
DROP TABLE IF EXISTS fermenter;

-- ---------------- 模块一：发酵罐 ----------------
CREATE TABLE fermenter (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    tank_no          VARCHAR(16)   NOT NULL COMMENT '罐号',
    capacity_l       DECIMAL(10,2) NOT NULL COMMENT '有效容量（L）',
    material         VARCHAR(16)   NOT NULL COMMENT '材质 STAINLESS/JACKETED/CONICAL',
    temp_min         DECIMAL(5,2)           COMMENT '温控下限（℃）',
    temp_max         DECIMAL(5,2)           COMMENT '温控上限（℃）',
    cooling          VARCHAR(16)            COMMENT '冷却方式 GLYCOL/ICE_WATER/AIR',
    status           VARCHAR(16)   NOT NULL COMMENT 'IDLE/MASHING/BOILING/FERMENTING/CONDITIONING/CLEANING/DISABLED',
    current_batch_no VARCHAR(24)            COMMENT '当前批次号',
    updated_at       DATETIME               COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fermenter_tank (tank_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------- 模块三：配方（先建，批次要引用） ----------------
CREATE TABLE recipe (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    code         VARCHAR(24)   NOT NULL COMMENT '配方编码',
    name         VARCHAR(64)   NOT NULL COMMENT '配方名',
    style        VARCHAR(16)   NOT NULL COMMENT 'IPA/STOUT/PILSNER/WHEAT/SOUR/PALE_ALE',
    target_og    DECIMAL(5,3)            COMMENT '目标原浓 OG',
    target_abv   DECIMAL(4,2)            COMMENT '目标酒精度 %vol',
    target_ibu   DECIMAL(5,1)            COMMENT '目标苦度 IBU',
    target_ebc   DECIMAL(5,1)            COMMENT '目标色度 EBC',
    process_note VARCHAR(512)            COMMENT '工艺说明',
    updated_at   DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_recipe_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Recipe.items：@OneToMany + @MapKeyColumn 的 Map。recipe_item 是用量明细本体，
-- Map 的 key（投放阶段）存在这张关联表里 —— (recipe_id, stage) 就是 Map 的 key 约束。
CREATE TABLE recipe_item_slot (
    recipe_id BIGINT      NOT NULL COMMENT '所属配方',
    stage     VARCHAR(24) NOT NULL COMMENT '投放阶段 = Recipe.items 这张 Map 的 key',
    item_id   BIGINT      NOT NULL COMMENT '指向 recipe_item',
    PRIMARY KEY (recipe_id, stage),
    UNIQUE KEY uk_slot_item (item_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE recipe_item (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    ingredient_code VARCHAR(24)   NOT NULL COMMENT '原料编码',
    ingredient_name VARCHAR(64)            COMMENT '原料名称',
    ingredient_type VARCHAR(16)            COMMENT 'MALT/HOP/YEAST/ADJUNCT/WATER',
    quantity        DECIMAL(10,3) NOT NULL COMMENT '用量',
    unit            VARCHAR(8)             COMMENT '单位',
    note            VARCHAR(128)           COMMENT '投放备注',
    PRIMARY KEY (id),
    KEY idx_item_ingredient (ingredient_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------- 模块二：啤酒批次 ----------------
CREATE TABLE batch (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    batch_no        VARCHAR(24)   NOT NULL COMMENT '批号',
    recipe_id       BIGINT                 COMMENT '配方 id',
    fermenter_no    VARCHAR(16)            COMMENT '占用罐号',
    brewer          VARCHAR(32)            COMMENT '酿酒师',
    brew_date       DATE                   COMMENT '酿造日期',
    mash_start      DATETIME               COMMENT '糖化开始',
    mash_end        DATETIME               COMMENT '糖化结束（转煮沸）',
    boil_end        DATETIME               COMMENT '煮沸结束',
    ferment_start   DATETIME               COMMENT '发酵开始',
    expected_mature DATE                   COMMENT '预计成熟日',
    actual_mature   DATE                   COMMENT '实际成熟日',
    volume_l        DECIMAL(10,2)          COMMENT '产量（L）',
    abv             DECIMAL(4,2)           COMMENT '酒精度 %vol',
    ibu             DECIMAL(5,1)           COMMENT '苦度 IBU',
    ebc             DECIMAL(5,1)           COMMENT '色度 EBC',
    status          VARCHAR(16)   NOT NULL COMMENT 'PLAN/MASH/BOIL/FERMENT/CONDITION/PACKAGED/SCRAPPED',
    updated_at      DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_batch_no (batch_no),
    KEY idx_batch_recipe (recipe_id),
    KEY idx_batch_tank (fermenter_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Batch.tempReadings：@ElementCollection Map<String,BigDecimal>，key = D1/D2…
CREATE TABLE batch_temp_reading (
    batch_id   BIGINT       NOT NULL,
    day_key    VARCHAR(16)  NOT NULL COMMENT '第 N 天 = Map 的 key',
    temp_value DECIMAL(5,2)          COMMENT '温度（℃）',
    PRIMARY KEY (batch_id, day_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Batch.gravityReadings：@ElementCollection Map<String,BigDecimal>，key 同上
CREATE TABLE batch_gravity_reading (
    batch_id      BIGINT       NOT NULL,
    day_key       VARCHAR(16)  NOT NULL COMMENT '第 N 天 = Map 的 key',
    gravity_value DECIMAL(5,2)          COMMENT '表观糖度（°P）',
    PRIMARY KEY (batch_id, day_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------- 模块四：原料库存 ----------------
CREATE TABLE ingredient (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    code          VARCHAR(24)   NOT NULL COMMENT '原料编号',
    name          VARCHAR(64)   NOT NULL COMMENT '原料名称',
    type          VARCHAR(16)   NOT NULL COMMENT 'MALT/HOP/YEAST/ADJUNCT/WATER',
    unit          VARCHAR(8)    NOT NULL COMMENT '单位',
    stock         DECIMAL(12,3) NOT NULL COMMENT '当前库存',
    safety_stock  DECIMAL(12,3) NOT NULL COMMENT '安全库存',
    unit_price    DECIMAL(10,2)          COMMENT '单价（元）',
    supplier      VARCHAR(64)            COMMENT '供应商',
    shelf_life_to DATE                   COMMENT '保质期至',
    updated_at    DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ingredient_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------- 模块五：灌装与出货 ----------------
CREATE TABLE bottling (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    bottling_no  VARCHAR(24)   NOT NULL COMMENT '灌装单号',
    batch_no     VARCHAR(24)   NOT NULL COMMENT '批次号',
    package_form VARCHAR(16)   NOT NULL COMMENT 'BOTTLE/CAN/KEG',
    quantity     INT           NOT NULL COMMENT '数量（瓶/罐/桶）',
    bottle_date  DATE                   COMMENT '灌装日期',
    loss_l       DECIMAL(10,2)          COMMENT '灌装损耗（L）',
    updated_at   DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bottling_no (bottling_no),
    KEY idx_bottling_batch (batch_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE shipment (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    shipment_no  VARCHAR(24)   NOT NULL COMMENT '出货单号',
    customer     VARCHAR(64)   NOT NULL COMMENT '客户',
    batch_no     VARCHAR(24)   NOT NULL COMMENT '批次号',
    package_form VARCHAR(16)   NOT NULL COMMENT 'BOTTLE/CAN/KEG',
    quantity     INT           NOT NULL COMMENT '数量',
    unit_price   DECIMAL(10,2) NOT NULL COMMENT '单价（元）',
    amount       DECIMAL(12,2) NOT NULL COMMENT '金额 = 数量 × 单价',
    ship_date    DATE                   COMMENT '出货日期',
    status       VARCHAR(16)   NOT NULL COMMENT 'PENDING/SHIPPED/PAID',
    updated_at   DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shipment_no (shipment_no),
    KEY idx_shipment_batch (batch_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ============================================================================
-- 种子数据
-- ============================================================================

-- ---------------- 4. 原料库存（17 条） ----------------
INSERT INTO ingredient (id, code, name, type, unit, stock, safety_stock, unit_price, supplier, shelf_life_to, updated_at) VALUES
(1,  'MALT-PIL',  '皮尔森麦芽',      'MALT',    'kg',  480.000, 120.000,   6.50, '中粮麦芽',   '2027-03-31', NOW()),
(2,  'MALT-MUN',  '慕尼黑麦芽',      'MALT',    'kg',  320.000,  80.000,   7.20, '中粮麦芽',   '2027-04-30', NOW()),
(3,  'MALT-CAR',  '水晶麦芽',        'MALT',    'kg',  150.000,  60.000,   9.80, '齐鲁麦芽',   '2027-02-28', NOW()),
(4,  'MALT-CHOC', '巧克力麦芽',      'MALT',    'kg',   95.000,  40.000,  11.50, '齐鲁麦芽',   '2027-01-31', NOW()),
(5,  'MALT-WHEAT','小麦芽',          'MALT',    'kg',  260.000,  90.000,   6.90, '中粮麦芽',   '2027-03-15', NOW()),
(6,  'MALT-OAT',  '燕麦片',          'MALT',    'kg',  110.000,  50.000,   8.40, '齐鲁麦芽',   '2026-11-30', NOW()),
(7,  'HOP-CAS',   '卡斯卡特酒花',    'HOP',     'kg',   42.000,  20.000, 168.00, '新疆三坪酒花','2027-06-30', NOW()),
(8,  'HOP-CIT',   '西楚酒花',        'HOP',     'kg',   18.000,  25.000, 260.00, '新疆三坪酒花','2027-06-30', NOW()),
(9,  'HOP-MOS',   '摩西酒花',        'HOP',     'kg',   36.000,  15.000, 220.00, '甘肃裕丰',   '2027-05-31', NOW()),
(10, 'HOP-SAAZ',  '萨兹酒花',        'HOP',     'kg',   22.000,  12.000, 245.00, '捷克酒花行', '2027-04-30', NOW()),
(11, 'YEAST-US05','US-05 艾尔酵母',  'YEAST',   'pkg',  90.000,  30.000,  38.00, '弗曼迪斯',   '2026-12-31', NOW()),
(12, 'YEAST-W34', 'W-34/70 拉格酵母','YEAST',   'pkg',  24.000,  30.000,  42.00, '韦亨斯蒂芬', '2026-12-31', NOW()),
(13, 'ADJ-LAC',   '乳酸菌',          'ADJUNCT', 'pkg',  40.000,  15.000,  55.00, '佰生优选',   '2026-10-31', NOW()),
(14, 'ADJ-CITR',  '陈皮',            'ADJUNCT', 'kg',   28.000,  10.000,  46.00, '广州果脯',   '2027-01-15', NOW()),
(15, 'ADJ-CORI',  '芫荽籽',          'ADJUNCT', 'kg',    9.000,  12.000,  68.00, '广州果脯',   '2027-02-28', NOW()),
(16, 'WAT-GYP',   '酿造石膏',        'WATER',   'kg',   60.000,  25.000,  12.00, '润水化学',   '2028-01-31', NOW()),
(17, 'ADJ-CHER',  '樱桃果泥',        'ADJUNCT', 'kg',  120.000,  40.000,  22.00, '大连果源',   '2026-12-15', NOW());

-- ---------------- 3. 配方（10 条） ----------------
INSERT INTO recipe (id, code, name, style, target_og, target_abv, target_ibu, target_ebc, process_note, updated_at) VALUES
(1,  'R-1001', '晨雾 IPA',         'IPA',      1.062, 6.50, 65.0, 12.0, '单步糖化 66℃ 60 分钟，煮沸 60 分钟，全程干投一次', NOW()),
(2,  'R-1002', '双倍干投浑浊 IPA', 'IPA',      1.075, 8.20, 75.0, 14.0, '蛋白休止 52℃ 15 分钟，两次干投，冷贮前不滤',     NOW()),
(3,  'R-1003', '午夜世涛',         'STOUT',    1.068, 7.00, 45.0, 78.0, '深色麦芽糖化末期投放，避免涩感',                  NOW()),
(4,  'R-1004', '燕麦咖啡世涛',     'STOUT',    1.072, 7.80, 40.0, 82.0, '燕麦片 8%，熟成 21 天后加冷萃咖啡液',            NOW()),
(5,  'R-1005', '波西米亚皮尔森',   'PILSNER',  1.048, 4.80, 38.0,  6.0, '三段煮出糖化，萨兹酒花三次投放，低温长贮 28 天',  NOW()),
(6,  'R-1006', '德式小麦白啤',     'WHEAT',    1.050, 5.20, 15.0,  9.0, '小麦芽占比 55%，香蕉丁香风味靠 20℃ 发酵 4 天',   NOW()),
(7,  'R-1007', '比利时小麦 Wit',   'WHEAT',    1.048, 4.90, 12.0,  8.0, '生小麦芽 40%，陈皮与芫荽籽末段投放',              NOW()),
(8,  'R-1008', '樱桃兰比克',       'SOUR',     1.052, 5.60,  8.0, 22.0, '陈年酒花，自然接种后橡木桶陈放 6 个月',            NOW()),
(9,  'R-1009', '海盐酸柠檬古斯',   'SOUR',     1.044, 4.40,  6.0,  7.0, '乳酸菌酸化 24 小时后再煮沸，加海盐 0.3%',        NOW()),
(10, 'R-1010', '落日淡色艾尔',     'PALE_ALE', 1.054, 5.40, 35.0, 11.0, '水晶麦芽提色，干投一次做柑橘收尾',                NOW());

-- ---------------- 3. 配方 Map 用量明细（49 条：明细本体 + Map 的 key 映射） ----------------
INSERT INTO recipe_item (id, ingredient_code, ingredient_name, ingredient_type, quantity, unit, note) VALUES
(1, 'MALT-PIL', '皮尔森麦芽', 'MALT', 220.000, 'kg', '基础麦芽'),
(2, 'HOP-CAS', '卡斯卡特酒花', 'HOP', 1.200, 'kg', '苦花 60 分钟'),
(3, 'HOP-CAS', '卡斯卡特酒花', 'HOP', 0.800, 'kg', '香花 5 分钟'),
(4, 'HOP-CIT', '西楚酒花', 'HOP', 1.000, 'kg', '回旋投放'),
(5, 'YEAST-US05', 'US-05 艾尔酵母', 'YEAST', 2.000, 'pkg', '18.5℃ 接种'),
(6, 'MALT-PIL', '皮尔森麦芽', 'MALT', 260.000, 'kg', '蛋白休止后 66℃ 糖化'),
(7, 'HOP-MOS', '摩西酒花', 'HOP', 1.500, 'kg', '苦花'),
(8, 'HOP-CIT', '西楚酒花', 'HOP', 1.500, 'kg', '回旋大量投放'),
(9, 'YEAST-US05', 'US-05 艾尔酵母', 'YEAST', 3.000, 'pkg', '19℃ 接种'),
(10, 'HOP-CIT', '西楚酒花', 'HOP', 1.200, 'kg', '一次干投 3 天'),
(11, 'HOP-MOS', '摩西酒花', 'HOP', 0.900, 'kg', '二次干投 2 天'),
(12, 'MALT-PIL', '皮尔森麦芽', 'MALT', 200.000, 'kg', '基础麦芽'),
(13, 'MALT-CHOC', '巧克力麦芽', 'MALT', 60.000, 'kg', '糖化末期投放'),
(14, 'HOP-CAS', '卡斯卡特酒花', 'HOP', 0.900, 'kg', '苦花'),
(15, 'YEAST-US05', 'US-05 艾尔酵母', 'YEAST', 2.000, 'pkg', '19℃ 接种'),
(16, 'MALT-MUN', '慕尼黑麦芽', 'MALT', 230.000, 'kg', '基础麦芽'),
(17, 'MALT-OAT', '燕麦片', 'MALT', 55.000, 'kg', '增加丝滑口感'),
(18, 'HOP-CAS', '卡斯卡特酒花', 'HOP', 1.100, 'kg', '苦花'),
(19, 'YEAST-US05', 'US-05 艾尔酵母', 'YEAST', 2.000, 'pkg', '19℃ 接种'),
(20, 'WAT-GYP', '酿造石膏', 'WATER', 0.800, 'kg', '调低残余碱度'),
(21, 'MALT-PIL', '皮尔森麦芽', 'MALT', 240.000, 'kg', '三段煮出'),
(22, 'HOP-SAAZ', '萨兹酒花', 'HOP', 1.600, 'kg', '苦花 60 分钟'),
(23, 'HOP-SAAZ', '萨兹酒花', 'HOP', 0.600, 'kg', '香花 5 分钟'),
(24, 'YEAST-W34', 'W-34/70 拉格酵母', 'YEAST', 3.000, 'pkg', '9℃ 接种'),
(25, 'MALT-WHEAT', '小麦芽', 'MALT', 150.000, 'kg', '小麦 55%'),
(26, 'MALT-PIL', '皮尔森麦芽', 'MALT', 120.000, 'kg', '糖化末期补投'),
(27, 'HOP-MOS', '摩西酒花', 'HOP', 0.700, 'kg', '苦花'),
(28, 'YEAST-W34', 'W-34/70 拉格酵母', 'YEAST', 3.000, 'pkg', '20℃ 高温小麦发酵'),
(29, 'MALT-WHEAT', '小麦芽', 'MALT', 160.000, 'kg', '生小麦 40%'),
(30, 'ADJ-CITR', '陈皮', 'ADJUNCT', 0.600, 'kg', '随洗糟水投放'),
(31, 'HOP-SAAZ', '萨兹酒花', 'HOP', 0.800, 'kg', '苦花'),
(32, 'ADJ-CORI', '芫荽籽', 'ADJUNCT', 0.400, 'kg', '末段提香'),
(33, 'YEAST-US05', 'US-05 艾尔酵母', 'YEAST', 2.000, 'pkg', '20℃ 接种'),
(34, 'MALT-PIL', '皮尔森麦芽', 'MALT', 180.000, 'kg', '基础麦芽'),
(35, 'MALT-WHEAT', '小麦芽', 'MALT', 45.000, 'kg', '生小麦增浑浊'),
(36, 'HOP-SAAZ', '萨兹酒花', 'HOP', 0.500, 'kg', '陈年酒花，只做防腐'),
(37, 'ADJ-CHER', '樱桃果泥', 'ADJUNCT', 45.000, 'kg', '主发酵后投放'),
(38, 'ADJ-LAC', '乳酸菌', 'ADJUNCT', 3.000, 'pkg', '自然接种'),
(39, 'MALT-PIL', '皮尔森麦芽', 'MALT', 150.000, 'kg', '基础麦芽'),
(40, 'MALT-WHEAT', '小麦芽', 'MALT', 60.000, 'kg', '小麦提供蛋白'),
(41, 'ADJ-CORI', '芫荽籽', 'ADJUNCT', 0.300, 'kg', '30 分钟投放'),
(42, 'ADJ-CITR', '陈皮', 'ADJUNCT', 0.900, 'kg', '回旋增香'),
(43, 'ADJ-LAC', '乳酸菌', 'ADJUNCT', 2.000, 'pkg', '酸化 24 小时'),
(44, 'MALT-PIL', '皮尔森麦芽', 'MALT', 210.000, 'kg', '基础麦芽'),
(45, 'MALT-CAR', '水晶麦芽', 'MALT', 35.000, 'kg', '提色增甜'),
(46, 'HOP-CAS', '卡斯卡特酒花', 'HOP', 0.900, 'kg', '苦花'),
(47, 'HOP-MOS', '摩西酒花', 'HOP', 0.600, 'kg', '香花'),
(48, 'HOP-CIT', '西楚酒花', 'HOP', 0.700, 'kg', '干投一次'),
(49, 'YEAST-US05', 'US-05 艾尔酵母', 'YEAST', 2.000, 'pkg', '18.5℃ 接种');

INSERT INTO recipe_item_slot (recipe_id, stage, item_id) VALUES
(1, 'MASH_IN', 1),
(1, 'BOIL_60', 2),
(1, 'BOIL_5', 3),
(1, 'WHIRLPOOL', 4),
(1, 'PITCH', 5),
(2, 'MASH_IN', 6),
(2, 'BOIL_60', 7),
(2, 'WHIRLPOOL', 8),
(2, 'PITCH', 9),
(2, 'DRYHOP_1', 10),
(2, 'DRYHOP_2', 11),
(3, 'MASH_IN', 12),
(3, 'MASH_OUT', 13),
(3, 'BOIL_60', 14),
(3, 'PITCH', 15),
(4, 'MASH_IN', 16),
(4, 'MASH_OUT', 17),
(4, 'BOIL_60', 18),
(4, 'PITCH', 19),
(5, 'WATER', 20),
(5, 'MASH_IN', 21),
(5, 'BOIL_60', 22),
(5, 'BOIL_5', 23),
(5, 'PITCH', 24),
(6, 'MASH_IN', 25),
(6, 'MASH_OUT', 26),
(6, 'BOIL_60', 27),
(6, 'PITCH', 28),
(7, 'MASH_IN', 29),
(7, 'SPARGE', 30),
(7, 'BOIL_60', 31),
(7, 'BOIL_5', 32),
(7, 'PITCH', 33),
(8, 'MASH_IN', 34),
(8, 'SPARGE', 35),
(8, 'BOIL_60', 36),
(8, 'FRUIT', 37),
(8, 'PITCH', 38),
(9, 'MASH_IN', 39),
(9, 'SPARGE', 40),
(9, 'BOIL_30', 41),
(9, 'WHIRLPOOL', 42),
(9, 'PITCH', 43),
(10, 'MASH_IN', 44),
(10, 'MASH_OUT', 45),
(10, 'BOIL_60', 46),
(10, 'BOIL_5', 47),
(10, 'DRYHOP_1', 48),
(10, 'PITCH', 49);

-- ---------------- 2. 啤酒批次（13 条） ----------------
INSERT INTO batch (id, batch_no, recipe_id, fermenter_no, brewer, brew_date, mash_start, mash_end, boil_end, ferment_start, expected_mature, actual_mature, volume_l, abv, ibu, ebc, status, updated_at) VALUES
(1,  'BC-2026-101', 1,  'FK-01', '王海涛', '2026-09-20', '2026-09-20 08:10:00', NULL, NULL, NULL, NULL, NULL,
     1200.00, NULL, NULL, NULL, 'MASH', NOW()),
(2,  'BC-2026-102', 5,  'FK-02', '李承泽', '2026-09-19', '2026-09-19 07:50:00', '2026-09-19 09:20:00', NULL, NULL, NULL, NULL,
     1500.00, NULL, NULL, NULL, 'BOIL', NOW()),
(3,  'BC-2026-103', 2,  'FK-03', '陈墨',   '2026-09-10', '2026-09-10 08:00:00', '2026-09-10 09:30:00', '2026-09-10 11:00:00', '2026-09-10 14:00:00', '2026-09-28', NULL,
     2100.00, NULL, NULL, NULL, 'FERMENT', NOW()),
(4,  'BC-2026-104', 3,  'FK-04', '陈墨',   '2026-09-12', '2026-09-12 08:20:00', '2026-09-12 09:50:00', '2026-09-12 11:20:00', '2026-09-12 15:00:00', '2026-09-30', NULL,
     2200.00, NULL, NULL, NULL, 'FERMENT', NOW()),
(5,  'BC-2026-105', 6,  'FK-05', '周敏',   '2026-09-15', '2026-09-15 09:00:00', '2026-09-15 10:30:00', '2026-09-15 12:00:00', '2026-09-15 16:30:00', '2026-10-02', NULL,
     800.00, NULL, NULL, NULL, 'FERMENT', NOW()),
(6,  'BC-2026-106', 4,  'FK-06', '王海涛', '2026-08-30', '2026-08-30 08:00:00', '2026-08-30 09:30:00', '2026-08-30 11:10:00', '2026-08-30 18:00:00', '2026-09-22', NULL,
     780.00, 7.90, 39.0, 80.0, 'CONDITION', NOW()),
(7,  'BC-2026-107', 7,  'FK-07', '周敏',   '2026-08-25', '2026-08-25 08:30:00', '2026-08-25 10:00:00', '2026-08-25 11:40:00', '2026-08-25 17:00:00', '2026-09-20', NULL,
     760.00, 4.90, 12.0, 8.0, 'CONDITION', NOW()),
(8,  'BC-2026-108', 8,  NULL,    '李承泽', '2026-08-01', '2026-08-01 08:00:00', '2026-08-01 09:30:00', '2026-08-01 11:00:00', '2026-08-01 20:00:00', '2026-08-22', '2026-08-25',
     420.00, 5.50, 8.0, 22.0, 'PACKAGED', NOW()),
(9,  'BC-2026-109', 9,  NULL,    '周敏',   '2026-08-05', '2026-08-05 08:10:00', '2026-08-05 09:40:00', '2026-08-05 11:10:00', '2026-08-05 19:00:00', '2026-08-28', '2026-08-30',
     400.00, 4.40, 6.0, 7.0, 'PACKAGED', NOW()),
(10, 'BC-2026-110', 10, NULL,    '王海涛', '2026-08-12', '2026-08-12 08:00:00', '2026-08-12 09:30:00', '2026-08-12 11:00:00', '2026-08-12 18:30:00', '2026-09-02', '2026-09-05',
     1500.00, 5.40, 35.0, 11.0, 'PACKAGED', NOW()),
(11, 'BC-2026-111', 2,  NULL,    '陈墨',   '2026-08-18', '2026-08-18 08:00:00', '2026-08-18 09:30:00', '2026-08-18 11:00:00', '2026-08-18 17:30:00', '2026-09-08', NULL,
     1900.00, NULL, NULL, NULL, 'SCRAPPED', NOW()),
(12, 'BC-2026-112', 3,  NULL,    '李承泽', '2026-09-24', NULL, NULL, NULL, NULL, NULL, NULL,
     2000.00, NULL, NULL, NULL, 'PLAN', NOW()),
(13, 'BC-2026-113', 6,  NULL,    '周敏',   '2026-09-26', NULL, NULL, NULL, NULL, NULL, NULL,
     800.00, NULL, NULL, NULL, 'PLAN', NOW());

-- ---------------- 2. 逐日温度曲线（Map<String,BigDecimal>） ----------------
INSERT INTO batch_temp_reading (batch_id, day_key, temp_value) VALUES
(3,  'D1', 18.80), (3,  'D2', 19.20), (3,  'D3', 19.60), (3,  'D4', 20.10), (3,  'D5', 20.00), (3,  'D6', 19.50), (3,  'D7', 19.10),
(4,  'D1', 18.50), (4,  'D2', 19.00), (4,  'D3', 19.40), (4,  'D4', 19.80), (4,  'D5', 19.60), (4,  'D6', 19.20),
(5,  'D1', 20.20), (5,  'D2', 20.60), (5,  'D3', 21.00), (5,  'D4', 20.80), (5,  'D5', 20.40), (5,  'D6', 20.00),
(6,  'D1',  2.00), (6,  'D2',  1.80), (6,  'D3',  1.60), (6,  'D4',  1.40), (6,  'D5',  1.20), (6,  'D6',  1.00),
(7,  'D1',  3.20), (7,  'D2',  2.80), (7,  'D3',  2.40), (7,  'D4',  2.00), (7,  'D5',  1.60), (7,  'D6',  1.20),
(8,  'D1', 22.50), (8,  'D2', 23.10), (8,  'D3', 23.40), (8,  'D4', 22.80), (8,  'D5', 21.60), (8,  'D6', 20.20),
(9,  'D1', 21.80), (9,  'D2', 22.20), (9,  'D3', 22.00), (9,  'D4', 21.40), (9,  'D5', 20.80),
(10, 'D1', 18.60), (10, 'D2', 19.00), (10, 'D3', 19.30), (10, 'D4', 19.10), (10, 'D5', 18.70), (10,'D6', 18.20);

-- ---------------- 2. 逐日糖度曲线（Map<String,BigDecimal>） ----------------
INSERT INTO batch_gravity_reading (batch_id, day_key, gravity_value) VALUES
(3,  'D1', 16.20), (3,  'D2', 13.80), (3,  'D3', 10.40), (3,  'D4',  7.60), (3,  'D5',  5.80), (3,  'D6',  4.60), (3,  'D7',  4.10),
(4,  'D1', 15.40), (4,  'D2', 13.20), (4,  'D3', 10.80), (4,  'D4',  8.40), (4,  'D5',  6.20), (4,  'D6',  5.00),
(5,  'D1', 12.80), (5,  'D2', 10.60), (5,  'D3',  8.20), (5,  'D4',  6.40), (5,  'D5',  5.20), (5,  'D6',  4.40),
(6,  'D1',  6.80), (6,  'D2',  5.60), (6,  'D3',  4.80), (6,  'D4',  4.30), (6,  'D5',  4.00), (6,  'D6',  3.80),
(7,  'D1',  6.20), (7,  'D2',  5.20), (7,  'D3',  4.60), (7,  'D4',  4.20), (7,  'D5',  3.90), (7,  'D6',  3.70),
(8,  'D1', 13.60), (8,  'D2', 10.20), (8,  'D3',  7.40), (8,  'D4',  5.60), (8,  'D5',  4.40), (8,  'D6',  3.90),
(9,  'D1', 11.40), (9,  'D2',  8.60), (9,  'D3',  6.20), (9,  'D4',  4.80), (9,  'D5',  3.90),
(10, 'D1', 13.20), (10, 'D2', 11.00), (10, 'D3',  8.40), (10, 'D4',  6.20), (10, 'D5',  4.80), (10,'D6',  4.00);

-- ---------------- 1. 发酵罐（12 条） ----------------
INSERT INTO fermenter (id, tank_no, capacity_l, material, temp_min, temp_max, cooling, status, current_batch_no, updated_at) VALUES
(1,  'FK-01', 2000.00, 'STAINLESS',  8.00, 14.00, 'GLYCOL',    'MASHING',      'BC-2026-101', NOW()),
(2,  'FK-02', 2000.00, 'JACKETED',  10.00, 16.00, 'ICE_WATER', 'BOILING',      'BC-2026-102', NOW()),
(3,  'FK-03', 3000.00, 'CONICAL',   16.00, 22.00, 'GLYCOL',    'FERMENTING',   'BC-2026-103', NOW()),
(4,  'FK-04', 3000.00, 'CONICAL',   16.00, 22.00, 'GLYCOL',    'FERMENTING',   'BC-2026-104', NOW()),
(5,  'FK-05', 1000.00, 'STAINLESS', 17.00, 21.00, 'GLYCOL',    'FERMENTING',   'BC-2026-105', NOW()),
(6,  'FK-06', 1000.00, 'CONICAL',    0.00,  4.00, 'GLYCOL',    'CONDITIONING', 'BC-2026-106', NOW()),
(7,  'FK-07', 1000.00, 'CONICAL',    0.00,  4.00, 'ICE_WATER', 'CONDITIONING', 'BC-2026-107', NOW()),
(8,  'FK-08',  500.00, 'JACKETED',   0.00,  6.00, 'GLYCOL',    'CLEANING',     NULL,          NOW()),
(9,  'FK-09',  500.00, 'STAINLESS',  0.00,  6.00, 'AIR',       'IDLE',         NULL,          NOW()),
(10, 'FK-10', 2000.00, 'CONICAL',   18.00, 24.00, 'GLYCOL',    'IDLE',         NULL,          NOW()),
(11, 'FK-11', 3000.00, 'STAINLESS', 15.00, 22.00, 'AIR',       'DISABLED',     NULL,          NOW()),
(12, 'FK-12', 1000.00, 'JACKETED',  12.00, 20.00, 'GLYCOL',    'IDLE',         NULL,          NOW());

-- ---------------- 5. 灌装单（12 条） ----------------
INSERT INTO bottling (id, bottling_no, batch_no, package_form, quantity, bottle_date, loss_l, updated_at) VALUES
(1,  'BT-2026-01', 'BC-2026-108', 'BOTTLE', 330, '2026-08-26', 12.00, NOW()),
(2,  'BT-2026-02', 'BC-2026-108', 'CAN',    480, '2026-08-26',  9.50, NOW()),
(3,  'BT-2026-03', 'BC-2026-108', 'KEG',     12, '2026-08-27',  4.00, NOW()),
(4,  'BT-2026-04', 'BC-2026-109', 'BOTTLE', 300, '2026-09-02', 10.50, NOW()),
(5,  'BT-2026-05', 'BC-2026-109', 'KEG',     10, '2026-09-02',  3.20, NOW()),
(6,  'BT-2026-06', 'BC-2026-110', 'CAN',    720, '2026-09-06', 15.00, NOW()),
(7,  'BT-2026-07', 'BC-2026-110', 'BOTTLE', 540, '2026-09-06', 11.80, NOW()),
(8,  'BT-2026-08', 'BC-2026-110', 'KEG',     20, '2026-09-07',  5.50, NOW()),
(9,  'BT-2026-09', 'BC-2026-106', 'BOTTLE', 260, '2026-09-20',  8.40, NOW()),
(10, 'BT-2026-10', 'BC-2026-107', 'CAN',    360, '2026-09-18',  9.20, NOW()),
(11, 'BT-2026-11', 'BC-2026-103', 'KEG',     18, '2026-09-22',  6.00, NOW()),
(12, 'BT-2026-12', 'BC-2026-104', 'BOTTLE', 480, '2026-09-21', 13.60, NOW());

-- ---------------- 5. 出货单（12 条，金额 = 数量 × 单价） ----------------
INSERT INTO shipment (id, shipment_no, customer, batch_no, package_form, quantity, unit_price, amount, ship_date, status, updated_at) VALUES
(1,  'SH-2026-01', '麦芽盒子酒吧',   'BC-2026-108', 'BOTTLE', 200, 18.00,  3600.00, '2026-08-28', 'PAID',    NOW()),
(2,  'SH-2026-02', '麦芽盒子酒吧',   'BC-2026-108', 'CAN',    300, 15.00,  4500.00, '2026-08-30', 'PAID',    NOW()),
(3,  'SH-2026-03', '胡桃里精酿',     'BC-2026-108', 'KEG',     10, 680.00, 6800.00, '2026-08-29', 'PAID',    NOW()),
(4,  'SH-2026-04', '啤氣酒馆',       'BC-2026-109', 'BOTTLE', 240, 19.50,  4680.00, '2026-09-04', 'SHIPPED', NOW()),
(5,  'SH-2026-05', '麦浪餐吧',       'BC-2026-109', 'KEG',      8, 720.00, 5760.00, '2026-09-05', 'SHIPPED', NOW()),
(6,  'SH-2026-06', '第十五号酒仓',   'BC-2026-110', 'CAN',    600, 14.80,  8880.00, '2026-09-08', 'SHIPPED', NOW()),
(7,  'SH-2026-07', '夜航西飞酒吧',   'BC-2026-110', 'BOTTLE', 400, 17.60,  7040.00, '2026-09-09', 'SHIPPED', NOW()),
(8,  'SH-2026-08', '麦芽盒子酒吧',   'BC-2026-110', 'KEG',     16, 690.00, 11040.00,'2026-09-15', 'PENDING', NOW()),
(9,  'SH-2026-09', '胡桃里精酿',     'BC-2026-106', 'BOTTLE', 200, 22.00,  4400.00, '2026-09-22', 'PENDING', NOW()),
(10, 'SH-2026-10', '麦浪餐吧',       'BC-2026-107', 'CAN',    280, 16.50,  4620.00, '2026-09-22', 'PENDING', NOW()),
(11, 'SH-2026-11', '夜航西飞酒吧',   'BC-2026-103', 'KEG',     14, 660.00, 9240.00, '2026-09-23', 'PENDING', NOW()),
(12, 'SH-2026-12', '啤氣酒馆',       'BC-2026-104', 'BOTTLE', 360, 18.80,  6768.00, '2026-09-24', 'PENDING', NOW());

SET FOREIGN_KEY_CHECKS = 1;
