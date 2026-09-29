package com.brewery.craft.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.brewery.craft.domain.Batch;

import jakarta.persistence.LockModeType;

public interface BatchRepository extends JpaRepository<Batch, Long> {

    List<Batch> findAllByOrderByBatchNoAsc();

    Optional<Batch> findByBatchNo(String batchNo);

    boolean existsByBatchNo(String batchNo);

    List<Batch> findAllByStatusOrderByBatchNoAsc(String status);

    List<Batch> findAllByRecipeIdOrderByBatchNoAsc(Long recipeId);

    List<Batch> findAllByFermenterNoOrderByBatchNoAsc(String fermenterNo);

    long countByStatus(String status);

    /** 质检开轮/提交/撤销与下游灌装、出货互斥：在批次行上加写锁串行化 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Batch b where b.id = :id")
    Optional<Batch> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Batch b where b.batchNo = :batchNo")
    Optional<Batch> findByBatchNoForUpdate(@Param("batchNo") String batchNo);
}
