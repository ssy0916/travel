package org.example.traveljavserver.vo;

import lombok.Data;

import java.util.Map;

/**
 * 行程返回 VO。
 * 列表场景 data 为 null（只返回元字段）；详情场景 data 为完整行程 JSON。
 */
@Data
public class TripVO {

    private Long id;
    private String city;
    private Integer day;
    private Integer budget;
    private String summary;
    private Boolean degraded;
    private String note;

    /** 完整行程 JSON（仅详情接口返回） */
    private Map<String, Object> data;

    /** 保存时内容完全相同的行程已存在：true 且返回已存在记录 */
    private Boolean duplicated;

    /**  epoch 毫秒，对齐前端 formatTime(ts) */
    private Long createdAt;
    private Long updatedAt;
}
