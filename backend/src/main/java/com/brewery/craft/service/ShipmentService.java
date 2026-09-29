package com.brewery.craft.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.brewery.craft.common.BizException;
import com.brewery.craft.domain.Batch;
import com.brewery.craft.domain.Bottling;
import com.brewery.craft.domain.QcRound;
import com.brewery.craft.domain.Shipment;
import com.brewery.craft.dto.ShipmentDtos;
import com.brewery.craft.dto.Views;
import com.brewery.craft.repository.BatchRepository;
import com.brewery.craft.repository.BottlingRepository;
import com.brewery.craft.repository.QcRoundRepository;
import com.brewery.craft.repository.ShipmentRepository;

/** 模块五：灌装与出货（质检放行是硬约束，不是提示文字）。 */
@Service
@Transactional(readOnly = true)
public class ShipmentService {

    private final BottlingRepository bottlingRepository;
    private final ShipmentRepository shipmentRepository;
    private final BatchRepository batchRepository;
    private final QcRoundRepository qcRoundRepository;

    public ShipmentService(BottlingRepository bottlingRepository,
                           ShipmentRepository shipmentRepository,
                           BatchRepository batchRepository,
                           QcRoundRepository qcRoundRepository) {
        this.bottlingRepository = bottlingRepository;
        this.shipmentRepository = shipmentRepository;
        this.batchRepository = batchRepository;
        this.qcRoundRepository = qcRoundRepository;
    }

    // ---------------- 灌装单 ----------------
    public List<Views.BottlingView> bottlings() {
        List<Bottling> all = bottlingRepository.findAllByOrderByBottleDateDescIdAsc();
        Map<String, String> stateByBatch = batchQcStates(
                all.stream().map(Bottling::getBatchNo).distinct().toList());
        List<Views.BottlingView> list = new ArrayList<>();
        for (Bottling b : all) {
            // 灌装单创建后放行轮次已快照；列表同时显示该批次【当前】质检态，便于发现撤销/复检
            list.add(Views.BottlingView.of(b, stateByBatch.getOrDefault(b.getBatchNo(), "NONE")));
        }
        return list;
    }

    @Transactional
    public Views.BottlingView createBottling(ShipmentDtos.BottlingSaveReq req) {
        if (bottlingRepository.existsByBottlingNo(req.getBottlingNo())) {
            throw new BizException("灌装单号 " + req.getBottlingNo() + " 已存在");
        }
        if (req.getQuantity() == null || req.getQuantity() <= 0) {
            throw new BizException("灌装数量必须大于 0");
        }
        String form = normalizePackageForm(req.getPackageForm());
        // 先锁批次行：和质检的放行/撤销/复检串行，杜绝「页面已放行、后端还在放灌装」的窗口
        Batch batch = batchRepository.findByBatchNoForUpdate(req.getBatchNo())
                .orElseThrow(() -> new BizException("批次 " + req.getBatchNo() + " 不存在"));
        QcRound latest = currentRound(batch);
        String blocked = QcGate.bottlingBlockReason(batch, latest);
        if (blocked != null) {
            throw new BizException("不能新建灌装单：" + blocked);
        }
        Bottling b = new Bottling();
        b.setBottlingNo(req.getBottlingNo());
        b.setBatchNo(req.getBatchNo());
        b.setPackageForm(form);
        b.setQuantity(req.getQuantity());
        b.setBottleDate(req.getBottleDate() == null ? LocalDate.now() : req.getBottleDate());
        b.setLossL(req.getLossL() == null ? BigDecimal.ZERO : req.getLossL());
        // 钉住是哪一轮放的行：事后撤销/复检不改写这张灌装单
        b.setQcRoundId(latest.getId());
        b.setUpdatedAt(LocalDateTime.now());
        return Views.BottlingView.of(bottlingRepository.save(b), QcGate.stateOf(latest).name());
    }

    // ---------------- 出货单 ----------------
    public List<Views.ShipmentView> shipments(String status) {
        List<Shipment> list = status == null || status.isBlank()
                ? shipmentRepository.findAllByOrderByShipDateDescIdAsc()
                : shipmentRepository.findAllByStatusOrderByShipDateDescIdAsc(status);
        Map<String, String> stateByBatch = batchQcStates(
                list.stream().map(Shipment::getBatchNo).distinct().toList());
        List<Views.ShipmentView> views = new ArrayList<>();
        for (Shipment s : list) {
            views.add(Views.ShipmentView.of(s, stateByBatch.getOrDefault(s.getBatchNo(), "NONE")));
        }
        return views;
    }

    @Transactional
    public Views.ShipmentView createShipment(ShipmentDtos.ShipmentSaveReq req) {
        if (shipmentRepository.existsByShipmentNo(req.getShipmentNo())) {
            throw new BizException("出货单号 " + req.getShipmentNo() + " 已存在");
        }
        if (req.getQuantity() == null || req.getQuantity() <= 0) {
            throw new BizException("出货数量必须大于 0");
        }
        if (req.getUnitPrice() == null || req.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("单价必须大于 0");
        }
        String form = normalizePackageForm(req.getPackageForm());
        Batch batch = batchRepository.findByBatchNoForUpdate(req.getBatchNo())
                .orElseThrow(() -> new BizException("批次 " + req.getBatchNo() + " 不存在"));
        QcRound latest = currentRound(batch);
        String blocked = QcGate.shipmentBlockReason(batch, latest);
        if (blocked != null) {
            throw new BizException("不能新建出货单：" + blocked);
        }
        // 出货必须基于该批次【已有】的灌装记录，且包装形式要对得上
        List<Bottling> bottlings = bottlingRepository.findAllByBatchNoOrderByIdAsc(req.getBatchNo());
        if (bottlings.isEmpty()) {
            throw new BizException("不能新建出货单：批次 " + req.getBatchNo() + " 还没有任何灌装记录");
        }
        boolean formMatched = bottlings.stream().anyMatch(x -> form.equals(x.getPackageForm()));
        if (!formMatched) {
            throw new BizException("不能新建出货单：批次 " + req.getBatchNo() + " 没有「"
                    + Views.packageFormName(form) + "」形式的灌装记录");
        }
        Shipment s = new Shipment();
        s.setShipmentNo(req.getShipmentNo());
        s.setCustomer(req.getCustomer());
        s.setBatchNo(req.getBatchNo());
        s.setPackageForm(form);
        s.setQuantity(req.getQuantity());
        s.setUnitPrice(req.getUnitPrice());
        s.setAmount(req.getUnitPrice().multiply(BigDecimal.valueOf(req.getQuantity())).setScale(2, RoundingMode.HALF_UP));
        s.setShipDate(req.getShipDate() == null ? LocalDate.now() : req.getShipDate());
        s.setStatus("PENDING");
        // 创建时再次确认放行结论仍有效，并把放行轮快照下来
        s.setQcRoundId(latest.getId());
        s.setFrozen(false);
        s.setUpdatedAt(LocalDateTime.now());
        return Views.ShipmentView.of(shipmentRepository.save(s), QcGate.stateOf(latest).name());
    }

    /**
     * 待发 → 已发 → 已结款。
     * 冻结单禁止推进；推进前在批次行锁下再次确认放行仍有效，
     * 与「撤销放行」严格按 批次 → 出货单 的顺序加锁，二者并发只会有一个成功。
     */
    @Transactional
    public Views.ShipmentView nextStatus(Long id) {
        Shipment preview = shipmentRepository.findById(id).orElseThrow(() -> new BizException("出货单不存在"));
        // 统一加锁顺序【先批次、后出货单】，与质检撤销（批次 → 批量更新出货行）一致，避免互等死锁
        Batch batch = batchRepository.findByBatchNoForUpdate(preview.getBatchNo())
                .orElseThrow(() -> new BizException("出货单对应批次不存在"));
        QcRound latest = currentRound(batch);
        Shipment s = shipmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("出货单不存在"));
        if (Boolean.TRUE.equals(s.getFrozen())) {
            throw new BizException("出货单已被质检冻结"
                    + (s.getFrozenReason() == null ? "" : "：" + s.getFrozenReason())
                    + "；待批次重新放行后才能发货");
        }
        String next = Views.shipmentNextStatus(s.getStatus());
        if (next == null) {
            throw new BizException("该出货单已结款，没有后续状态");
        }
        if ("PENDING".equals(s.getStatus()) || "SHIPPED".equals(s.getStatus())) {
            String blocked = QcGate.shipmentBlockReason(batch, latest);
            if (blocked != null) {
                // 正常情况下前面冻结已拦住；这是撤销与发货同刻到达时的终态兜底
                throw new BizException("质检放行已失效，出货被阻止：" + blocked);
            }
        }
        s.setStatus(next);
        s.setUpdatedAt(LocalDateTime.now());
        return Views.ShipmentView.of(shipmentRepository.save(s), QcGate.stateOf(latest).name());
    }

    /** 金额与出货量汇总（含质检冻结单数） */
    public Views.ShipmentSummary summary() {
        List<Shipment> all = shipmentRepository.findAllByOrderByShipDateDescIdAsc();
        Map<String, List<Shipment>> grouped = new java.util.LinkedHashMap<>();
        for (String st : List.of("PENDING", "SHIPPED", "PAID")) {
            grouped.put(st, new ArrayList<>());
        }
        for (Shipment s : all) {
            grouped.computeIfAbsent(s.getStatus(), k -> new ArrayList<>()).add(s);
        }
        Views.ShipmentSummary summary = new Views.ShipmentSummary();
        BigDecimal amountTotal = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        long qtyTotal = 0;
        long frozen = 0;
        for (Map.Entry<String, List<Shipment>> e : grouped.entrySet()) {
            long qty = 0;
            BigDecimal amount = BigDecimal.ZERO;
            for (Shipment s : e.getValue()) {
                qty += s.getQuantity() == null ? 0 : s.getQuantity();
                amount = amount.add(s.getAmount() == null ? BigDecimal.ZERO : s.getAmount());
                if (Boolean.TRUE.equals(s.getFrozen())) {
                    frozen++;
                }
            }
            Views.ShipmentSummaryItem item = new Views.ShipmentSummaryItem();
            item.setStatus(e.getKey());
            item.setStatusName(Views.shipmentStatusName(e.getKey()));
            item.setCount(e.getValue().size());
            item.setQuantity(qty);
            item.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
            summary.getItems().add(item);
            amountTotal = amountTotal.add(amount);
            qtyTotal += qty;
            if ("PAID".equals(e.getKey())) {
                paid = amount;
            }
        }
        summary.setOrderTotal(all.size());
        summary.setQuantityTotal(qtyTotal);
        summary.setAmountTotal(amountTotal.setScale(2, RoundingMode.HALF_UP));
        summary.setPaidAmount(paid.setScale(2, RoundingMode.HALF_UP));
        summary.setFrozenCount(frozen);
        return summary;
    }

    // ---------------- 内部工具 ----------------

    private QcRound currentRound(Batch batch) {
        if (batch.getLatestQcId() == null) {
            return null;
        }
        return qcRoundRepository.findById(batch.getLatestQcId()).orElse(null);
    }

    private Map<String, String> batchQcStates(List<String> batchNos) {
        Map<String, String> result = new HashMap<>();
        for (String no : batchNos) {
            batchRepository.findByBatchNo(no).ifPresent(b -> {
                QcRound latest = b.getLatestQcId() == null ? null
                        : qcRoundRepository.findById(b.getLatestQcId()).orElse(null);
                result.put(no, QcGate.stateOf(latest).name());
            });
        }
        return result;
    }

    private String normalizePackageForm(String form) {
        String f = form == null || form.isBlank() ? "BOTTLE" : form.trim().toUpperCase();
        if (!"BOTTLE".equals(f) && !"CAN".equals(f) && !"KEG".equals(f)) {
            throw new BizException("包装形式取值非法：" + form + "（只接受 BOTTLE/CAN/KEG）");
        }
        return f;
    }
}
