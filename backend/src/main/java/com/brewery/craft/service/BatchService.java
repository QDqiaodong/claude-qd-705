package com.brewery.craft.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.brewery.craft.common.BizException;
import com.brewery.craft.domain.Batch;
import com.brewery.craft.domain.Fermenter;
import com.brewery.craft.domain.QcRound;
import com.brewery.craft.domain.Recipe;
import com.brewery.craft.dto.BatchDtos;
import com.brewery.craft.dto.Views;
import com.brewery.craft.repository.BatchRepository;
import com.brewery.craft.repository.BottlingRepository;
import com.brewery.craft.repository.FermenterRepository;
import com.brewery.craft.repository.QcRoundRepository;
import com.brewery.craft.repository.RecipeRepository;

/** 模块二：啤酒批次（状态推进 + @ElementCollection 曲线）。 */
@Service
@Transactional(readOnly = true)
public class BatchService {

    private final BatchRepository batchRepository;
    private final RecipeRepository recipeRepository;
    private final FermenterRepository fermenterRepository;
    private final QcRoundRepository qcRoundRepository;
    private final BottlingRepository bottlingRepository;

    public BatchService(BatchRepository batchRepository,
                        RecipeRepository recipeRepository,
                        FermenterRepository fermenterRepository,
                        QcRoundRepository qcRoundRepository,
                        BottlingRepository bottlingRepository) {
        this.batchRepository = batchRepository;
        this.recipeRepository = recipeRepository;
        this.fermenterRepository = fermenterRepository;
        this.qcRoundRepository = qcRoundRepository;
        this.bottlingRepository = bottlingRepository;
    }

    public List<Views.BatchView> list(String status) {
        List<Batch> batches = status == null || status.isBlank()
                ? batchRepository.findAllByOrderByBatchNoAsc()
                : batchRepository.findAllByStatusOrderByBatchNoAsc(status);
        // 批量带出最新一轮结论，列表页直接显示当前有效质检态
        Map<Long, QcRound> latestByBatch = loadLatestRounds(batches);
        List<Views.BatchView> list = new ArrayList<>();
        for (Batch b : batches) {
            list.add(view(b, latestByBatch.get(b.getId())));
        }
        return list;
    }

    public Views.BatchView get(Long id) {
        Batch b = batchRepository.findById(id).orElseThrow(() -> new BizException("批次不存在"));
        QcRound latest = b.getLatestQcId() == null ? null
                : qcRoundRepository.findById(b.getLatestQcId()).orElse(null);
        return view(b, latest);
    }

    /** 带全量轮次历史的详情视图 */
    public Views.BatchView getWithRounds(Long id) {
        Batch b = batchRepository.findById(id).orElseThrow(() -> new BizException("批次不存在"));
        QcRound latest = b.getLatestQcId() == null ? null
                : qcRoundRepository.findById(b.getLatestQcId()).orElse(null);
        List<QcRound> rounds = qcRoundRepository.findAllByBatchIdOrderByRoundNoAsc(id);
        return Views.BatchView.of(b, recipeName(b), latest, rounds);
    }

    private Map<Long, QcRound> loadLatestRounds(List<Batch> batches) {
        Map<Long, QcRound> map = new java.util.HashMap<>();
        for (Batch b : batches) {
            if (b.getLatestQcId() != null) {
                qcRoundRepository.findById(b.getLatestQcId()).ifPresent(r -> map.put(b.getId(), r));
            }
        }
        return map;
    }

    private Views.BatchView view(Batch b) {
        QcRound latest = b.getLatestQcId() == null ? null
                : qcRoundRepository.findById(b.getLatestQcId()).orElse(null);
        return view(b, latest);
    }

    private Views.BatchView view(Batch b, QcRound latest) {
        return Views.BatchView.of(b, recipeName(b), latest, List.of());
    }

    private String recipeName(Batch b) {
        if (b.getRecipeId() == null) {
            return "";
        }
        return recipeRepository.findById(b.getRecipeId()).map(Recipe::getName).orElse("");
    }

    @Transactional
    public Views.BatchView create(BatchDtos.BatchSaveReq req) {
        if (batchRepository.existsByBatchNo(req.getBatchNo())) {
            throw new BizException("批号 " + req.getBatchNo() + " 已存在");
        }
        String status = req.getStatus() == null || req.getStatus().isBlank() ? "PLAN" : req.getStatus();
        if (Views.batchStatusName(status).equals(status)) {
            throw new BizException("批次状态取值非法：" + status);
        }
        if (req.getRecipeId() != null && recipeRepository.findById(req.getRecipeId()).isEmpty()) {
            throw new BizException("配方不存在");
        }
        Batch b = new Batch();
        b.setBatchNo(req.getBatchNo());
        b.setRecipeId(req.getRecipeId());
        b.setFermenterNo(req.getFermenterNo());
        b.setBrewer(req.getBrewer());
        b.setBrewDate(req.getBrewDate() == null ? LocalDate.now() : req.getBrewDate());
        b.setMashStart(req.getMashStart());
        b.setMashEnd(req.getMashEnd());
        b.setBoilEnd(req.getBoilEnd());
        b.setFermentStart(req.getFermentStart());
        b.setExpectedMature(req.getExpectedMature());
        b.setActualMature(req.getActualMature());
        b.setVolumeL(req.getVolumeL() == null ? BigDecimal.ZERO : req.getVolumeL());
        b.setAbv(req.getAbv());
        b.setIbu(req.getIbu());
        b.setEbc(req.getEbc());
        b.setStatus(status);
        b.setUpdatedAt(LocalDateTime.now());
        return view(batchRepository.save(b));
    }

    /**
     * 状态推进：计划 → 糖化 → 煮沸 → 发酵 → 熟成 → 已灌装。
     * 走到哪一步就把这一步的时间戳钉上，并同步罐体状态。
     */
    @Transactional
    public Views.BatchView advance(Long id, String targetStatus) {
        Batch b = batchRepository.findByIdForUpdate(id).orElseThrow(() -> new BizException("批次不存在"));
        if ("SCRAPPED".equals(b.getStatus())) {
            throw new BizException("已报废批次不能再推进");
        }
        if ("PACKAGED".equals(b.getStatus())) {
            throw new BizException("批次已灌装，流程结束");
        }
        String next = Views.batchNextStatus(b.getStatus());
        if (targetStatus != null && !targetStatus.isBlank()) {
            if (!targetStatus.equals(next)) {
                throw new BizException("只能单向推进到" + Views.batchStatusName(next));
            }
        }
        LocalDateTime now = LocalDateTime.now();
        switch (next) {
            case "MASH" -> b.setMashStart(now);
            case "BOIL" -> b.setMashEnd(now);
            case "FERMENT" -> b.setBoilEnd(now);
            case "CONDITION" -> {
                if (b.getFermentStart() == null) {
                    b.setFermentStart(now);
                }
                if (b.getExpectedMature() == null) {
                    b.setExpectedMature(LocalDate.now().plusDays(14));
                }
            }
            case "PACKAGED" -> {
                // 终态规则：熟成 → 已灌装 必须以【最新一轮明确放行】为前提，
                // 复检中/驳回/撤销放行一律不得完工，和灌装/出货走同一个裁决口径
                QcRound latest = b.getLatestQcId() == null ? null
                        : qcRoundRepository.findById(b.getLatestQcId()).orElse(null);
                String reason = QcGate.qcBlockReason(latest);
                if (reason != null) {
                    throw new BizException("不能推进到已灌装：" + reason);
                }
                if (b.getActualMature() == null) {
                    b.setActualMature(LocalDate.now());
                }
            }
            default -> throw new BizException("当前状态无法继续推进");
        }
        b.setStatus(next);
        b.setUpdatedAt(now);
        Batch saved = batchRepository.save(b);
        syncTank(saved);
        return view(saved);
    }

    @Transactional
    public Views.BatchView scrap(Long id) {
        Batch b = batchRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("批次不存在"));
        if ("PACKAGED".equals(b.getStatus())) {
            throw new BizException("已灌装批次不能报废");
        }
        // 终态规则：已有灌装实物的批次不允许直接报废（走专门的质量处置流程），
        // 防止报废和质检放行/出货推进互相覆盖出矛盾状态
        if (!bottlingRepository.findAllByBatchNoOrderByIdAsc(b.getBatchNo()).isEmpty()) {
            throw new BizException("该批次已有灌装单，不能直接报废；请按质量事故流程处置已灌装产品");
        }
        QcRound latest = b.getLatestQcId() == null ? null
                : qcRoundRepository.findById(b.getLatestQcId()).orElse(null);
        if (latest != null && "OPEN".equals(latest.getResult())) {
            throw new BizException("第 " + latest.getRoundNo() + " 轮质检正在送检中，请先提交结论或完成复检后再报废");
        }
        b.setStatus("SCRAPPED");
        b.setUpdatedAt(LocalDateTime.now());
        Batch saved = batchRepository.save(b);
        syncTank(saved);
        return view(saved);
    }

    private void syncTank(Batch batch) {
        if (batch.getFermenterNo() == null || batch.getFermenterNo().isBlank()) {
            return;
        }
        Optional<Fermenter> tank = fermenterRepository.findByTankNo(batch.getFermenterNo());
        if (tank.isEmpty()) {
            return;
        }
        Fermenter t = tank.get();
        if ("PACKAGED".equals(batch.getStatus()) || "SCRAPPED".equals(batch.getStatus())) {
            if (batch.getBatchNo().equals(t.getCurrentBatchNo())) {
                t.setCurrentBatchNo(null);
                t.setStatus("CLEANING");
            }
        } else if (batch.getBatchNo().equals(t.getCurrentBatchNo()) || t.getCurrentBatchNo() == null) {
            t.setCurrentBatchNo(batch.getBatchNo());
            t.setStatus(Views.tankStatusOfBatch(batch.getStatus()));
        }
        t.setUpdatedAt(LocalDateTime.now());
        fermenterRepository.save(t);
    }

    /** 【@ElementCollection Map】写入或覆盖某一天的测温/测糖 */
    @Transactional
    public Views.BatchView putReading(Long id, BatchDtos.ReadingReq req) {
        Batch b = batchRepository.findById(id).orElseThrow(() -> new BizException("批次不存在"));
        String dayKey = req.getDayKey() == null || req.getDayKey().isBlank() ? "D1" : req.getDayKey();
        if (req.getTemp() != null) {
            b.getTempReadings().put(dayKey, req.getTemp());
        }
        if (req.getGravity() != null) {
            b.getGravityReadings().put(dayKey, req.getGravity());
        }
        b.setUpdatedAt(LocalDateTime.now());
        return view(batchRepository.save(b));
    }

    @Transactional
    public Views.BatchView removeReading(Long id, String dayKey) {
        Batch b = batchRepository.findById(id).orElseThrow(() -> new BizException("批次不存在"));
        if (!b.getTempReadings().containsKey(dayKey) && !b.getGravityReadings().containsKey(dayKey)) {
            throw new BizException("第 " + dayKey + " 天没有测量记录");
        }
        b.getTempReadings().remove(dayKey);
        b.getGravityReadings().remove(dayKey);
        b.setUpdatedAt(LocalDateTime.now());
        return view(batchRepository.save(b));
    }

    /** 按 key 查询某一天的温度/糖度 */
    public Map<String, BigDecimal> reading(Long id, String dayKey) {
        Batch b = batchRepository.findById(id).orElseThrow(() -> new BizException("批次不存在"));
        Map<String, BigDecimal> one = new java.util.LinkedHashMap<>();
        one.put("temp", b.getTempReadings().get(dayKey));
        one.put("gravity", b.getGravityReadings().get(dayKey));
        one.put("dayKey", BigDecimal.valueOf(Views.dayKeyOrder(dayKey) == Integer.MAX_VALUE ? 0 : Views.dayKeyOrder(dayKey)));
        return one;
    }

    /** 产量汇总：按状态聚合 */
    public Views.BatchSummary summary() {
        List<Batch> batches = batchRepository.findAllByOrderByBatchNoAsc();
        Map<String, java.util.List<Batch>> grouped = new java.util.LinkedHashMap<>();
        for (String s : List.of("PLAN", "MASH", "BOIL", "FERMENT", "CONDITION", "PACKAGED", "SCRAPPED")) {
            grouped.put(s, new ArrayList<>());
        }
        for (Batch b : batches) {
            grouped.computeIfAbsent(b.getStatus(), k -> new ArrayList<>()).add(b);
        }
        Views.BatchSummary summary = new Views.BatchSummary();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal packaged = BigDecimal.ZERO;
        for (Map.Entry<String, java.util.List<Batch>> e : grouped.entrySet()) {
            BigDecimal sum = BigDecimal.ZERO;
            for (Batch b : e.getValue()) {
                sum = sum.add(b.getVolumeL() == null ? BigDecimal.ZERO : b.getVolumeL());
            }
            Views.BatchSummaryItem item = new Views.BatchSummaryItem();
            item.setStatus(e.getKey());
            item.setStatusName(Views.batchStatusName(e.getKey()));
            item.setCount(e.getValue().size());
            item.setTotalVolume(sum.setScale(0, RoundingMode.HALF_UP));
            summary.getItems().add(item);
            total = total.add(sum);
            if ("PACKAGED".equals(e.getKey())) {
                packaged = sum;
            }
        }
        summary.setBatchTotal(batches.size());
        summary.setVolumeTotal(total.setScale(0, RoundingMode.HALF_UP));
        summary.setPackagedVolume(packaged.setScale(0, RoundingMode.HALF_UP));
        return summary;
    }
}
