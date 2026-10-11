package org.example.traveljavserver.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 旅游行程推荐响应 VO（v1.4）。
 * 正常行程与降级行程共用同一结构；通过 degraded / sourceFlags 标识各数据节点来源。
 */
@Data
@NoArgsConstructor
public class ItineraryVO {

    private Boolean success;
    private String error;

    private String city;
    /** 行程天数（对齐前端字段名 day） */
    private Integer day;
    /** 预算总额（元） */
    private Integer budget;

    private String summary;
    private List<DayPlan> days;
    private BudgetBreakdown budgetBreakdown;
    private List<String> tips;

    /** 是否为降级行程（规则模板） */
    private Boolean degraded;
    private String degradeReason;

    /** 高德地理编码返回的城市中心坐标 */
    private Double centerLng;
    private Double centerLat;

    /** 高德天气预报；未配置或请求失败时为空数组 */
    private List<WeatherInfo> weather;
    /** 高德住宿 POI 搜索结果，不代表价格、空房或质量保证 */
    private List<HotelRecommendation> hotelRecommendations;

    /** 各数据节点来源：real / fallback（前端 mock 时可能出现 mock） */
    private SourceFlags sourceFlags;

    /** 单日行程 */
    @Data
    @NoArgsConstructor
    public static class DayPlan {
        private Integer day;
        private String title;
        private List<Slot> slots;
    }

    /** 时段安排（上午/下午/晚上） */
    @Data
    @NoArgsConstructor
    public static class Slot {
        private String period;
        private List<Attraction> attractions;
    }

    /** 景点 */
    @Data
    @NoArgsConstructor
    public static class Attraction {
        private String name;
        private String duration;
        private String cost;
        /** 交通建议（非高德路线规划结果） */
        private String transport;
        private String description;

        /** 仅在城市 adcode 下唯一匹配到高德 POI 时返回，否则为 null */
        private Double lng;
        private Double lat;
        /** 门票 / 开放时间 / 图片 / 地址：仅高德实际返回时填充 */
        private String ticket;
        private String openTime;
        private String photo;
        private String address;
        /** verified / unverified */
        private String poiVerification;
    }

    /** 预算明细；降级模板各分项可为 null，total 回填请求预算 */
    @Data
    @NoArgsConstructor
    public static class BudgetBreakdown {
        private Integer accommodation;
        private Integer dining;
        private Integer transport;
        private Integer tickets;
        private Integer other;
        private Integer total;
    }

    /** 高德天气预报单项 */
    @Data
    @NoArgsConstructor
    public static class WeatherInfo {
        private String date;
        private String dayWeather;
        private String nightWeather;
        private String dayTemperature;
        private String nightTemperature;
    }

    /** 高德住宿 POI 推荐项 */
    @Data
    @NoArgsConstructor
    public static class HotelRecommendation {
        private String name;
        private String address;
        private String photo;
        private String rating;
        /** 高德标注价格（仅供参考） */
        private String price;
    }

    /** 数据节点来源标记 */
    @Data
    @NoArgsConstructor
    public static class SourceFlags {
        private String llm;
        private String geocode;
        private String weather;
        private String poi;
        private String hotel;
        private String knowledge;

        /** 所有节点均为真实数据（knowledge 由调用方按是否命中知识库再覆盖） */
        public static SourceFlags real() {
            SourceFlags f = new SourceFlags();
            f.llm = "real";
            f.geocode = "real";
            f.weather = "real";
            f.poi = "real";
            f.hotel = "real";
            f.knowledge = "real";
            return f;
        }

        /** 所有节点均为降级 */
        public static SourceFlags fallback() {
            SourceFlags f = new SourceFlags();
            f.llm = "fallback";
            f.geocode = "fallback";
            f.weather = "fallback";
            f.poi = "fallback";
            f.hotel = "fallback";
            f.knowledge = "fallback";
            return f;
        }
    }
}
