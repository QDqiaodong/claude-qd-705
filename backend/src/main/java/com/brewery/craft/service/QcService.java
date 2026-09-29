package com.brewery.craft.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.brewery.craft.common.BizException;
import com.brewery.craft.domain.Batch;
import com.brewery.craft.domain.QcRound;
import com.brewery.craft.domain.Recipe;
import com.brewery.craft.dto.QcDtos;
import com.brewery.craft.dto.Views;
import com.brewery.craft.repository.BatchRepository;
import com.brewery.craft.repository.QcRoundRepository;
import com.brewery.craft.repository.RecipeRepository;
import com.brewery.craft.repository.ShipmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 批次质检放行链。
 *
 * <p>三条铁律：
 * <ol>
 *   <li>已提交的检验内容只追加、不覆盖、不删除；复检永远新开一轮。</li>
 *   <li>放行/复检/撤销与下游灌装、出货在批次行上串行化（行锁 + 乐观版本号）。</li>
 *   <li>撤销放行必须填原因，冻结该批次全部待发出货；重新放行后自动解冻；
 *       已发/已结款单据永不改写。</li>
 * </ol>
 */
@Service
@Transactional(readOnly = true)
public class QcService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final QcRoundRepository qcRoundRepository;
    private final BatchRepository batchRepository;
    private final ShipmentRepository shipmentRepository;
    private final RecipeRepository recipeRepository;

    public QcService(QcRoundRepository qcRoundRepository,
                     BatchRepository batchRepository,
                     ShipmentRepository shipmentRepository,
                     RecipeRepository recipeRepository) {
        this.qcRoundRepository = qcRoundRepository;
        this.batchRepository = batchRepository;
        this.shipmentRepository = shipmentRepository;
        this.recipeRepository = recipeRepository;
    }

    /** 批次详情：当前有效结论 + 全部历史轮次 */
    public Views.BatchView detail(Long batchId) {
        Batch b = batchRepository.findById(batchId).orElseThrow(() -> new BizException("批次不存在"));
        QcRound latest = b.getLatestQcId() == null ? null
                : qcRoundRepository.findById(b.getLatestQcId()).orElse(null);
        List<QcRound> rounds = qcRoundRepository.findAllByBatchIdOrderByRoundNoAsc(batchId);
        String recipeName = b.getRecipeId() == null ? ""
                : recipeRepository.findById(b.getRecipeId()).map(Recipe::getName).orElse("");
        return Views.BatchView.of(b, recipeName, latest, rounds);
    }

    /**
     * 发起一轮质检（送检）。
     * 首轮 = 待质检；此前已有任何轮次（放行/驳回/撤销）后再开 = 复检中。
     */
    @Transactional
    public Views.QcRoundView openRound(Long batchId, QcDtos.OpenRoundReq req) {
        Batch b = lockBatch(batchId, req.getExpectedVersion());
        if ("SCRAPPED".equals(b.getStatus())) {
            throw new BizException("批次已报废，不能发起质检");
        }
        QcRound current = currentRound(b);
        if (current != null && "OPEN".equals(current.getResult())) {
            throw new BizException("第 " + current.getRoundNo() + " 轮质检已在送检中，不能重复发起（请直接提交结论或等待复检）");
        }
        int nextNo = current == null ? 1 : current.getRoundNo() + 1;
        LocalDateTime now = LocalDateTime.now();
        QcRound r = new QcRound();
        r.setBatchId(batchId);
        r.setRoundNo(nextNo);
        // 送检时的批次状态快照：事后批次再怎么推进，这轮送检时是什么状态永远可查
        r.setBatchStatus(b.getStatus());
        r.setInspector(req.getInspector().trim());
        r.setResult("OPEN");
        r.setReadingsSummary(snapshotReadings(b));
        r.setOpenedAt(now);
        r.setSupersededRoundId(current == null ? null : current.getId());
        try {
            qcRoundRepository.saveAndFlush(r);
        } catch (DataIntegrityViolationException dup) {
            // uk_qc_open_round / uk_qc_round 兜底：并发双击、网络重试都只会留一轮
            throw new BizException("该批次已有进行中的质检轮次，请勿重复提交");
        }
        b.setLatestQcId(r.getId());
        b.setUpdatedAt(now);
        bumpVersion(b);
        batchRepository.save(b);

        // 复检（第 2 轮及以后）一开，原放行即挂起：尚未发出的出货立刻冻结
        if (nextNo > 1) {
            String reason = "批次 " + b.getBatchNo() + " 已发起第 " + nextNo + " 轮复检，原放行结论挂起";
            shipmentRepository.freezePending(b.getBatchNo(), reason);
        }
        return Views.QcRoundView.of(r, true);
    }

    /** 提交本轮结论：PASS 放行（并解冻待发出货）/ REJECT 驳回（并冻结待发出货） */
    @Transactional
    public Views.QcRoundView submit(Long batchId, QcDtos.SubmitRoundReq req) {
        Batch b = lockBatch(batchId, req.getExpectedVersion());
        QcRound r = requireOpenRound(b, req.getRoundId());
        boolean pass;
        String verdict = req.getVerdict() == null ? "" : req.getVerdict().trim().toUpperCase();
        switch (verdict) {
            case "PASS", "PASSED" -> pass = true;
            case "REJECT", "REJECTED" -> pass = false;
            default -> throw new BizException("质检结论取值非法：" + req.getVerdict() + "（只接受 PASS / REJECT）");
        }
        String reason = trim(req.getReason());
        if (pass) {
            if (req.getQcAbv() == null || req.getQcAbv().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("放行必须填写检验酒精度");
            }
            if (req.getSensory() == null || req.getSensory().isBlank()) {
                throw new BizException("放行必须填写感官结论");
            }
            r.setResult("PASSED");
            r.setQcAbv(req.getQcAbv());
            r.setSensory(req.getSensory().trim());
            r.setReason(reason);
        } else {
            if (reason == null) {
                throw new BizException("驳回必须填写原因");
            }
            r.setResult("REJECTED");
            r.setQcAbv(req.getQcAbv());
            r.setSensory(trim(req.getSensory()));
            r.setReason(reason);
        }
        LocalDateTime now = LocalDateTime.now();
        r.setSubmittedAt(now);
        r.setSubmittedBy(req.getOperator().trim());
        qcRoundRepository.save(r);
        b.setUpdatedAt(now);
        bumpVersion(b);
        batchRepository.save(b);

        if (pass) {
            // 重新放行：此前因撤销/复检/驳回冻结的待发单恢复；已发/已结款不在条件内，不受影响
            shipmentRepository.unfreezePending(b.getBatchNo());
        } else {
            shipmentRepository.freezePending(b.getBatchNo(),
                    "批次 " + b.getBatchNo() + " 最新质检结论：驳回" + (reason == null ? "" : "，" + reason));
        }
        return Views.QcRoundView.of(r, true);
    }

    /** 撤销放行：必须填原因；放行轮转为 REVOKED 并冻结待发出货（历史不删） */
    @Transactional
    public Views.QcRoundView revoke(Long batchId, QcDtos.RevokeReq req) {
        Batch b = lockBatch(batchId, req.getExpectedVersion());
        QcRound r = currentRound(b);
        if (r == null || !"PASSED".equals(r.getResult())) {
            throw new BizException("只有最新一轮为【已放行】时才能撤销；复检/驳回/撤销状态请改用发起复检");
        }
        if (req.getRoundId() != null && !req.getRoundId().equals(r.getId())) {
            throw new BizException("你操作的放行轮次已被其他复检/撤销取代，请刷新后再操作");
        }
        String reason = req.getReason() == null ? null : req.getReason().trim();
        if (reason == null || reason.isBlank()) {
            // 控制器层有 @NotBlank，这里是直接服务调用/绕过校验时的防御
            throw new BizException("撤销放行必须填写原因");
        }
        LocalDateTime now = LocalDateTime.now();
        // 不新开轮、不删旧数据：放行轮原地转 REVOKED，放行时的酒精度/感官/提交人全部保留
        r.setResult("REVOKED");
        r.setReason(reason);
        r.setRevokedAt(now);
        r.setRevokedBy(req.getOperator().trim());
        qcRoundRepository.save(r);
        b.setUpdatedAt(now);
        bumpVersion(b);
        batchRepository.save(b);

        int frozen = shipmentRepository.freezePending(b.getBatchNo(),
                "批次 " + b.getBatchNo() + " 放行已撤销：" + reason);
        // 返回里带冻结数：让操作人员立刻知道几张待发单被挂起
        Views.QcRoundView v = Views.QcRoundView.of(r, true);
        v.setReason(reason + "（已冻结 " + frozen + " 张待发出货单）");
        return v;
    }

    // ---------------- 内部工具 ----------------

    /** 行锁 + 乐观版本号双保险：并发的放行/撤销/下游操作在这里被串行化 */
    private Batch lockBatch(Long batchId, Integer expectedVersion) {
        Batch b = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new BizException("批次不存在"));
        if (expectedVersion != null && b.getQcVersion() != null
                && !expectedVersion.equals(b.getQcVersion())) {
            throw new BizException("批次质检状态已被其他人更新（版本 " + expectedVersion
                    + " 已过期，当前为 " + b.getQcVersion() + "），请刷新后重试");
        }
        return b;
    }

    /** 仅质检流转推进版本号；非质检的批次更新（入罐/出罐/读数）不动它 */
    private static void bumpVersion(Batch b) {
        b.setQcVersion((b.getQcVersion() == null ? 0 : b.getQcVersion()) + 1);
    }

    private QcRound currentRound(Batch b) {
        if (b.getLatestQcId() != null) {
            return qcRoundRepository.findById(b.getLatestQcId()).orElse(null);
        }
        return qcRoundRepository.findFirstByBatchIdOrderByRoundNoDesc(b.getId()).orElse(null);
    }

    /** 提交结论时校验：必须有一轮 OPEN，且前端提交的就是这轮，过期详情一律拒绝 */
    private QcRound requireOpenRound(Batch b, Long roundId) {
        QcRound r = currentRound(b);
        if (r == null || !"OPEN".equals(r.getResult())) {
            throw new BizException("当前没有进行中的质检轮次，不能提交结论");
        }
        if (roundId != null && !roundId.equals(r.getId())) {
            throw new BizException("你提交的第 " + describe(roundId) + " 轮已不是当前有效轮次，请刷新后再提交");
        }
        return r;
    }

    private String describe(Long roundId) {
        return roundId == null ? "?" : qcRoundRepository.findById(roundId)
                .map(x -> String.valueOf(x.getRoundNo())).orElse("?");
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * 送检时逐日温度/糖度摘要快照：记录条数、首末值，序列化成 JSON 文本钉在这轮上。
     * 之后批次曲线继续补录也不改变这轮送检时看到的内容。
     */
    private String snapshotReadings(Batch b) {
        try {
            Map<String, Object> snap = new LinkedHashMap<>();
            List<String> tempKeys = sortedKeys(b.getTempReadings());
            List<String> gravityKeys = sortedKeys(b.getGravityReadings());
            snap.put("tempDays", tempKeys.size());
            snap.put("gravityDays", gravityKeys.size());
            if (!tempKeys.isEmpty()) {
                snap.put("tempFirst", b.getTempReadings().get(tempKeys.get(0)));
                snap.put("tempLast", b.getTempReadings().get(tempKeys.get(tempKeys.size() - 1)));
            }
            if (!gravityKeys.isEmpty()) {
                snap.put("gravityFirst", b.getGravityReadings().get(gravityKeys.get(0)));
                snap.put("gravityLast", b.getGravityReadings().get(gravityKeys.get(gravityKeys.size() - 1)));
            }
            return JSON.writeValueAsString(snap);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> sortedKeys(Map<String, BigDecimal> map) {
        List<String> keys = new java.util.ArrayList<>();
        if (map != null) {
            keys.addAll(map.keySet());
        }
        keys.sort((a, c) -> Integer.compare(Views.dayKeyOrder(a), Views.dayKeyOrder(c)));
        return keys;
    }
}
