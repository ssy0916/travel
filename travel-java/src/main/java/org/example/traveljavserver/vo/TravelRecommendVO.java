package org.example.traveljavserver.vo;

import lombok.Data;

import java.util.List;

/**
 * 旅游行程推荐响应 VO
 */
@Data
public class TravelRecommendVO {
    private Boolean success;
    private String city;
    private Integer days;
    private Integer totalBudget;
    private String content;
    private List<DailyItinerary> dailyItinerary;
    private BudgetBreakdown budgetBreakdown;
    private List<String> tips;
    private List<String> warnings;

    /**
     * 单日行程
     */
    @Data
    public static class DailyItinerary {
        private Integer day;
        private String date;
        private TimeSlot morning;
        private TimeSlot afternoon;
        private TimeSlot evening;
    }

    /**
     * 时间段安排（上午/下午/晚上）
     */
    @Data
    public static class TimeSlot {
        private String spot;
        private String duration;
        private String ticket;
        private String transportation;
        private String description;
    }

    /**
     * 预算明细
     */
    @Data
    public static class BudgetBreakdown {
        private Integer accommodation;
        private Integer food;
        private Integer transportation;
        private Integer tickets;
        private Integer other;
    }
}
