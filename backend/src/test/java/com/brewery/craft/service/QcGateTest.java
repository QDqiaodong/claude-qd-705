package com.brewery.craft.service;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.brewery.craft.domain.Batch;
import com.brewery.craft.domain.QcRound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 裁决矩阵：所有下游拦截口径只允许出自 QcGate 一处。 */
class QcGateTest {

    @Test
    void stateDerivation_followsLatestRoundOnly() {
        assertEquals(QcGate.QcState.NONE, QcGate.stateOf(null));
        assertEquals(QcGate.QcState.OPEN, QcGate.stateOf(round(1, "OPEN")));
        assertEquals(QcGate.QcState.RECHECK, QcGate.stateOf(round(2, "OPEN")));
        assertEquals(QcGate.QcState.PASSED, QcGate.stateOf(round(2, "PASSED")));
        assertEquals(QcGate.QcState.REJECTED, QcGate.stateOf(round(3, "REJECTED")));
        assertEquals(QcGate.QcState.REVOKED, QcGate.stateOf(round(2, "REVOKED")));
    }

    @Test
    void bottlingGate_everyNonPassStateHasStableReason() {
        for (String result : new String[]{"OPEN", "PASSED", "REJECTED", "REVOKED"}) {
            // 仅 PASSED 放行；送检/驳回/撤销均给出业务原因
            QcRound r = "OPEN".equals(result) ? round(1, result) : round(2, result);
            String reason = QcGate.qcBlockReason(r);
            if ("PASSED".equals(result)) {
                assertNull(reason);
            } else {
                assertTrue(reason.length() > 4, result + " 必须给出可读业务原因");
            }
        }
        assertTrue(QcGate.qcBlockReason(null).contains("还没有质检记录"));
        assertTrue(QcGate.qcBlockReason(round(2, "OPEN")).contains("复检中"));
    }

    @Test
    void bottlingGate_combinesMaturityReadingsAndQc() {
        Batch maturePass = batch("CONDITION", true);
        assertNull(QcGate.bottlingBlockReason(maturePass, round(1, "PASSED")),
                "熟成 + 测量齐全 + 放行 => 可灌装");
        assertNull(QcGate.bottlingBlockReason(batch("PACKAGED", true), round(1, "PASSED")));

        assertTrue(QcGate.bottlingBlockReason(batch("SCRAPPED", true), round(1, "PASSED")).contains("报废"));
        assertTrue(QcGate.bottlingBlockReason(batch("FERMENT", true), round(1, "PASSED")).contains("可成熟"));
        assertTrue(QcGate.bottlingBlockReason(batch("CONDITION", false), round(1, "PASSED")).contains("测量记录"));
        assertTrue(QcGate.bottlingBlockReason(batch("CONDITION", true), round(1, "REVOKED")).contains("撤销"));
        assertTrue(QcGate.bottlingBlockReason(batch("CONDITION", true), null).contains("还没有质检记录"));
    }

    @Test
    void rejectedReason_isCarriedIntoBlockMessage() {
        QcRound rejected = round(1, "REJECTED");
        rejected.setReason("发酵度不达标");
        assertTrue(QcGate.shipmentBlockReason(batch("CONDITION", true), rejected).contains("发酵度不达标"));
        assertFalse(QcGate.shipmentBlockReason(batch("CONDITION", true), round(1, "PASSED")) != null);
    }

    private static QcRound round(int no, String result) {
        QcRound r = new QcRound();
        r.setRoundNo(no);
        r.setResult(result);
        return r;
    }

    private static Batch batch(String status, boolean withReadings) {
        Batch b = new Batch();
        b.setStatus(status);
        if (withReadings) {
            b.setTempReadings(Map.of("D1", new BigDecimal("19.0")));
            b.setGravityReadings(Map.of("D1", new BigDecimal("5.0")));
        }
        return b;
    }
}
