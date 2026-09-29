package com.brewery.craft.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 批次质检轮次：一行 = 一轮质检（只增不改，可追溯历史）。
 *
 * <p>流转：OPEN（送检中）→ PASSED（放行）/ REJECTED（驳回）；
 * PASSED → REVOKED（撤销放行，必须填原因）。任何终态之后都只能发起【复检】，
 * 复检会新开下一轮，旧轮内容（送检状态快照、读数摘要、酒精度、感官结论、操作人、时间）
 * 原样保留，绝不在原行上覆盖。
 */
@Data
@Entity
@Table(name = "batch_qc_round")
public class QcRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    /** 轮次序号：同批次内从 1 递增 */
    @Column(name = "round_no", nullable = false)
    private Integer roundNo;

    /** 送检时批次状态快照（PLAN/.../CONDITION/PACKAGED/SCRAPPED） */
    @Column(name = "batch_status", nullable = false, length = 16)
    private String batchStatus;

    /** 送检操作人 */
    @Column(name = "inspector", nullable = false, length = 32)
    private String inspector;

    /** 提交结论操作人 */
    @Column(name = "submitted_by", length = 32)
    private String submittedBy;

    /** OPEN/PASSED/REJECTED/REVOKED */
    @Column(name = "result", nullable = false, length = 16)
    private String result;

    /** 检验酒精度 %vol（开轮时不填，提交结论时必须有） */
    @Column(name = "qc_abv", precision = 4, scale = 2)
    private BigDecimal qcAbv;

    /** 感官结论（色/香/味/澄清度） */
    @Column(name = "sensory", length = 512)
    private String sensory;

    /** 驳回/撤销/复检原因（撤销放行必填） */
    @Column(name = "reason", length = 512)
    private String reason;

    /** 送检时逐日温度/糖度摘要快照（JSON 文本），开轮后不再随批次曲线变化 */
    @Column(name = "readings_summary", length = 1024)
    private String readingsSummary;

    /** 开轮（送检）时间 */
    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    /** 提交放行/驳回时间 */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /** 撤销放行时间 */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /** 撤销放行操作人 */
    @Column(name = "revoked_by", length = 32)
    private String revokedBy;

    /** 本轮回溯/复检自哪一轮（链式留痕，可为空表示首轮） */
    @Column(name = "superseded_round_id")
    private Long supersededRoundId;
}
