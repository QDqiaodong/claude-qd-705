package com.brewery.craft.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.brewery.craft.domain.Batch;

public interface BatchRepository extends JpaRepository<Batch, Long> {

    List<Batch> findAllByOrderByBatchNoAsc();

    Optional<Batch> findByBatchNo(String batchNo);

    boolean existsByBatchNo(String batchNo);

    List<Batch> findAllByStatusOrderByBatchNoAsc(String status);

    List<Batch> findAllByRecipeIdOrderByBatchNoAsc(Long recipeId);

    List<Batch> findAllByFermenterNoOrderByBatchNoAsc(String fermenterNo);

    long countByStatus(String status);
}
