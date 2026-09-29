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
import com.brewery.craft.domain.QcAction;
import com.brewery.craft.domain.QcReading;
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
    private static final Map<String, String> SHIPMENT_STATUS = Map.ofEntries(
            Map.entry("PENDING", "待发"),
            Map.entry("FROZEN", "冻结"),
            Map.entry("SHIPPED", "已发"),
            Map.entry("PAID", "已结款"));

    private static final Map<String, String> SHIPMENT_NEXT = Map.of(
            "PENDING", "SHIPPED",
            "SHIPPED", "PAID");

    // ---------------- 质检有效状态（由最新轮次推导） ----------------
    private static final Map<String, String> QC_STATUS = Map.of(
            "NONE", "待质检",
            "PENDING", "待质检",
            "REINSPECT", "复检中",
            "PASS", "已放行",
            "REJECT", "已驳回",
            "REVOKED", "已撤销");

    private static final Map<String, String> QC_ROUND_STATUS = Map.of(
            "PENDING", "检验中",
            "PASS", "放行",
            "REJECT", "驳回",
            "REVOKED", "已撤销");

    private static final Map<String, String> QC_ACTION = Map.of(
            "SUBMIT", "送检",
            "PASS", "通过放行",
            "REJECT", "驳回",
            "REVOKE", "撤销放行");

    public static String qcStatusName(String v) {
        return QC_STATUS.getOrDefault(v, v == null ? "" : v);
    }

    public static String qcRoundStatusName(String v) {
        return QC_ROUND_STATUS.getOrDefault(v, v == null ? "" : v);
    }

    public static String qcActionName(String v) {
        return QC_ACTION.getOrDefault(v, v == null ? "" : v);
    }

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
        /** 质检有效状态：NONE/PENDING/REINSPECT/PASS/REJECT/REVOKED */
        private String qcStatus;
        private String qcStatusName;
        /** 质检最新轮序号（null = 尚未送检） */
        private Integer qcRoundSeq;
        /** 最新有效结论的原因/说明（撤销/驳回原因、放行说明） */
        private String qcReason;
        /** 质检轮数 */
        private long qcRoundCount;
        /** 工艺时间线（由各阶段时间字段拼成） */
        private List<TimelineNode> timeline = new ArrayList<>();
        /** @ElementCollection Map 逐日温度/糖度曲线 */
        private List<CurvePoint> curve = new ArrayList<>();
        private LocalDateTime updatedAt;

        public static BatchView of(Batch e, String recipeName) {
            return of(e, recipeName, null);
        }

        /**
         * @param qc 当前批次的质检状态；列表场景可传 null（不额外查库，前端可再拉详情）
         */
        public static BatchView of(Batch e, String recipeName, BatchQcStateView qc) {
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
            if (qc != null) {
                v.setQcStatus(qc.getStatus());
                v.setQcStatusName(qc.getStatusName());
                v.setQcRoundSeq(qc.getRoundSeq());
                v.setQcReason(qc.getReason());
                v.setQcRoundCount(qc.getRoundCount());
            }
            v.buildTimeline();
            v.buildCurve(e);
            v.setUpdatedAt(e.getUpdatedAt());
            return v;
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

    // ---------------- 模块六：质检 ----------------
    @Data
    public static class BatchQcStateView {

        private String status;
        private String statusName;
        private Integer roundSeq;
        private String reason;
        private long roundCount;
        /** 放行轮版本（前端撤销/下结论时做乐观锁校验） */
        private Integer version;

        public static BatchQcStateView of(String code, QcRound latest, String reason, long roundCount) {
            BatchQcStateView v = new BatchQcStateView();
            v.setStatus(code);
            v.setStatusName(qcStatusName(code));
            v.setRoundSeq(latest == null ? null : latest.getRoundSeq());
            v.setReason(reason);
            v.setRoundCount(roundCount);
            v.setVersion(latest == null ? null : latest.getVersion());
            return v;
        }
    }

    @Data
    public static class QcReadingView {

        private String dayKey;
        private BigDecimal temp;
        private BigDecimal gravity;

        public static QcReadingView of(QcReading r) {
            QcReadingView v = new QcReadingView();
            v.setDayKey(r.getDayKey());
            v.setTemp(r.getTemp());
            v.setGravity(r.getGravity());
            return v;
        }
    }

    @Data
    public static class QcRoundView {

        private Long id;
        private Long batchId;
        private String batchNo;
        private Integer roundSeq;
        private String status;
        private String statusName;
        private String batchStatusSnapshot;
        private String batchStatusSnapshotName;
        private BigDecimal abv;
        private String sensory;
        private String conclusionNote;
        private String submittedBy;
        private LocalDateTime submittedAt;
        private String decidedBy;
        private LocalDateTime decidedAt;
        private String revokedBy;
        private LocalDateTime revokedAt;
        private String revokeReason;
        private Integer version;
        private List<QcReadingView> readings = new ArrayList<>();

        public static QcRoundView of(QcRound e) {
            QcRoundView v = new QcRoundView();
            v.setId(e.getId());
            v.setBatchId(e.getBatchId());
            v.setBatchNo(e.getBatchNo());
            v.setRoundSeq(e.getRoundSeq());
            v.setStatus(e.getStatus());
            v.setStatusName(qcRoundStatusName(e.getStatus()));
            v.setBatchStatusSnapshot(e.getBatchStatusSnapshot());
            v.setBatchStatusSnapshotName(batchStatusName(e.getBatchStatusSnapshot()));
            v.setAbv(e.getAbv());
            v.setSensory(e.getSensory());
            v.setConclusionNote(e.getConclusionNote());
            v.setSubmittedBy(e.getSubmittedBy());
            v.setSubmittedAt(e.getSubmittedAt());
            v.setDecidedBy(e.getDecidedBy());
            v.setDecidedAt(e.getDecidedAt());
            v.setRevokedBy(e.getRevokedBy());
            v.setRevokedAt(e.getRevokedAt());
            v.setRevokeReason(e.getRevokeReason());
            v.setVersion(e.getVersion());
            if (e.getReadings() != null) {
                for (QcReading r : e.getReadings()) {
                    v.getReadings().add(QcReadingView.of(r));
                }
            }
            return v;
        }
    }

    @Data
    public static class QcActionView {

        private Long id;
        private Long roundId;
        private Integer roundSeq;
        private String action;
        private String actionName;
        private String conclusion;
        private String operator;
        private String reason;
        private LocalDateTime createdAt;

        public static QcActionView of(QcAction e) {
            QcActionView v = new QcActionView();
            v.setId(e.getId());
            v.setRoundId(e.getRoundId());
            v.setRoundSeq(e.getRoundSeq());
            v.setAction(e.getAction());
            v.setActionName(qcActionName(e.getAction()));
            v.setConclusion(e.getConclusion());
            v.setOperator(e.getOperator());
            v.setReason(e.getReason());
            v.setCreatedAt(e.getCreatedAt());
            return v;
        }
    }

    /** 批次质检档案：当前有效状态 + 历史轮次（含测量快照）+ 操作流水 */
    @Data
    public static class BatchQcView {

        private Long batchId;
        private String batchNo;
        private String batchStatus;
        private String batchStatusName;
        private BatchQcStateView current;
        private List<QcRoundView> rounds = new ArrayList<>();
        private List<QcActionView> actions = new ArrayList<>();

        public static BatchQcView of(Batch b,
                                     com.brewery.craft.service.QcGate.QcState state,
                                     List<QcRound> roundEntities,
                                     List<QcAction> actionEntities) {
            BatchQcView v = new BatchQcView();
            v.setBatchId(b.getId());
            v.setBatchNo(b.getBatchNo());
            v.setBatchStatus(b.getStatus());
            v.setBatchStatusName(batchStatusName(b.getStatus()));
            v.setCurrent(BatchQcStateView.of(state.code, state.latestRound, state.reason, state.roundCount));
            for (QcRound r : roundEntities) {
                v.getRounds().add(QcRoundView.of(r));
            }
            for (QcAction a : actionEntities) {
                v.getActions().add(QcActionView.of(a));
            }
            return v;
        }
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
        /** 灌装时该批次的质检有效状态 */
        private String qcStatus;
        private String qcStatusName;
        private String qcReason;
        private String batchStatus;
        private String batchStatusName;
        private LocalDateTime updatedAt;

        public static BottlingView of(Bottling e) {
            return of(e, null, null, null);
        }

        public static BottlingView of(Bottling e, String qcStatus, String qcReason, String batchStatus) {
            BottlingView v = new BottlingView();
            v.setId(e.getId());
            v.setBottlingNo(e.getBottlingNo());
            v.setBatchNo(e.getBatchNo());
            v.setPackageForm(e.getPackageForm());
            v.setPackageFormName(packageFormName(e.getPackageForm()));
            v.setQuantity(e.getQuantity());
            v.setBottleDate(e.getBottleDate());
            v.setLossL(e.getLossL());
            v.setQcStatus(qcStatus);
            v.setQcStatusName(qcStatus == null ? "" : qcStatusName(qcStatus));
            v.setQcReason(qcReason);
            v.setBatchStatus(batchStatus);
            v.setBatchStatusName(batchStatus == null ? "" : batchStatusName(batchStatus));
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
        /** 冻结原因 / 质检状态说明（非冻结时为当前批次质检结论说明） */
        private String frozenReason;
        private LocalDateTime frozenAt;
        private Integer frozenRoundSeq;
        /** 该批次当前的质检有效状态 */
        private String qcStatus;
        private String qcStatusName;
        private String qcReason;
        private String batchStatus;
        private String batchStatusName;
        /** 出货推进被阻止时的业务原因（FROZEN/放行失效等） */
        private String blockedReason;
        private LocalDateTime updatedAt;

        public static ShipmentView of(Shipment e) {
            return of(e, null, null, null);
        }

        public static ShipmentView of(Shipment e, String qcStatus, String qcReason, String batchStatus) {
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
            v.setFrozenReason(e.getFrozenReason());
            v.setFrozenAt(e.getFrozenAt());
            v.setFrozenRoundSeq(e.getFrozenRoundSeq());
            v.setQcStatus(qcStatus);
            v.setQcStatusName(qcStatus == null ? "" : qcStatusName(qcStatus));
            v.setQcReason(qcReason);
            v.setBatchStatus(batchStatus);
            v.setBatchStatusName(batchStatus == null ? "" : batchStatusName(batchStatus));
            if ("FROZEN".equals(e.getStatus())) {
                v.setBlockedReason(e.getFrozenReason());
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
