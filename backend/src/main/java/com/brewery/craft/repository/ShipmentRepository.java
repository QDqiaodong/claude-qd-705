package com.brewery.craft.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.brewery.craft.domain.Shipment;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    List<Shipment> findAllByOrderByShipDateDescIdAsc();

    Optional<Shipment> findByShipmentNo(String shipmentNo);

    boolean existsByShipmentNo(String shipmentNo);

    List<Shipment> findAllByStatusOrderByShipDateDescIdAsc(String status);

    List<Shipment> findAllByBatchNoOrderByIdAsc(String batchNo);

    long countByStatus(String status);
}
