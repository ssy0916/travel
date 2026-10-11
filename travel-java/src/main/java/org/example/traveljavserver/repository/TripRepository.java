package org.example.traveljavserver.repository;

import org.example.traveljavserver.entity.Trip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 行程持久化 Repository。
 * 所有查询均带 deviceId，做匿名设备隔离。
 */
public interface TripRepository extends JpaRepository<Trip, Long> {

    Page<Trip> findByDeviceId(String deviceId, Pageable pageable);

    Page<Trip> findByDeviceIdAndCity(String deviceId, String city, Pageable pageable);

    Optional<Trip> findByIdAndDeviceId(Long id, String deviceId);

    /** 同一设备下按 payload 哈希去重 */
    Optional<Trip> findByDeviceIdAndPayloadHash(String deviceId, String payloadHash);
}
