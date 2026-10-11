package org.example.traveljavserver.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

/**
 * 行程持久化实体。
 * payload 保存完整 ItineraryVO JSON；列表页只读元字段，详情页再取 payload。
 */
@Data
@Entity
@Table(name = "trips", indexes = {
        @Index(name = "idx_trips_device", columnList = "deviceId"),
        @Index(name = "idx_trips_device_city", columnList = "deviceId,city")
})
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 匿名设备标识（前端生成并随 X-Device-Id 头发送；缺省为 anonymous） */
    @Column(nullable = false, length = 64)
    private String deviceId;

    @Column(nullable = false, length = 64)
    private String city;

    /** 天数（对齐前端字段名 day）；H2 中 day 是保留字，显式改列名 */
    @Column(name = "trip_days")
    private Integer day;

    /** 预算总额（元） */
    private Integer budget;

    @Column(length = 255)
    private String summary;

    /** 是否为降级行程 */
    private Boolean degraded;

    /** 用户备注（可编辑写回） */
    @Column(length = 500)
    private String note;

    /** 行程内容 SHA-256，用于同一设备下去重（内容完全相同的行程不重复保存） */
    @Column(nullable = false, length = 64)
    private String payloadHash;

    /** 完整行程 JSON（ItineraryVO） */
    @Lob
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
