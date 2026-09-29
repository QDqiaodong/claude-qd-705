package com.brewery.craft.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 批次质检放行链入参。 */
public final class QcDtos {

    private QcDtos() {
    }

    /** 发起一轮质检（送检）：记录送检批次状态快照与操作人 */
    @Data
    public static class OpenRoundReq {

        /** 开轮操作人（酿酒师/质检员） */
        @NotBlank(message = "操作人不能为空")
        private String inspector;

        /**
         * 前端手里这版批次的 qcVersion（乐观锁）。
         * 两个人同时开轮/放行时，过期版本直接被后端拒绝，不会产生重复轮次。
         */
        private Integer expectedVersion;
    }

    /** 提交本轮结论：放行 / 驳回 */
    @Data
    public static class SubmitRoundReq {

        /** PASS 放行 / REJECT 驳回 */
        @NotBlank(message = "质检结论不能为空")
        private String verdict;

        @NotBlank(message = "操作人不能为空")
        private String operator;

        /** 检验酒精度（放行必填） */
        private BigDecimal qcAbv;

        /** 感官结论（放行必填：色/香/味/澄清度） */
        private String sensory;

        /** 驳回原因（驳回必填） */
        private String reason;

        /** 前端提交时基于的轮次 id：过期详情（轮次已被别人复检取代）直接拒绝 */
        private Long roundId;

        private Integer expectedVersion;
    }

    /** 撤销放行：必须填写原因 */
    @Data
    public static class RevokeReq {

        @NotBlank(message = "撤销放行必须填写原因")
        private String reason;

        @NotBlank(message = "操作人不能为空")
        private String operator;

        private Long roundId;

        private Integer expectedVersion;
    }
}
