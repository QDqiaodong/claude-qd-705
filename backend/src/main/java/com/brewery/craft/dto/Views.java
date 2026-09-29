package com.brewery.craft.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.brewery.craft.domain.Batch;
import com.brewery.craft.domain.Bottling;
import com.brewery.craft.domain.Fermenter;
import com.brewery.craft.domain.Ingredient;
import com.brewery.craft.domain.QcRound;
import com.brewery.craft.domain.Recipe;
import com.brewery.craft.domain.RecipeItem;
import com.brewery.craft.domain.Shipment;

import lombok.Data;

/**
 * 出参视图 + 酿酒业务词典。
 *
 * 说明：common 包里的 Dict 由骨架Generator生成，本仓的业务字典集中在这里，
 * 把一个 {@code Fermenter} / {@code Batch} 之类的实体翻译成前端直接可渲染的 VO。
 */
public final class Views {

    private Views() {
    }

    // ---------------- 罐体材质 ----------------
    private static final Map<String, String> MATERIAL = Map.of(
            "STAINLESS", "不锈钢",
            "JACKETED", "夹套",
            "CONICAL", "锥底");

    // ---------------- 冷却方式 ----------------
    private static final Map<String, String> COOLING = Map.of(
            "GLYCOL", "乙二醇夹套",
            "ICE_WATER", "冰水盘管",
            "AIR", "强制风冷");

    // ---------------- 罐体状态（= 当前所处酿造阶段） ----------------
    private static final Map<String, String> TANK_STATUS = Map.of(
            "IDLE", "空闲",
            "MASHING", "糖化",
            "BOILING", "煮沸",
            "FERMENTING", "发酵",
            "CONDITIONING", "熟成",
            "CLEANING", "清洗",
            "DISABLED", "停用");

    /** 阶段在页面上从左到右的排列顺序 */
    public static final List<String> TANK_STATUS_ORDER = List.of(
            "MASHING", "BOILING", "FERMENTING", "CONDITIONING", "CLEANING", "IDLE", "DISABLED");

    /** 阶段对应液位配色 */
    private static final Map<String, String> TANK_STATUS_COLOR = Map.of(
            "IDLE", "#c8d3c0",
            "MASHING", "#f0d58a",
            "BOILING", "#ef9b52",
            "FERMENTING", "#8bc34a",
            "CONDITIONING", "#d99a2b",
            "CLEANING", "#9aa5ab",
            "DISABLED", "#7a8288");

    // ---------------- 啤酒风格 ----------------
    private static final Map<String, String> STYLE = Map.of(
            "IPA", "IPA",
            "STOUT", "世涛",
            "PILSNER", "皮尔森",
            "WHEAT", "小麦",
            "SOUR", "酸啤",
            "PALE_ALE", "淡色艾尔");

    // ---------------- 批次状态流转 ----------------
    private static final Map<String, String> BATCH_STATUS = Map.of(
            "PLAN", "计划",
            "MASH", "糖化",
            "BOIL", "煮沸",
            "FERMENT", "发酵",
            "CONDITION", "熟成",
            "PACKAGED", "已灌装",
            "SCRAPPED", "报废");

    /** 批次推进链路：key 的下一站是 value */
    private static final Map<String, String> BATCH_NEXT = Map.of(
            "PLAN", "MASH",
            "MASH", "BOIL",
            "BOIL", "FERMENT",
            "FERMENT", "CONDITION",
            "CONDITION", "PACKAGED");

    /** 批次状态 → 罐体状态（批次推进时同步刷罐） */
    private static final Map<String, String> BATCH_TO_TANK = Map.of(
            "PLAN", "IDLE",
            "MASH", "MASHING",
            "BOIL", "BOILING",
            "FERMENT", "FERMENTING",
            "CONDITION", "CONDITIONING",
            "PACKAGED", "CLEANING",
            "SCRAPPED", "CLEANING");

    // ---------------- 原料类型 ----------------
    private static final Map<String, String> INGREDIENT_TYPE = Map.of(
            "MALT", "麦芽",
            "HOP", "啤酒花",
            "YEAST", "酵母",
            "ADJUNCT", "辅料",
            "WATER", "水处理剂");

    /** 原料类型配色（配方卡 conic-gradient 环形图用） */
    private static final Map<String, String> INGREDIENT_COLOR = Map.of(
            "MALT", "#8d6e3f",
            "HOP", "#7cb342",
            "YEAST", "#c9a227",
            "ADJUNCT", "#26a69a",
            "WATER", "#5c9dd6");

    public static final List<String> INGREDIENT_TYPE_ORDER = List.of("MALT", "HOP", "YEAST", "ADJUNCT", "WATER");

    // ---------------- 投放阶段（Map 的 key） ----------------
    private static final Map<String, String> STAGE = Map.ofEntries(
            Map.entry("WATER", "水处理"),
            Map.entry("MASH_IN", "糖化投料"),
            Map.entry("MASH_OUT", "糖化终止"),
            Map.entry("SPARGE", "洗糟"),
            Map.entry("BOIL_60", "煮沸60分钟"),
            Map.entry("BOIL_30", "煮沸30分钟"),
            Map.entry("BOIL_5", "煮沸5分钟"),
            Map.entry("WHIRLPOOL", "回旋沉淀"),
            Map.entry("PITCH", "接种酵母"),
            Map.entry("FRUIT", "加果泥"),
            Map.entry("DRYHOP_1", "干投一次"),
            Map.entry("DRYHOP_2", "干投二次"));

    /** 投放阶段在 UI 上的固定顺序（Map 的 key 排序依据） */
    public static final List<String> STAGE_ORDER = List.of(
            "WATER", "MASH_IN", "MASH_OUT", "SPARGE",
            "BOIL_60", "BOIL_30", "BOIL_5", "WHIRLPOOL",
            "PITCH", "FRUIT", "DRYHOP_1", "DRYHOP_2");

    // ---------------- 包装形式 ----------------
    private static final Map<String, String> PACKAGE_FORM = Map.of(
            "BOTTLE", "瓶",
            "CAN", "易拉罐",
            "KEG", "桶");

    // ---------------- 出货状态 ----------------
    private static final Map<String, String> SHIPMENT_STATUS = Map.of(
            "PENDING", "待发",
            "SHIPPED", "已发",
            "PAID", "已结款");

    private static final Map<String, String> SHIPMENT_NEXT = Map.of(
            "PENDING", "SHIPPED",
            "SHIPPED", "PAID");

    // ---------------- 质检轮次结论（一行一轮，只增不改） ----------------
    private static final Map<String, String> QC_RESULT = Map.of(
            "OPEN", "送检中",
            "PASSED", "放行",
            "REJECTED", "驳回",
            "REVOKED", "已撤销");

    /** 当前有效质检态（按最新轮次派生） */
    private static final Map<String, String> QC_STATE = Map.of(
            "NONE", "待质检",
            "OPEN", "待质检",
            "RECHECK", "复检中",
            "PASSED", "已放行",
            "REJECTED", "已驳回",
            "REVOKED", "已撤销");

    private static final Map<String, String> QC_STATE_COLOR = Map.of(
            "NONE", "#9aa5ab",
            "OPEN", "#ef9b52",
            "RECHECK", "#d99a2b",
            "PASSED", "#4b8a25",
            "REJECTED", "#c0392b",
            "REVOKED", "#7a4e9e");

    // ---------------- 词典取值 ----------------
    public static String materialName(String v) {
        return MATERIAL.getOrDefault(v, v == null ? "" : v);
    }

    public static String coolingName(String v) {
        return COOLING.getOrDefault(v, v == null ? "" : v);
    }

    public static String tankStatusName(String v) {
        return TANK_STATUS.getOrDefault(v, v == null ? "" : v);
    }

    public static String tankStatusColor(String v) {
        return TANK_STATUS_COLOR.getOrDefault(v, "#8d9aa5");
    }

    public static String styleName(String v) {
        return STYLE.getOrDefault(v, v == null ? "" : v);
    }

    public static String batchStatusName(String v) {
        return BATCH_STATUS.getOrDefault(v, v == null ? "" : v);
    }

    public static String batchNextStatus(String v) {
        return BATCH_NEXT.get(v);
    }

    public static String tankStatusOfBatch(String batchStatus) {
        return BATCH_TO_TANK.getOrDefault(batchStatus, "IDLE");
    }

    public static String ingredientTypeName(String v) {
        return INGREDIENT_TYPE.getOrDefault(v, v == null ? "" : v);
    }

    public static String ingredientColor(String v) {
        return INGREDIENT_COLOR.getOrDefault(v, "#9aa5ab");
    }

    public static String stageName(String v) {
        return STAGE.getOrDefault(v, v == null ? "" : v);
    }

    public static String packageFormName(String v) {
        return PACKAGE_FORM.getOrDefault(v, v == null ? "" : v);
    }

    public static String shipmentStatusName(String v) {
        return SHIPMENT_STATUS.getOrDefault(v, v == null ? "" : v);
    }

    public static String shipmentNextStatus(String v) {
        return SHIPMENT_NEXT.get(v);
    }

    public static String qcResultName(String v) {
        return QC_RESULT.getOrDefault(v, v == null ? "" : v);
    }

    public static String qcStateName(String v) {
        return QC_STATE.getOrDefault(v, v == null ? "" : v);
    }

    public static String qcStateColor(String v) {
        return QC_STATE_COLOR.getOrDefault(v, "#8d9aa5");
    }

    /** "D1" "D10" 这类 Day key 按数字排序 */
    public static int dayKeyOrder(String key) {
        if (key == null) {
            return Integer.MAX_VALUE;
        }
        String digits = key.replaceAll("\\D", "");
        return digits.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(digits);
    }

    // ---------------- 出参视图 ----------------
    @Data
    public static class FermenterView {

        private Long id;
        private String tankNo;
        private BigDecimal capacityL;
        private String material;
        private String materialName;
        private BigDecimal tempMin;
        private BigDecimal tempMax;
        private String cooling;
        private String coolingName;
        private String status;
        private String statusName;
        private String statusColor;
        private String currentBatchNo;
        /** 液位百分比（当前批次产量 / 罐容） */
        private BigDecimal fillPercent;
        private LocalDateTime updatedAt;

        public static FermenterView of(Fermenter e, BigDecimal fillPercent) {
            FermenterView v = new FermenterView();
            v.setId(e.getId());
            v.setTankNo(e.getTankNo());
            v.setCapacityL(e.getCapacityL());
            v.setMaterial(e.getMaterial());
            v.setMaterialName(materialName(e.getMaterial()));
            v.setTempMin(e.getTempMin());
            v.setTempMax(e.getTempMax());
            v.setCooling(e.getCooling());
            v.setCoolingName(coolingName(e.getCooling()));
            v.setStatus(e.getStatus());
            v.setStatusName(tankStatusName(e.getStatus()));
            v.setStatusColor(tankStatusColor(e.getStatus()));
            v.setCurrentBatchNo(e.getCurrentBatchNo());
            v.setFillPercent(fillPercent);
            v.setUpdatedAt(e.getUpdatedAt());
            return v;
        }
    }

    @Data
    public static class TimelineNode {

        private String stage;
        private String stageName;
        private LocalDateTime time;
        private String status;
    }

    @Data
    public static class CurvePoint {

        private String dayKey;
        private BigDecimal temp;
        private BigDecimal gravity;
    }

    /** 一轮质检的只读视图（历史轮次同样用它渲染，任何字段都不允许页面回写） */
    @Data
    public static class QcRoundView {

        private Long id;
        private Long batchId;
        private Integer roundNo;
        private String batchStatus;
        private String batchStatusName;
        private String inspector;
        private String submittedBy;
        /** OPEN/PASSED/REJECTED/REVOKED */
        private String result;
        private String resultName;
        private BigDecimal qcAbv;
        private String sensory;
        private String reason;
        /** 送检时逐日温度/糖度摘要快照（JSON 原文，开轮后不随批次曲线变） */
        private String readingsSummary;
        private LocalDateTime openedAt;
        private LocalDateTime submittedAt;
        private LocalDateTime revokedAt;
        private String revokedBy;
        private Long supersededRoundId;
        /** 是否当前有效轮次（批次指针指向它） */
        private boolean latest;

        public static QcRoundView of(QcRound e, boolean latest) {
            QcRoundView v = new QcRoundView();
            v.setId(e.getId());
            v.setBatchId(e.getBatchId());
            v.setRoundNo(e.getRoundNo());
            v.setBatchStatus(e.getBatchStatus());
            v.setBatchStatusName(batchStatusName(e.getBatchStatus()));
            v.setInspector(e.getInspector());
            v.setSubmittedBy(e.getSubmittedBy());
            v.setResult(e.getResult());
            v.setResultName(qcResultName(e.getResult()));
            v.setQcAbv(e.getQcAbv());
            v.setSensory(e.getSensory());
            v.setReason(e.getReason());
            v.setReadingsSummary(e.getReadingsSummary());
            v.setOpenedAt(e.getOpenedAt());
            v.setSubmittedAt(e.getSubmittedAt());
            v.setRevokedAt(e.getRevokedAt());
            v.setRevokedBy(e.getRevokedBy());
            v.setSupersededRoundId(e.getSupersededRoundId());
            v.setLatest(latest);
            return v;
        }
    }

    @Data
    public static class BatchView {

        private Long id;
        private String batchNo;
        private Long recipeId;
        private String recipeName;
        private String fermenterNo;
        private String brewer;
        private LocalDate brewDate;
        private LocalDateTime mashStart;
        private LocalDateTime mashEnd;
        private LocalDateTime boilEnd;
        private LocalDateTime fermentStart;
        private LocalDate expectedMature;
        private LocalDate actualMature;
        private BigDecimal volumeL;
        private BigDecimal abv;
        private BigDecimal ibu;
        private BigDecimal ebc;
        private String status;
        private String statusName;
        private String nextStatus;
        private String nextStatusName;
        // ---- 质检放行链（当前可操作状态：永远以最新有效轮次判定） ----
        /** NONE/OPEN/RECHECK/PASSED/REJECTED/REVOKED */
        private String qcState;
        private String qcStateName;
        private String qcStateColor;
        /** 非空 = 当前不能继续灌装/出货/推进的业务原因（后端真实裁决，不是提示文字） */
        private String qcBlockReason;
        private Long latestQcRoundId;
        private Integer latestQcRoundNo;
        /** 最新一轮的结论人/时间/原因/酒精度/感官，列表页直接展示当前有效结论 */
        private String latestQcResult;
        private String latestQcResultName;
        private String latestQcOperator;
        private LocalDateTime latestQcAt;
        private String latestQcReason;
        private BigDecimal latestQcAbv;
        private String latestQcSensory;
        private Integer qcRoundCount;
        /** 详情页：全部历史轮次（只增不改，复检不抹旧轮） */
        private List<QcRoundView> qcRounds = new ArrayList<>();
        /** 乐观锁版本，前端提交时原样带回做过期提交拦截 */
        private Integer qcVersion;
        /** 是否具备必要测量记录（温度+糖度至少各一条） */
        private boolean readingsReady;
        /** 是否处于可成熟状态 */
        private boolean mature;
        /** 工艺时间线（由各阶段时间字段拼成） */
        private List<TimelineNode> timeline = new ArrayList<>();
        /** @ElementCollection Map 逐日温度/糖度曲线 */
        private List<CurvePoint> curve = new ArrayList<>();
        private LocalDateTime updatedAt;

        public static BatchView of(Batch e, String recipeName) {
            return of(e, recipeName, null, List.of());
        }

        /**
         * @param latest 当前有效轮次（batch.latestQcId 指向的那轮，可能为 null）
         * @param rounds 全部轮次（含历史），列表接口传空集合即可，详情接口传全量
         */
        public static BatchView of(Batch e, String recipeName, QcRound latest, List<QcRound> rounds) {
            BatchView v = new BatchView();
            v.setId(e.getId());
            v.setBatchNo(e.getBatchNo());
            v.setRecipeId(e.getRecipeId());
            v.setRecipeName(recipeName);
            v.setFermenterNo(e.getFermenterNo());
            v.setBrewer(e.getBrewer());
            v.setBrewDate(e.getBrewDate());
            v.setMashStart(e.getMashStart());
            v.setMashEnd(e.getMashEnd());
            v.setBoilEnd(e.getBoilEnd());
            v.setFermentStart(e.getFermentStart());
            v.setExpectedMature(e.getExpectedMature());
            v.setActualMature(e.getActualMature());
            v.setVolumeL(e.getVolumeL());
            v.setAbv(e.getAbv());
            v.setIbu(e.getIbu());
            v.setEbc(e.getEbc());
            v.setStatus(e.getStatus());
            v.setStatusName(batchStatusName(e.getStatus()));
            String next = batchNextStatus(e.getStatus());
            v.setNextStatus(next);
            v.setNextStatusName(next == null ? "" : batchStatusName(next));
            v.setQcVersion(e.getQcVersion());
            v.setMature(com.brewery.craft.service.QcGate.mature(e));
            v.setReadingsReady(com.brewery.craft.service.QcGate.hasNecessaryReadings(e));
            v.fillQc(e, latest, rounds);
            v.buildTimeline();
            v.buildCurve(e);
            v.setUpdatedAt(e.getUpdatedAt());
            return v;
        }

        private void fillQc(Batch e, QcRound latest, List<QcRound> rounds) {
            com.brewery.craft.service.QcGate.QcState state = com.brewery.craft.service.QcGate.stateOf(latest);
            String key = state.name();
            qcState = key;
            qcStateName = qcStateName(key);
            qcStateColor = qcStateColor(key);
            qcRoundCount = rounds.isEmpty()
                    ? (latest == null ? 0 : (int) (latest.getRoundNo() == null ? 1 : latest.getRoundNo()))
                    : rounds.size();
            if (latest != null) {
                latestQcRoundId = latest.getId();
                latestQcRoundNo = latest.getRoundNo();
                latestQcResult = latest.getResult();
                latestQcResultName = qcResultName(latest.getResult());
                latestQcReason = latest.getReason();
                latestQcAbv = latest.getQcAbv();
                latestQcSensory = latest.getSensory();
                latestQcOperator = "REVOKED".equals(latest.getResult())
                        ? latest.getRevokedBy()
                        : (latest.getSubmittedBy() != null ? latest.getSubmittedBy() : latest.getInspector());
                latestQcAt = latest.getRevokedAt() != null
                        ? latest.getRevokedAt()
                        : (latest.getSubmittedAt() != null ? latest.getSubmittedAt() : latest.getOpenedAt());
            }
            for (QcRound r : rounds) {
                qcRounds.add(QcRoundView.of(r, latest != null && r.getId().equals(latest.getId())));
            }
            // 当前能否继续下游：批次报废 / 可成熟态 / 测量记录 / 最新放行，统一由裁决器给原因
            if ("SCRAPPED".equals(e.getStatus())) {
                qcBlockReason = "批次已报废";
            } else if (com.brewery.craft.service.QcGate.mature(e)) {
                String reason = com.brewery.craft.service.QcGate.qcBlockReason(latest);
                if (reason == null && !com.brewery.craft.service.QcGate.hasNecessaryReadings(e)) {
                    reason = "缺少必要的逐日温度/糖度测量记录";
                }
                qcBlockReason = reason;
            } else {
                qcBlockReason = "未进入可成熟（熟成）状态";
            }
        }

        private void buildTimeline() {
            addNode(timeline, "酿造日", brewDate == null ? null : brewDate.atStartOfDay());
            addNode(timeline, "糖化开始", mashStart);
            addNode(timeline, "糖化结束（转煮沸）", mashEnd);
            addNode(timeline, "煮沸结束", boilEnd);
            addNode(timeline, "发酵开始", fermentStart);
            addNode(timeline, "预计成熟日", expectedMature == null ? null : expectedMature.atStartOfDay());
            addNode(timeline, "实际成熟日", actualMature == null ? null : actualMature.atStartOfDay());
        }

        private void buildCurve(Batch e) {
            Map<String, BigDecimal> temps = e.getTempReadings() == null ? Map.of() : e.getTempReadings();
            Map<String, BigDecimal> gravities = e.getGravityReadings() == null ? Map.of() : e.getGravityReadings();
            List<String> keys = new ArrayList<>(temps.keySet());
            for (String k : gravities.keySet()) {
                if (!keys.contains(k)) {
                    keys.add(k);
                }
            }
            keys.sort((a, b) -> Integer.compare(dayKeyOrder(a), dayKeyOrder(b)));
            for (String k : keys) {
                CurvePoint p = new CurvePoint();
                p.setDayKey(k);
                p.setTemp(temps.get(k));
                p.setGravity(gravities.get(k));
                curve.add(p);
            }
        }
    }

    private static void addNode(List<TimelineNode> nodes, String stageName, LocalDateTime time) {
        TimelineNode node = new TimelineNode();
        node.setStage(stageName);
        node.setStageName(stageName);
        node.setTime(time);
        node.setStatus(time == null ? "PENDING" : "DONE");
        nodes.add(node);
    }

    /** 批次量价：某状态下的产量汇总 */
    @Data
    public static class BatchSummaryItem {

        private String status;
        private String statusName;
        private long count;
        private BigDecimal totalVolume;
    }

    @Data
    public static class BatchSummary {

        private List<BatchSummaryItem> items = new ArrayList<>();
        private long batchTotal;
        private BigDecimal volumeTotal;
        private BigDecimal packagedVolume;
    }

    @Data
    public static class RecipeItemView {

        /** Map 的 key = 投放阶段 */
        private String stage;
        private String stageName;
        private Long itemId;
        private String ingredientCode;
        private String ingredientName;
        private String ingredientType;
        private String ingredientTypeName;
        private BigDecimal quantity;
        private String unit;
        private String note;

        public static RecipeItemView of(String stage, RecipeItem item) {
            RecipeItemView v = new RecipeItemView();
            v.setStage(stage);
            v.setStageName(stageName(stage));
            v.setItemId(item.getId());
            v.setIngredientCode(item.getIngredientCode());
            v.setIngredientName(item.getIngredientName());
            v.setIngredientType(item.getIngredientType());
            v.setIngredientTypeName(ingredientTypeName(item.getIngredientType()));
            v.setQuantity(item.getQuantity());
            v.setUnit(item.getUnit());
            v.setNote(item.getNote());
            return v;
        }
    }

    @Data
    public static class CompositionSlice {

        private String ingredientType;
        private String ingredientTypeName;
        private String color;
        private BigDecimal amount;
        private BigDecimal percent;
    }

    @Data
    public static class RecipeView {

        private Long id;
        private String code;
        private String name;
        private String style;
        private String styleName;
        private BigDecimal targetOg;
        private BigDecimal targetAbv;
        private BigDecimal targetIbu;
        private BigDecimal targetEbc;
        private String processNote;
        private int itemCount;
        /** 各原料类型占比（conic-gradient 环形图） */
        private List<CompositionSlice> composition = new ArrayList<>();
        private List<RecipeItemView> items = new ArrayList<>();
        private LocalDateTime updatedAt;

        public static RecipeView of(Recipe e) {
            RecipeView v = new RecipeView();
            v.setId(e.getId());
            v.setCode(e.getCode());
            v.setName(e.getName());
            v.setStyle(e.getStyle());
            v.setStyleName(styleName(e.getStyle()));
            v.setTargetOg(e.getTargetOg());
            v.setTargetAbv(e.getTargetAbv());
            v.setTargetIbu(e.getTargetIbu());
            v.setTargetEbc(e.getTargetEbc());
            v.setProcessNote(e.getProcessNote());
            Map<String, RecipeItem> map = e.getItems() == null ? Map.of() : e.getItems();
            List<String> keys = new ArrayList<>(map.keySet());
            keys.sort((a, b) -> Integer.compare(STAGE_ORDER.indexOf(a) < 0 ? 99 : STAGE_ORDER.indexOf(a),
                    STAGE_ORDER.indexOf(b) < 0 ? 99 : STAGE_ORDER.indexOf(b)));
            for (String key : keys) {
                v.getItems().add(RecipeItemView.of(key, map.get(key)));
            }
            v.setItemCount(v.getItems().size());
            v.buildComposition();
            v.setUpdatedAt(e.getUpdatedAt());
            return v;
        }

        private void buildComposition() {
            Map<String, BigDecimal> sumByType = new LinkedHashMap<>();
            BigDecimal total = BigDecimal.ZERO;
            for (RecipeItemView it : items) {
                if (it.getQuantity() == null) {
                    continue;
                }
                sumByType.merge(it.getIngredientType(), it.getQuantity(), BigDecimal::add);
                total = total.add(it.getQuantity());
            }
            if (total.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }
            for (String type : INGREDIENT_TYPE_ORDER) {
                BigDecimal amount = sumByType.get(type);
                if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                CompositionSlice slice = new CompositionSlice();
                slice.setIngredientType(type);
                slice.setIngredientTypeName(ingredientTypeName(type));
                slice.setColor(ingredientColor(type));
                slice.setAmount(amount);
                slice.setPercent(amount.multiply(BigDecimal.valueOf(100))
                        .divide(total, 1, java.math.RoundingMode.HALF_UP));
                composition.add(slice);
            }
        }
    }

    @Data
    public static class IngredientView {

        private Long id;
        private String code;
        private String name;
        private String type;
        private String typeName;
        private String unit;
        private BigDecimal stock;
        private BigDecimal safetyStock;
        private BigDecimal unitPrice;
        private String supplier;
        private LocalDate shelfLifeTo;
        /** 库存 = 0 为 false；stock < safetyStock 时 true，页面标红 */
        private boolean low;
        private LocalDateTime updatedAt;

        public static IngredientView of(Ingredient e) {
            IngredientView v = new IngredientView();
            v.setId(e.getId());
            v.setCode(e.getCode());
            v.setName(e.getName());
            v.setType(e.getType());
            v.setTypeName(ingredientTypeName(e.getType()));
            v.setUnit(e.getUnit());
            v.setStock(e.getStock());
            v.setSafetyStock(e.getSafetyStock());
            v.setUnitPrice(e.getUnitPrice());
            v.setSupplier(e.getSupplier());
            v.setShelfLifeTo(e.getShelfLifeTo());
            v.setLow(e.getStock() != null && e.getSafetyStock() != null
                    && e.getStock().compareTo(e.getSafetyStock()) < 0);
            v.setUpdatedAt(e.getUpdatedAt());
            return v;
        }
    }

    @Data
    public static class BottlingView {

        private Long id;
        private String bottlingNo;
        private String batchNo;
        private String packageForm;
        private String packageFormName;
        private Integer quantity;
        private LocalDate bottleDate;
        private BigDecimal lossL;
        private Long qcRoundId;
        /** 创建灌装时该批次的质检态快照名（历史单不随后续撤销/复检改写） */
        private String qcState;
        private String qcStateName;
        private String qcStateColor;
        private LocalDateTime updatedAt;

        public static BottlingView of(Bottling e) {
            return of(e, "PASSED");
        }

        public static BottlingView of(Bottling e, String qcState) {
            BottlingView v = new BottlingView();
            v.setId(e.getId());
            v.setBottlingNo(e.getBottlingNo());
            v.setBatchNo(e.getBatchNo());
            v.setPackageForm(e.getPackageForm());
            v.setPackageFormName(packageFormName(e.getPackageForm()));
            v.setQuantity(e.getQuantity());
            v.setBottleDate(e.getBottleDate());
            v.setLossL(e.getLossL());
            v.setQcRoundId(e.getQcRoundId());
            v.setQcState(qcState);
            v.setQcStateName(qcStateName(qcState));
            v.setQcStateColor(qcStateColor(qcState));
            v.setUpdatedAt(e.getUpdatedAt());
            return v;
        }
    }

    @Data
    public static class ShipmentView {

        private Long id;
        private String shipmentNo;
        private String customer;
        private String batchNo;
        private String packageForm;
        private String packageFormName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal amount;
        private LocalDate shipDate;
        private String status;
        private String statusName;
        private String nextStatus;
        private String nextStatusName;
        private Long qcRoundId;
        /** 批次当前质检态（用于列表醒目标注 待质检/复检中/已驳回/已放行/已撤销） */
        private String qcState;
        private String qcStateName;
        private String qcStateColor;
        /** 撤销放行/复检/驳回导致的冻结：PENDING 单被挂起，重新放行才恢复 */
        private boolean frozen;
        private String frozenReason;
        private LocalDateTime frozenAt;
        private LocalDateTime updatedAt;

        public static ShipmentView of(Shipment e) {
            return of(e, null);
        }

        public static ShipmentView of(Shipment e, String qcState) {
            ShipmentView v = new ShipmentView();
            v.setId(e.getId());
            v.setShipmentNo(e.getShipmentNo());
            v.setCustomer(e.getCustomer());
            v.setBatchNo(e.getBatchNo());
            v.setPackageForm(e.getPackageForm());
            v.setPackageFormName(packageFormName(e.getPackageForm()));
            v.setQuantity(e.getQuantity());
            v.setUnitPrice(e.getUnitPrice());
            v.setAmount(e.getAmount());
            v.setShipDate(e.getShipDate());
            v.setStatus(e.getStatus());
            v.setStatusName(shipmentStatusName(e.getStatus()));
            String next = shipmentNextStatus(e.getStatus());
            v.setNextStatus(next);
            v.setNextStatusName(next == null ? "" : shipmentStatusName(next));
            v.setQcRoundId(e.getQcRoundId());
            v.setFrozen(Boolean.TRUE.equals(e.getFrozen()));
            v.setFrozenReason(e.getFrozenReason());
            v.setFrozenAt(e.getFrozenAt());
            if (qcState != null) {
                v.setQcState(qcState);
                v.setQcStateName(qcStateName(qcState));
                v.setQcStateColor(qcStateColor(qcState));
            }
            v.setUpdatedAt(e.getUpdatedAt());
            return v;
        }
    }

    @Data
    public static class ShipmentSummaryItem {

        private String status;
        private String statusName;
        private long count;
        private long quantity;
        private BigDecimal amount;
    }

    @Data
    public static class ShipmentSummary {

        private List<ShipmentSummaryItem> items = new ArrayList<>();
        private long orderTotal;
        private long quantityTotal;
        private BigDecimal amountTotal;
        private BigDecimal paidAmount;
        /** 被质检冻结的待发出货单数（撤销放行/复检中/已驳回） */
        private long frozenCount;
    }

    @Data
    public static class Overview {

        private long tankTotal;
        private long tankBusy;
        private long tankIdle;
        private Map<String, Long> tankStatusCount = new LinkedHashMap<>();
        private BigDecimal capacityTotal;
        private long batchTotal;
        private long batchFermenting;
        private long batchPackaged;
        private BigDecimal batchVolumeTotal;
        private BigDecimal packagedVolume;
        private long recipeTotal;
        private long ingredientLow;
        private long shipmentPending;
        private BigDecimal shipmentAmount;
    }
}
