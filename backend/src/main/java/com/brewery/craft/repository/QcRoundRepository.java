package com.brewery.craft.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.brewery.craft.domain.QcRound;

public interface QcRoundRepository extends JpaRepository<QcRound, Long> {

    /** 一个批次的全部轮次，按时间顺序（= 轮次序号）排列，构成可追溯历史 */
    List<QcRound> findAllByBatchIdOrderByRoundNoAsc(Long batchId);

    Optional<QcRound> findByBatchIdAndRoundNo(Long batchId, Integer roundNo);

    Optional<QcRound> findFirstByBatchIdOrderByRoundNoDesc(Long batchId);

    long countByBatchId(Long batchId);
}
