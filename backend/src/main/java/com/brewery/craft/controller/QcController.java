package com.brewery.craft.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.brewery.craft.common.ApiResponse;
import com.brewery.craft.dto.QcDtos;
import com.brewery.craft.dto.Views;
import com.brewery.craft.service.QcService;

import jakarta.validation.Valid;

/**
 * 批次质检放行链：
 *   POST /batches/{id}/qc/rounds           发起一轮质检（首轮送检 / 再次发起=复检）
 *   POST /batches/{id}/qc/submit           提交本轮结论（PASS 放行 / REJECT 驳回）
 *   POST /batches/{id}/qc/revoke           撤销放行（必须填原因）
 *   GET  /batches/{id}/qc                  当前有效结论 + 全部历史轮次
 */
@RestController
@RequestMapping("/api/batches/{batchId}/qc")
public class QcController {

    private final QcService qcService;

    public QcController(QcService qcService) {
        this.qcService = qcService;
    }

    @GetMapping
    public ApiResponse<Views.BatchView> detail(@PathVariable("batchId") Long batchId) {
        return ApiResponse.success(qcService.detail(batchId));
    }

    @PostMapping("/rounds")
    public ApiResponse<Views.QcRoundView> open(@PathVariable("batchId") Long batchId,
                                               @Valid @RequestBody QcDtos.OpenRoundReq req) {
        return ApiResponse.success("质检轮次已发起（送检）", qcService.openRound(batchId, req));
    }

    @PostMapping("/submit")
    public ApiResponse<Views.QcRoundView> submit(@PathVariable("batchId") Long batchId,
                                                 @Valid @RequestBody QcDtos.SubmitRoundReq req) {
        Views.QcRoundView v = qcService.submit(batchId, req);
        String msg = "PASSED".equals(v.getResult())
                ? "第 " + v.getRoundNo() + " 轮已放行"
                : "第 " + v.getRoundNo() + " 轮已驳回";
        return ApiResponse.success(msg, v);
    }

    @PostMapping("/revoke")
    public ApiResponse<Views.QcRoundView> revoke(@PathVariable("batchId") Long batchId,
                                                 @Valid @RequestBody QcDtos.RevokeReq req) {
        return ApiResponse.success("放行已撤销，未发出货已冻结", qcService.revoke(batchId, req));
    }
}
