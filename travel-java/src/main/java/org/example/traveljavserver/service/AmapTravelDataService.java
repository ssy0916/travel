package org.example.traveljavserver.service;

import org.example.traveljavserver.vo.ItineraryVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 高德数据装配服务：
 * load()   —— 一次性拉取城市地理编码、景点 POI、天气、住宿 POI（各节点独立降级，互不影响）
 * enrich() —— 把真实数据回填到 ItineraryVO，并按城市 adcode 唯一匹配景点名补坐标/POI 信息
 * promptContext() —— 生成注入大模型 prompt 的候选 POI / 天气文本
 */
@Service
public class AmapTravelDataService {

    private static final Logger log = LoggerFactory.getLogger(AmapTravelDataService.class);

    /** 景点候选最多取多少条用于 prompt 注入与详情补全 */
    private static final int ATTRACTION_LIMIT = 20;
    /** 逐条调 POI 详情补全门票/图片的上限（控制 QPS 与耗时） */
    private static final int DETAIL_LIMIT = 8;
    /** 注入 prompt 的候选景点上限 */
    private static final int PROMPT_POI_LIMIT = 15;
    /** 住宿 POI 数量 */
    private static final int HOTEL_LIMIT = 10;

    private final AmapClient amapClient;

    public AmapTravelDataService(AmapClient amapClient) {
        this.amapClient = amapClient;
    }

    /**
     * 一次 recommend 的高德数据快照。
     * candidates 为转换后的景点候选（真实 POI），供降级模板直接排期与 enrich 唯一匹配。
     */
    public record TravelData(
            AmapClient.CityLocation location,
            List<ItineraryVO.Attraction> candidates,
            List<AmapClient.WeatherInfo> weather,
            List<AmapClient.Poi> hotels) {

        /** 候选景点；没有可用 POI 时返回 empty，调用方据此走纯占位降级 */
        public Optional<List<ItineraryVO.Attraction>> attractions() {
            return (candidates == null || candidates.isEmpty())
                    ? Optional.empty()
                    : Optional.of(candidates);
        }
    }

    /** 加载城市相关高德数据；任何节点失败都被隔离，不影响其他节点 */
    public TravelData load(String city) {
        AmapClient.CityLocation location = safeCall(
                () -> amapClient.geocode(city).orElse(null), null, "地理编码");
        // 有 adcode 用 adcode 限定，否则退回城市名
        String cityParam = location != null && !location.adcode().isBlank()
                ? location.adcode() : city;

        List<AmapClient.Poi> pois = safeCall(
                () -> amapClient.searchPois(null, "110000", cityParam, ATTRACTION_LIMIT),
                List.<AmapClient.Poi>of(), "景点POI");
        List<ItineraryVO.Attraction> candidates = toCandidates(pois, cityParam);

        List<AmapClient.WeatherInfo> weather = location != null
                ? safeCall(() -> amapClient.weatherForecast(location.adcode()),
                        List.<AmapClient.WeatherInfo>of(), "天气")
                : List.of();

        List<AmapClient.Poi> hotels = safeCall(
                () -> amapClient.searchPois("酒店", "080000", cityParam, HOTEL_LIMIT),
                List.<AmapClient.Poi>of(), "住宿POI");

        return new TravelData(location, candidates, weather, hotels);
    }

    /**
     * 把高德数据回填进行程：
     * - 城市中心坐标、天气、住宿 POI
     * - 按景点名在城市 POI 内唯一匹配，唯一命中才覆盖坐标/地址/门票/开放时间/图片并置 verified
     * - 模型输出中的坐标/图片/门票/开放时间一律不接受，避免编造真实 POI 信息
     * - sourceFlags 的 geocode/weather/poi/hotel 按实际结果覆盖；llm/knowledge 不动
     */
    public void enrich(ItineraryVO vo, TravelData data) {
        if (vo == null) {
            return;
        }
        ItineraryVO.SourceFlags flags = vo.getSourceFlags();
        if (flags == null) {
            flags = ItineraryVO.SourceFlags.fallback();
        }

        if (data == null) {
            vo.setCenterLng(null);
            vo.setCenterLat(null);
            vo.setWeather(List.of());
            vo.setHotelRecommendations(List.of());
            flags.setGeocode("fallback");
            flags.setWeather("fallback");
            flags.setPoi("fallback");
            flags.setHotel("fallback");
            vo.setSourceFlags(flags);
            return;
        }

        // 地理编码 → 城市中心坐标
        if (data.location() != null) {
            vo.setCenterLng(data.location().lng());
            vo.setCenterLat(data.location().lat());
            flags.setGeocode("real");
        } else {
            vo.setCenterLng(null);
            vo.setCenterLat(null);
            flags.setGeocode("fallback");
        }

        // 天气
        if (data.weather() != null && !data.weather().isEmpty()) {
            vo.setWeather(data.weather().stream().map(AmapTravelDataService::toWeatherVO).toList());
            flags.setWeather("real");
        } else {
            vo.setWeather(List.of());
            flags.setWeather("fallback");
        }

        // 住宿 POI
        if (data.hotels() != null && !data.hotels().isEmpty()) {
            vo.setHotelRecommendations(data.hotels().stream()
                    .map(AmapTravelDataService::toHotelVO).toList());
            flags.setHotel("real");
        } else {
            vo.setHotelRecommendations(List.of());
            flags.setHotel("fallback");
        }

        // 景点 POI 唯一匹配
        boolean hasCandidates = data.candidates() != null && !data.candidates().isEmpty();
        flags.setPoi(hasCandidates ? "real" : "fallback");
        if (vo.getDays() != null) {
            for (ItineraryVO.DayPlan day : vo.getDays()) {
                if (day == null || day.getSlots() == null) {
                    continue;
                }
                for (ItineraryVO.Slot slot : day.getSlots()) {
                    if (slot == null || slot.getAttractions() == null) {
                        continue;
                    }
                    for (ItineraryVO.Attraction attraction : slot.getAttractions()) {
                        verifyAttraction(attraction, hasCandidates ? data.candidates() : List.of());
                    }
                }
            }
        }
        vo.setSourceFlags(flags);
    }

    /** 生成注入 prompt 的高德参考文本；全部为空时返回空串 */
    public String promptContext(TravelData data) {
        if (data == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (data.candidates() != null && !data.candidates().isEmpty()) {
            sb.append("\n【高德景点候选 POI】（优先选用以下真实名称，不要编造候选之外的门票/坐标）\n");
            int n = Math.min(data.candidates().size(), PROMPT_POI_LIMIT);
            for (int i = 0; i < n; i++) {
                ItineraryVO.Attraction a = data.candidates().get(i);
                sb.append(i + 1).append(". ").append(a.getName());
                if (a.getAddress() != null) {
                    sb.append("（地址：").append(a.getAddress()).append('）');
                }
                sb.append('\n');
            }
        }
        if (data.weather() != null && !data.weather().isEmpty()) {
            sb.append("\n【高德未来天气预报】（仅供穿衣/雨具参考，不要杜撰预报之外的天气）\n");
            for (AmapClient.WeatherInfo w : data.weather()) {
                sb.append(w.date()).append("：").append(w.dayWeather()).append('/')
                        .append(w.nightWeather()).append("，")
                        .append(w.dayTemperature()).append("℃/").append(w.nightTemperature()).append("℃\n");
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------------
    // 内部方法
    // ---------------------------------------------------------------------

    /** 文本搜索 POI → 行程景点候选；前若干条补一次详情拿门票/开放时间/图片 */
    private List<ItineraryVO.Attraction> toCandidates(List<AmapClient.Poi> pois, String cityParam) {
        if (pois == null || pois.isEmpty()) {
            return List.of();
        }
        List<ItineraryVO.Attraction> result = new ArrayList<>();
        int detailRemaining = DETAIL_LIMIT;
        for (AmapClient.Poi poi : pois) {
            AmapClient.Poi enriched = poi;
            if (detailRemaining > 0 && poi.id() != null && !poi.id().isBlank()) {
                enriched = safeCall(() -> amapClient.poiDetail(poi.id()).orElse(poi), poi, "POI详情");
                detailRemaining--;
            }
            result.add(toAttraction(enriched));
        }
        return result;
    }

    private static ItineraryVO.Attraction toAttraction(AmapClient.Poi poi) {
        ItineraryVO.Attraction a = new ItineraryVO.Attraction();
        a.setName(poi.name());
        a.setAddress(poi.address());
        a.setLng(poi.lng());
        a.setLat(poi.lat());
        a.setTicket(poi.ticket());
        a.setOpenTime(poi.openTime());
        a.setPhoto(poi.photo());
        a.setPoiVerification("verified");
        a.setDescription("高德地图检索到的真实 POI，具体门票、开放时间与交通请以景区官方信息为准。");
        return a;
    }

    private static ItineraryVO.WeatherInfo toWeatherVO(AmapClient.WeatherInfo w) {
        ItineraryVO.WeatherInfo vo = new ItineraryVO.WeatherInfo();
        vo.setDate(w.date());
        vo.setDayWeather(w.dayWeather());
        vo.setNightWeather(w.nightWeather());
        vo.setDayTemperature(w.dayTemperature());
        vo.setNightTemperature(w.nightTemperature());
        return vo;
    }

    private static ItineraryVO.HotelRecommendation toHotelVO(AmapClient.Poi poi) {
        ItineraryVO.HotelRecommendation h = new ItineraryVO.HotelRecommendation();
        h.setName(poi.name());
        h.setAddress(poi.address());
        h.setPhoto(poi.photo());
        h.setRating(poi.rating());
        h.setPrice(poi.price());
        return h;
    }

    /**
     * 按景点名在候选 POI 中做唯一精确匹配：
     * - 已是降级模板排好的真实候选（verified 且有坐标）保持不动
     * - 模型产出的景点先清空高德专属字段；同名候选恰好 1 个才补真实数据并置 verified
     * - 无匹配或同名歧义置 unverified，不补坐标
     */
    private void verifyAttraction(ItineraryVO.Attraction attraction,
                                  List<ItineraryVO.Attraction> candidates) {
        if (attraction == null) {
            return;
        }
        if ("verified".equals(attraction.getPoiVerification())
                && attraction.getLng() != null && attraction.getLat() != null) {
            return;
        }
        // 模型输出的坐标/图片/门票/开放时间一律不接受
        attraction.setLng(null);
        attraction.setLat(null);
        attraction.setTicket(null);
        attraction.setOpenTime(null);
        attraction.setPhoto(null);
        attraction.setAddress(null);

        String name = attraction.getName();
        if (name == null || name.isBlank() || candidates.isEmpty()) {
            attraction.setPoiVerification("unverified");
            return;
        }
        List<ItineraryVO.Attraction> matched = candidates.stream()
                .filter(c -> name.trim().equals(c.getName() == null ? "" : c.getName().trim()))
                .toList();
        if (matched.size() == 1) {
            ItineraryVO.Attraction p = matched.get(0);
            attraction.setLng(p.getLng());
            attraction.setLat(p.getLat());
            attraction.setAddress(p.getAddress());
            attraction.setTicket(p.getTicket());
            attraction.setOpenTime(p.getOpenTime());
            attraction.setPhoto(p.getPhoto());
            attraction.setPoiVerification("verified");
        } else {
            // 0 个或同名多个（歧义）都不补
            attraction.setPoiVerification("unverified");
        }
    }

    private static <T> T safeCall(Supplier<T> supplier, T fallback, String node) {
        try {
            T value = supplier.get();
            return value != null ? value : fallback;
        } catch (Exception e) {
            log.warn("高德[{}]节点调用异常，按降级处理：{}", node, e.getMessage());
            return fallback;
        }
    }
}
