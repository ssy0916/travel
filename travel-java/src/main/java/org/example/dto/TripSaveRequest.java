package org.example.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 行程保存/编辑请求。
 * itinerary 为完整行程 JSON（对应 ItineraryVO），服务端从中提取 city/day/budget 等元字段。
 */
@Data
public class TripSaveRequest {

    /** 完整行程 JSON（保存时必填；编辑备注时可不传） */
    private Map<String, Object> itinerary;

    /** 用户备注，可空 */
    @Size(max = 500, message = "备注不能超过 500 字")
    private String note;
}
