package com.brewery.craft.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP 层验证：
 *   - 批次列表/详情带质检态与历史轮次；
 *   - 撤销放行缺原因 → 400；
 *   - 对复检中/已撤销/已驳回批次建灌装单/出货单 → 400 + 中文业务原因；
 *   - 冻结单推进 → 400。
 */
@SpringBootTest
@AutoConfigureMockMvc
class QcChainWebTest {

    @Autowired MockMvc mvc;

    @Test
    void batchListAndDetailExposeQcStateAndRounds() throws Exception {
        // BC-2026-107 = 已撤销（2 轮历史）
        mvc.perform(get("/api/batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data[0].qcStateName").exists());
        mvc.perform(get("/api/batches/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qcState").value("REVOKED"))
                .andExpect(jsonPath("$.data.qcStateName").value("已撤销"))
                .andExpect(jsonPath("$.data.qcRounds.length()").value(2))
                .andExpect(jsonPath("$.data.qcRounds[0].result").value("PASSED"))
                .andExpect(jsonPath("$.data.qcRounds[1].result").value("REVOKED"))
                .andExpect(jsonPath("$.data.qcRounds[1].reason").isNotEmpty());
        // BC-2026-103 = 复检中
        mvc.perform(get("/api/batches/3"))
                .andExpect(jsonPath("$.data.qcState").value("RECHECK"))
                .andExpect(jsonPath("$.data.qcRounds.length()").value(2));
    }

    @Test
    void bottlingAgainstRevokedBatch_is400WithBusinessReason() throws Exception {
        mvc.perform(post("/api/shipping/bottlings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bottlingNo":"BT-WEB-1","batchNo":"BC-2026-107",
                                 "packageForm":"BOTTLE","quantity":10}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("撤销")));
    }

    @Test
    void shipmentAgainstRejectedBatch_is400() throws Exception {
        mvc.perform(post("/api/shipping/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shipmentNo":"SH-WEB-1","customer":"x","batchNo":"BC-2026-104",
                                 "packageForm":"BOTTLE","quantity":10,"unitPrice":20}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("驳回")));
    }

    @Test
    void frozenShipmentCannotShip_andShipmentsExposeFrozen() throws Exception {
        // SH-2026-12（BC-2026-104 已驳回）冻结中
        mvc.perform(get("/api/shipping/shipments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.shipmentNo=='SH-2026-12')].frozen").value(
                        org.hamcrest.Matchers.hasItem(true)));
        mvc.perform(post("/api/shipping/shipments/12/next"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("冻结")));
    }

    @Test
    void revokeWithoutReason_is400() throws Exception {
        // 对一个 PASSED 批次（BC-2026-108）撤销但不带原因 → 校验失败
        mvc.perform(post("/api/batches/8/qc/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator\":\"u\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("原因")));
    }
}
