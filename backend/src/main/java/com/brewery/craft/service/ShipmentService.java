package com.brewery.craft.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.brewery.craft.common.BizException;
import com.brewery.craft.domain.Bottling;
import com.brewery.craft.domain.Shipment;
import com.brewery.craft.dto.ShipmentDtos;
import com.brewery.craft.dto.Views;
import com.brewery.craft.repository.BatchRepository;
import com.brewery.craft.repository.BottlingRepository;
import com.brewery.craft.repository.ShipmentRepository;

/** 模块五：灌装与出货。 */
@Service
public class ShipmentService {

    private final BottlingRepository bottlingRepository;
    private final ShipmentRepository shipmentRepository;
    private final BatchRepository batchRepository;

    public ShipmentService(BottlingRepository bottlingRepository,
                           ShipmentRepository shipmentRepository,
                           BatchRepository batchRepository) {
        this.bottlingRepository = bottlingRepository;
        this.shipmentRepository = shipmentRepository;
        this.batchRepository = batchRepository;
    }

    // ---------------- 灌装单 ----------------
    public List<Views.BottlingView> bottlings() {
        List<Views.BottlingView> list = new ArrayList<>();
        for (Bottling b : bottlingRepository.findAllByOrderByBottleDateDescIdAsc()) {
            list.add(Views.BottlingView.of(b));
        }
        return list;
    }

    @Transactional
    public Views.BottlingView createBottling(ShipmentDtos.BottlingSaveReq req) {
        if (bottlingRepository.existsByBottlingNo(req.getBottlingNo())) {
            throw new BizException("灌装单号 " + req.getBottlingNo() + " 已存在");
        }
        if (batchRepository.findByBatchNo(req.getBatchNo()).isEmpty()) {
            throw new BizException("批次 " + req.getBatchNo() + " 不存在");
        }
        if (req.getQuantity() == null || req.getQuantity() <= 0) {
            throw new BizException("灌装数量必须大于 0");
        }
        Bottling b = new Bottling();
        b.setBottlingNo(req.getBottlingNo());
        b.setBatchNo(req.getBatchNo());
        b.setPackageForm(req.getPackageForm() == null ? "BOTTLE" : req.getPackageForm());
        b.setQuantity(req.getQuantity());
        b.setBottleDate(req.getBottleDate() == null ? LocalDate.now() : req.getBottleDate());
        b.setLossL(req.getLossL() == null ? BigDecimal.ZERO : req.getLossL());
        b.setUpdatedAt(LocalDateTime.now());
        return Views.BottlingView.of(bottlingRepository.save(b));
    }

    // ---------------- 出货单 ----------------
    public List<Views.ShipmentView> shipments(String status) {
        List<Shipment> list = status == null || status.isBlank()
                ? shipmentRepository.findAllByOrderByShipDateDescIdAsc()
                : shipmentRepository.findAllByStatusOrderByShipDateDescIdAsc(status);
        List<Views.ShipmentView> views = new ArrayList<>();
        for (Shipment s : list) {
            views.add(Views.ShipmentView.of(s));
        }
        return views;
    }

    @Transactional
    public Views.ShipmentView createShipment(ShipmentDtos.ShipmentSaveReq req) {
        if (shipmentRepository.existsByShipmentNo(req.getShipmentNo())) {
            throw new BizException("出货单号 " + req.getShipmentNo() + " 已存在");
        }
        if (batchRepository.findByBatchNo(req.getBatchNo()).isEmpty()) {
            throw new BizException("批次 " + req.getBatchNo() + " 不存在");
        }
        if (req.getQuantity() == null || req.getQuantity() <= 0) {
            throw new BizException("出货数量必须大于 0");
        }
        if (req.getUnitPrice() == null || req.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("单价必须大于 0");
        }
        Shipment s = new Shipment();
        s.setShipmentNo(req.getShipmentNo());
        s.setCustomer(req.getCustomer());
        s.setBatchNo(req.getBatchNo());
        s.setPackageForm(req.getPackageForm() == null ? "BOTTLE" : req.getPackageForm());
        s.setQuantity(req.getQuantity());
        s.setUnitPrice(req.getUnitPrice());
        s.setAmount(req.getUnitPrice().multiply(BigDecimal.valueOf(req.getQuantity())).setScale(2, RoundingMode.HALF_UP));
        s.setShipDate(req.getShipDate() == null ? LocalDate.now() : req.getShipDate());
        s.setStatus(req.getStatus() == null || req.getStatus().isBlank() ? "PENDING" : req.getStatus());
        if (Views.shipmentStatusName(s.getStatus()).equals(s.getStatus())) {
            throw new BizException("出货状态取值非法：" + s.getStatus());
        }
        s.setUpdatedAt(LocalDateTime.now());
        return Views.ShipmentView.of(shipmentRepository.save(s));
    }

    /** 待发 → 已发 → 已结款 */
    @Transactional
    public Views.ShipmentView nextStatus(Long id) {
        Shipment s = shipmentRepository.findById(id).orElseThrow(() -> new BizException("出货单不存在"));
        String next = Views.shipmentNextStatus(s.getStatus());
        if (next == null) {
            throw new BizException("该出货单已结款，没有后续状态");
        }
        s.setStatus(next);
        s.setUpdatedAt(LocalDateTime.now());
        return Views.ShipmentView.of(shipmentRepository.save(s));
    }

    /** 金额与出货量汇总 */
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
        for (Map.Entry<String, List<Shipment>> e : grouped.entrySet()) {
            long qty = 0;
            BigDecimal amount = BigDecimal.ZERO;
            for (Shipment s : e.getValue()) {
                qty += s.getQuantity() == null ? 0 : s.getQuantity();
                amount = amount.add(s.getAmount() == null ? BigDecimal.ZERO : s.getAmount());
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
        return summary;
    }
}
