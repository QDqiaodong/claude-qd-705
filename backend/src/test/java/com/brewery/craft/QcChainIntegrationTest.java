package com.brewery.craft;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.brewery.craft.common.BizException;
import com.brewery.craft.domain.Batch;
import com.brewery.craft.domain.Shipment;
import com.brewery.craft.dto.QcDtos;
import com.brewery.craft.dto.ShipmentDtos;
import com.brewery.craft.dto.Views;
import com.brewery.craft.repository.BatchRepository;
import com.brewery.craft.repository.QcRoundRepository;
import com.brewery.craft.repository.ShipmentRepository;
import com.brewery.craft.service.BatchService;
import com.brewery.craft.service.QcService;
import com.brewery.craft.service.ShipmentService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 质检放行 ↔ 灌装/出货联动的端到端验证（H2 MySQL 模式 + 种子数据）。
 */
@SpringBootTest
class QcChainIntegrationTest {

    @Autowired QcService qcService;
    @Autowired ShipmentService shipmentService;
    @Autowired BatchService batchService;
    @Autowired BatchRepository batchRepository;
    @Autowired QcRoundRepository qcRoundRepository;
    @Autowired ShipmentRepository shipmentRepository;

    // ================= 1. 从零走完整条链：送检→放行→灌装→出货→复检→驳回→再放行→结款→撤销 =================
    @Test
    void fullChain_release_bottling_shipment_recheck_reject_repass_pay_revoke() {
        Long batchId = seedConditionBatch("BC-IT-FULL", 1000);

        // 未质检：灌装/出货都被拦
        BizException e0 = assertThrows(BizException.class, () -> createBottling("BT-IT-0", "BC-IT-FULL"));
        assertTrue(e0.getMessage().contains("还没有质检记录"), e0.getMessage());

        // 开轮（送检），此时灌装仍被拦
        qcService.openRound(batchId, openReq("张三", null));
        Views.BatchView openView = qcService.detail(batchId);
        assertEquals("OPEN", openView.getQcState());
        assertTrue(openView.getQcBlockReason().contains("尚未放行"));
        BizException e1 = assertThrows(BizException.class, () -> createBottling("BT-IT-1", "BC-IT-FULL"));
        assertTrue(e1.getMessage().contains("正在质检中"), e1.getMessage());

        // 放行校验：缺酒精度/感官/驳回原因
        assertThrows(BizException.class, () -> qcService.submit(batchId, submit("PASS", "李四", null, "感官正常", null)));
        assertThrows(BizException.class, () -> qcService.submit(batchId, submit("PASS", "李四", new BigDecimal("5.2"), "  ", null)));
        assertThrows(BizException.class, () -> qcService.submit(batchId, submit("REJECT", "李四", null, null, null)));
        // 重复点击送检：不能产生第二轮
        assertThrows(BizException.class, () -> qcService.openRound(batchId, openReq("张三", null)));

        // 放行
        qcService.submit(batchId, submit("PASS", "李四", new BigDecimal("5.20"), "清亮，柑橘香，杀口正常", null));
        assertEquals("PASSED", qcService.detail(batchId).getQcState());

        // 灌装放行 + 出货必须有对应包装形式的灌装记录
        Views.BottlingView bottle = createBottling("BT-IT-B1", "BC-IT-FULL");
        assertNotNull(bottle.getQcRoundId(), "放行轮次必须随灌装单快照");
        ShipmentDtos.ShipmentSaveReq keg = shipmentReq("SH-IT-K1", "BC-IT-FULL", "KEG", 10);
        BizException eKeg = assertThrows(BizException.class, () -> shipmentService.createShipment(keg));
        assertTrue(eKeg.getMessage().contains("桶"), eKeg.getMessage());
        Views.ShipmentView ship = shipmentService.createShipment(shipmentReq("SH-IT-S1", "BC-IT-FULL", "BOTTLE", 100));
        assertFalse(ship.isFrozen());

        // 发起复检：旧放行保留，当前变复检中，待发出货冻结
        qcService.openRound(batchId, openReq("王五", null));
        Views.BatchView recheck = qcService.detail(batchId);
        assertEquals("RECHECK", recheck.getQcState());
        assertEquals(2, recheck.getQcRounds().size());
        assertEquals("PASSED", recheck.getQcRounds().get(0).getResult(), "旧轮放行结论不得被覆盖");
        assertTrue(recheck.getQcRounds().get(0).getReadingsSummary().contains("tempDays"));
        Shipment frozenShip = shipmentRepository.findByShipmentNo("SH-IT-S1").orElseThrow();
        assertTrue(frozenShip.getFrozen());
        assertNotNull(frozenShip.getFrozenReason());
        BizException eFrozen = assertThrows(BizException.class, () -> shipmentService.nextStatus(frozenShip.getId()));
        assertTrue(eFrozen.getMessage().contains("冻结"));
        assertThrows(BizException.class, () -> createBottling("BT-IT-B2", "BC-IT-FULL"));

        // 过期详情提交：拿第 1 轮 id 提交第 2 轮 → 拒绝
        Long round1Id = recheck.getQcRounds().get(0).getId();
        QcDtos.SubmitRoundReq stale = submit("REJECT", "王五", new BigDecimal("4.9"), "异味", "香味衰减");
        stale.setRoundId(round1Id);
        assertThrows(BizException.class, () -> qcService.submit(batchId, stale));

        // 第 2 轮驳回：历史不变，冻结依旧
        qcService.submit(batchId, submit("REJECT", "王五", new BigDecimal("4.90"), "有轻微纸板味", "感官不合格"));
        assertEquals("REJECTED", qcService.detail(batchId).getQcState());
        assertEquals("PASSED", qcService.detail(batchId).getQcRounds().get(0).getResult());
        assertTrue(shipmentRepository.findByShipmentNo("SH-IT-S1").orElseThrow().getFrozen());

        // 第 3 轮重新放行：待发单恢复并发货、结款；结款后撤销放行不影响历史单
        qcService.openRound(batchId, openReq("赵六", null));
        qcService.submit(batchId, submit("PASS", "赵六", new BigDecimal("5.30"), "复检合格，风味恢复", null));
        assertEquals("PASSED", qcService.detail(batchId).getQcState());
        Shipment recovered = shipmentRepository.findByShipmentNo("SH-IT-S1").orElseThrow();
        assertFalse(recovered.getFrozen());
        assertNull(recovered.getFrozenReason());
        shipmentService.nextStatus(recovered.getId()); // → SHIPPED
        shipmentService.nextStatus(recovered.getId()); // → PAID

        // 撤销必须填原因
        assertThrows(BizException.class,
                () -> qcService.revoke(batchId, revoke("  ", "赵六", null)));
        Views.QcRoundView revoked = qcService.revoke(batchId, revoke("客户投诉暂停", "赵六", null));
        assertTrue(revoked.getReason().contains("冻结 0 张"), "已发/已结款单据不得被冻结：" + revoked.getReason());
        Shipment paid = shipmentRepository.findByShipmentNo("SH-IT-S1").orElseThrow();
        assertEquals("PAID", paid.getStatus());
        assertFalse(paid.getFrozen(), "已结款历史单不能被改写");

        // 撤销后新建下游被拦；历史轮次完整：PASSED / REJECTED / REVOKED 三轮都在
        assertThrows(BizException.class, () -> createBottling("BT-IT-B3", "BC-IT-FULL"));
        assertThrows(BizException.class,
                () -> shipmentService.createShipment(shipmentReq("SH-IT-S2", "BC-IT-FULL", "BOTTLE", 1)));
        Views.BatchView fin = qcService.detail(batchId);
        assertEquals(3, fin.getQcRounds().size());
        assertEquals("PASSED", fin.getQcRounds().get(0).getResult());
        assertEquals("REJECTED", fin.getQcRounds().get(1).getResult());
        assertEquals("REVOKED", fin.getQcRounds().get(2).getResult());
        // 撤销不抹掉任何一轮：第 1 轮放行（李四/5.20）、第 2 轮驳回、第 3 轮放行后撤销（赵六/5.30）
        assertEquals("李四", fin.getQcRounds().get(0).getSubmittedBy());
        assertEquals(new BigDecimal("5.20"), fin.getQcRounds().get(0).getQcAbv());
        assertEquals("王五", fin.getQcRounds().get(1).getSubmittedBy());
        assertEquals("赵六", fin.getQcRounds().get(2).getSubmittedBy(), "撤销前行放行的提交人必须保留");
        assertEquals(new BigDecimal("5.30"), fin.getQcRounds().get(2).getQcAbv(), "撤销前行放行的酒精度必须保留");
        assertEquals("赵六", fin.getQcRounds().get(2).getRevokedBy());
    }

    // ================= 2. 种子数据：撤销/复检/驳回批次的冻结与拦截 =================
    @Test
    void seededStates_blockDownstream_andFreezePending() {
        // BC-2026-107 已撤销：SH-2026-10 冻结待发
        Shipment s10 = shipmentRepository.findByShipmentNo("SH-2026-10").orElseThrow();
        assertTrue(s10.getFrozen());
        assertThrows(BizException.class, () -> shipmentService.nextStatus(s10.getId()));
        assertThrows(BizException.class, () -> createBottling("BT-IT-SEED-1", "BC-2026-107"));
        assertThrows(BizException.class,
                () -> shipmentService.createShipment(shipmentReq("SH-IT-SEED-1", "BC-2026-107", "CAN", 1)));

        // BC-2026-103 复检中：SH-2026-11 冻结，灌装/出货入口阻止
        assertTrue(shipmentRepository.findByShipmentNo("SH-2026-11").orElseThrow().getFrozen());
        assertThrows(BizException.class, () -> createBottling("BT-IT-SEED-2", "BC-2026-103"));

        // BC-2026-104 已驳回：SH-2026-12 冻结
        assertTrue(shipmentRepository.findByShipmentNo("SH-2026-12").orElseThrow().getFrozen());
        assertThrows(BizException.class,
                () -> shipmentService.createShipment(shipmentReq("SH-IT-SEED-2", "BC-2026-104", "BOTTLE", 1)));

        // 已发/已结款历史单（108/109/110）不受撤销影响
        assertEquals("PAID", shipmentRepository.findByShipmentNo("SH-2026-03").orElseThrow().getStatus());
        assertFalse(shipmentRepository.findByShipmentNo("SH-2026-03").orElseThrow().getFrozen());

        // 报废 / 未成熟批次拦截
        assertThrows(BizException.class, () -> createBottling("BT-IT-SEED-3", "BC-2026-111"));
        assertThrows(BizException.class, () -> createBottling("BT-IT-SEED-4", "BC-2026-113"));

        // 已灌装且放行的批次仍可补建灌装单（PACKAGED 属可成熟态）
        assertNotNull(shipmentService.createBottling(
                bottlingReq("BT-IT-SEED-5", "BC-2026-108", "BOTTLE", 60)));
    }

    // ================= 3. 批次推进到「已灌装」同样受放行约束 =================
    @Test
    void advanceToPackaged_requiresPass() {
        // BC-2026-105 FERMENT 无质检：可推进到 CONDITION，但不能再推进到 PACKAGED
        Long id = batchRepository.findByBatchNo("BC-2026-105").orElseThrow().getId();
        batchService.advance(id, "CONDITION");
        BizException e = assertThrows(BizException.class, () -> batchService.advance(id, "PACKAGED"));
        assertTrue(e.getMessage().contains("质检"), e.getMessage());

        // 报废已有灌装单的批次（BC-2026-106 有 BT-2026-09）必须阻止
        assertThrows(BizException.class,
                () -> batchService.scrap(batchRepository.findByBatchNo("BC-2026-106").orElseThrow().getId()));
    }

    // ================= 4. 乐观版本号：过期提交被拒 =================
    @Test
    void staleVersion_isRejected() {
        Long id = seedConditionBatch("BC-IT-VER", 500);
        qcService.openRound(id, openReq("张三", 0));
        // 开轮后版本已是 1，仍拿过期版本 0 提交 → 必须拒绝
        assertThrows(BizException.class,
                () -> qcService.submit(id, submit("PASS", "张三", new BigDecimal("5.0"), "正常", null, 0, null)));
    }

    // ================= 5. 并发：撤销放行 ⚈ 发货，最终只能有一条合法状态链 =================
    @Test
    void concurrentRevokeAndShip_serializesToOneLegalChain() throws Exception {
        String batchNo = "BC-IT-CONC1";
        Long id = seedConditionBatch(batchNo, 600);
        qcService.openRound(id, openReq("张三", null));
        qcService.submit(id, submit("PASS", "张三", new BigDecimal("6.1"), "合格", null));
        createBottling("BT-IT-CONC1", batchNo);
        Views.ShipmentView ship = shipmentService.createShipment(shipmentReq("SH-IT-CONC1", batchNo, "BOTTLE", 50));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicReference<Throwable> shipError = new AtomicReference<>();
        Future<?> revokeF = pool.submit(() -> {
            await(start);
            qcService.revoke(id, revoke("突击撤销", "张三", null));
        });
        Future<?> shipF = pool.submit(() -> {
            await(start);
            try {
                shipmentService.nextStatus(ship.getId());
            } catch (Throwable t) {
                shipError.set(t);
            }
        });
        start.countDown();
        revokeF.get(10, TimeUnit.SECONDS);
        shipF.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        Shipment after = shipmentRepository.findByShipmentNo("SH-IT-CONC1").orElseThrow();
        if (after.getFrozen()) {
            // 撤销先到：单子必须停在 PENDING，发货被拒
            assertEquals("PENDING", after.getStatus());
            assertNotNull(shipError.get());
        } else {
            // 发货先到：单子必须是 SHIPPED 且撤销没有去改写它
            assertEquals("SHIPPED", after.getStatus());
            assertNull(shipError.get());
        }
        assertEquals("REVOKED", qcService.detail(id).getQcState());
    }

    // ================= 6. 并发：两人同时开轮，只能产生一轮 OPEN =================
    @Test
    void concurrentDoubleOpen_createsSingleRound() throws Exception {
        String batchNo = "BC-IT-CONC2";
        Long id = seedConditionBatch(batchNo, 600);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        Runnable task = () -> {
            await(start);
            try {
                qcService.openRound(id, openReq("张三", null));
                ok.incrementAndGet();
            } catch (BizException b) {
                rejected.incrementAndGet();
            }
        };
        Future<?> f1 = pool.submit(task);
        Future<?> f2 = pool.submit(task);
        start.countDown();
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        // 明确断言：恰成功一轮、并发的另一轮被业务拒绝，库里只有一轮
        assertEquals(1, ok.get(), "只能成功开一轮");
        assertEquals(1, rejected.get(), "并发的另一轮必须被拒绝");
        long openRounds = qcRoundRepository.findAllByBatchIdOrderByRoundNoAsc(id).size();
        assertEquals(1, openRounds, "不得生成重复轮次");
    }

    // ================= 7. 并发：放行 ⚈ 灌装，状态链保持一致 =================
    @Test
    void concurrentPassAndBottling_neverBottlesAgainstNonPass() throws Exception {
        String batchNo = "BC-IT-CONC3";
        Long id = seedConditionBatch(batchNo, 600);
        qcService.openRound(id, openReq("张三", null));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicReference<Throwable> bottleError = new AtomicReference<>();
        Future<?> passF = pool.submit(() -> {
            await(start);
            qcService.submit(id, submit("PASS", "张三", new BigDecimal("7.0"), "合格", null));
        });
        Future<?> bottleF = pool.submit(() -> {
            await(start);
            try {
                createBottling("BT-IT-CONC3", batchNo);
            } catch (Throwable t) {
                bottleError.set(t);
            }
        });
        start.countDown();
        passF.get(10, TimeUnit.SECONDS);
        bottleF.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        // 放行必然先于或晚于灌装：放行先成功 → 灌装合法；灌装先到 → 必被拒。
        // 最终批次一定是 PASSED，且若灌装成功，它快照的一定是 PASSED 轮（不可能是 OPEN）
        assertEquals("PASSED", qcService.detail(id).getQcState());
        if (bottleError.get() == null) {
            Views.BottlingView v = shipmentService.bottlings().stream()
                    .filter(b -> "BT-IT-CONC3".equals(b.getBottlingNo())).findFirst().orElseThrow();
            assertNotNull(v.getQcRoundId());
            assertEquals("PASSED", v.getQcState());
        } else {
            assertTrue(bottleError.get() instanceof BizException);
        }
    }

    // ---------------- helpers ----------------
    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private Long seedConditionBatch(String no, int volume) {
        Batch b = new Batch();
        b.setBatchNo(no);
        b.setBrewer("测试酿酒师");
        b.setStatus("CONDITION");
        b.setVolumeL(BigDecimal.valueOf(volume));
        b.setTempReadings(new LinkedHashMap<>(java.util.Map.of("D1", new BigDecimal("18.5"), "D2", new BigDecimal("19.0"))));
        b.setGravityReadings(new LinkedHashMap<>(java.util.Map.of("D1", new BigDecimal("12.0"), "D2", new BigDecimal("4.5"))));
        return batchRepository.saveAndFlush(b).getId();
    }

    private Views.BottlingView createBottling(String no, String batchNo) {
        return shipmentService.createBottling(bottlingReq(no, batchNo, "BOTTLE", 100));
    }

    private static ShipmentDtos.BottlingSaveReq bottlingReq(String no, String batchNo, String form, int qty) {
        ShipmentDtos.BottlingSaveReq r = new ShipmentDtos.BottlingSaveReq();
        r.setBottlingNo(no);
        r.setBatchNo(batchNo);
        r.setPackageForm(form);
        r.setQuantity(qty);
        return r;
    }

    private static ShipmentDtos.ShipmentSaveReq shipmentReq(String no, String batchNo, String form, int qty) {
        ShipmentDtos.ShipmentSaveReq r = new ShipmentDtos.ShipmentSaveReq();
        r.setShipmentNo(no);
        r.setCustomer("测试客户");
        r.setBatchNo(batchNo);
        r.setPackageForm(form);
        r.setQuantity(qty);
        r.setUnitPrice(new BigDecimal("20.00"));
        return r;
    }

    private static QcDtos.OpenRoundReq openReq(String inspector, Integer version) {
        QcDtos.OpenRoundReq r = new QcDtos.OpenRoundReq();
        r.setInspector(inspector);
        r.setExpectedVersion(version);
        return r;
    }

    private static QcDtos.SubmitRoundReq submit(String verdict, String operator, BigDecimal abv,
                                                String sensory, String reason) {
        return submit(verdict, operator, abv, sensory, reason, null, null);
    }

    private static QcDtos.SubmitRoundReq submit(String verdict, String operator, BigDecimal abv,
                                                String sensory, String reason,
                                                Integer version, Long roundId) {
        QcDtos.SubmitRoundReq r = new QcDtos.SubmitRoundReq();
        r.setVerdict(verdict);
        r.setOperator(operator);
        r.setQcAbv(abv);
        r.setSensory(sensory);
        r.setReason(reason);
        r.setExpectedVersion(version);
        r.setRoundId(roundId);
        return r;
    }

    private static QcDtos.RevokeReq revoke(String reason, String operator, Long roundId) {
        QcDtos.RevokeReq r = new QcDtos.RevokeReq();
        r.setReason(reason);
        r.setOperator(operator);
        r.setRoundId(roundId);
        return r;
    }
}
