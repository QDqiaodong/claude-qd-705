package com.brewery.craft.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.brewery.craft.domain.Shipment;

import jakarta.persistence.LockModeType;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    List<Shipment> findAllByOrderByShipDateDescIdAsc();

    Optional<Shipment> findByShipmentNo(String shipmentNo);

    boolean existsByShipmentNo(String shipmentNo);

    List<Shipment> findAllByStatusOrderByShipDateDescIdAsc(String status);

    List<Shipment> findAllByBatchNoOrderByIdAsc(String batchNo);

    long countByStatus(String status);

    /** 推进状态时加行锁：与「撤销放行冻结」互斥，防止同一张单同时被冻结和发出 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shipment s where s.id = :id")
    Optional<Shipment> findByIdForUpdate(@Param("id") Long id);

    /** 该批次全部【尚未发出】的待发单（已发/已结款是历史终态，绝不动它） */
    List<Shipment> findAllByBatchNoAndStatusOrderByIdAsc(String batchNo, String status);

    /**
     * 冻结：只作用于 PENDING 单。返回受影响行数。
     * 用条件 UPDATE 而非先查后改，和「推进出货」抢行锁时数据库层面直接串行化。
     */
    @Modifying
    @Query("update Shipment s set s.frozen = true, s.frozenReason = :reason, s.frozenAt = CURRENT_TIMESTAMP, s.updatedAt = CURRENT_TIMESTAMP "
            + "where s.batchNo = :batchNo and s.status = 'PENDING' and s.frozen = false")
    int freezePending(@Param("batchNo") String batchNo, @Param("reason") String reason);

    /** 重新放行后解冻：冻结原因留痕清空，历史冻结时间随解冻失效 */
    @Modifying
    @Query("update Shipment s set s.frozen = false, s.frozenReason = null, s.frozenAt = null, s.updatedAt = CURRENT_TIMESTAMP "
            + "where s.batchNo = :batchNo and s.status = 'PENDING' and s.frozen = true")
    int unfreezePending(@Param("batchNo") String batchNo);
}
