package com.brewery.craft.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 模块五之二：出货单。 */
@Data
@Entity
@Table(name = "shipment")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 出货单号 */
    @Column(name = "shipment_no", nullable = false, length = 24)
    private String shipmentNo;

    /** 客户 */
    @Column(name = "customer", nullable = false, length = 64)
    private String customer;

    /** 批次号 */
    @Column(name = "batch_no", nullable = false, length = 24)
    private String batchNo;

    /** 包装形式：BOTTLE 瓶 / CAN 易拉罐 / KEG 桶 */
    @Column(name = "package_form", nullable = false, length = 16)
    private String packageForm;

    /** 数量（瓶/罐/桶） */
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /** 单价（元） */
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /** 金额 = 数量 × 单价 */
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "ship_date")
    private LocalDate shipDate;

    /** PENDING 待发 / SHIPPED 已发 / PAID 已结款 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 创建出货时确认的放行轮次 id（快照） */
    @Column(name = "qc_round_id")
    private Long qcRoundId;

    /** 是否被质检冻结：撤销放行/复检中/驳回时冻结待发出货，重新放行后解冻 */
    @Column(name = "frozen", nullable = false)
    private Boolean frozen = false;

    /** 冻结原因（撤销/复检/驳回的业务原因） */
    @Column(name = "frozen_reason", length = 512)
    private String frozenReason;

    /** 最近一次冻结时间 */
    @Column(name = "frozen_at")
    private LocalDateTime frozenAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
