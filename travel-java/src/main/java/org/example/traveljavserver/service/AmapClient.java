package org.example.traveljavserver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 高德开放平台 Web 服务客户端：地理编码、天气预报、POI 文本搜索、POI 详情。
 * 未配置 API Key 或任一请求失败时安全返回空结果（Optional.empty / 空 List），由上层做节点级降级。
 */
@Component
public class AmapClient {

    private static final Logger log = LoggerFactory.getLogger(AmapClient.class);

    private final String apiKey;
    private final String baseUrl;
    private final OkHttpClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AmapClient(@Value("${server.amap.api-key:}") String apiKey,
                      @Value("${server.amap.base-url:https://restapi.amap.com}") String baseUrl) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://restapi.amap.com" : baseUrl.trim();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    /** 地理编码结果 */
    public record CityLocation(String city, String adcode, Double lng, Double lat) {
    }

    /** POI 搜索/详情结果；详情接口未覆盖的字段为 null */
    public record Poi(String id, String name, String type, String address, Double lng, Double lat,
                      String ticket, String openTime, String photo, String rating, String price) {
    }

    /** 天气预报单项 */
    public record WeatherInfo(String date, String dayWeather, String nightWeather,
                              String dayTemperature, String nightTemperature) {
    }

    /** GET 查询参数配置器（函数式） */
    @FunctionalInterface
    public interface QueryConfigurer {
        void configure(HttpUrl.Builder urlBuilder);
    }

    /**
     * 地理编码：城市名 → adcode + 中心坐标。
     * GET /v3/geocode/geo?address=北京
     */
    public Optional<CityLocation> geocode(String city) {
        if (!isConfigured() || city == null || city.isBlank()) {
            return Optional.empty();
        }
        JsonNode root = get("/v3/geocode/geo", b -> b.addQueryParameter("address", city.trim()));
        if (!isOk(root)) {
            return Optional.empty();
        }
        JsonNode geocodes = root.path("geocodes");
        if (!geocodes.isArray() || geocodes.isEmpty()) {
            return Optional.empty();
        }
        JsonNode g = geocodes.get(0);
        double[] ll = parseLocation(g.path("location").asText(""));
        if (ll == null) {
            return Optional.empty();
        }
        String formatted = g.path("formatted_address").asText("");
        return Optional.of(new CityLocation(
                (formatted == null || formatted.isBlank()) ? city : formatted,
                g.path("adcode").asText(""),
                ll[0], ll[1]));
    }

    /**
     * 未来多日天气预报（extensions=all，通常含当天起 4 天）。
     * GET /v3/weather/weatherInfo?city=adcode&extensions=all
     */
    public List<WeatherInfo> weatherForecast(String adcode) {
        if (!isConfigured() || adcode == null || adcode.isBlank()) {
            return List.of();
        }
        JsonNode root = get("/v3/weather/weatherInfo", b -> {
            b.addQueryParameter("city", adcode.trim());
            b.addQueryParameter("extensions", "all");
        });
        if (!isOk(root)) {
            return List.of();
        }
        JsonNode forecasts = root.path("forecasts");
        if (!forecasts.isArray() || forecasts.isEmpty()) {
            return List.of();
        }
        JsonNode casts = forecasts.get(0).path("casts");
        if (!casts.isArray()) {
            return List.of();
        }
        List<WeatherInfo> result = new ArrayList<>();
        for (JsonNode c : casts) {
            String date = c.path("date").asText("");
            if (date.isBlank()) {
                continue;
            }
            result.add(new WeatherInfo(
                    date,
                    c.path("dayweather").asText(""),
                    c.path("nightweather").asText(""),
                    c.path("daytemp").asText(""),
                    c.path("nighttemp").asText("")));
        }
        return result;
    }

    /**
     * POI 文本搜索。
     * GET /v3/place/text?keywords=&types=&city=adcode&citylimit=true&offset=N
     *
     * @param keywords 关键词，可为 null（仅按类型搜）
     * @param types    POI 类型码，如 110000 风景名胜、080000 住宿服务
     * @param city     城市 adcode（优先）或城市名
     */
    public List<Poi> searchPois(String keywords, String types, String city, int offset) {
        if (!isConfigured() || (keywords == null || keywords.isBlank()) && (types == null || types.isBlank())) {
            return List.of();
        }
        JsonNode root = get("/v3/place/text", b -> {
            if (keywords != null && !keywords.isBlank()) {
                b.addQueryParameter("keywords", keywords.trim());
            }
            if (types != null && !types.isBlank()) {
                b.addQueryParameter("types", types.trim());
            }
            if (city != null && !city.isBlank()) {
                b.addQueryParameter("city", city.trim());
                b.addQueryParameter("citylimit", "true");
            }
            b.addQueryParameter("offset", String.valueOf(Math.max(1, Math.min(offset, 50))));
            b.addQueryParameter("page", "1");
            b.addQueryParameter("extensions", "base");
        });
        if (!isOk(root)) {
            return List.of();
        }
        JsonNode pois = root.path("pois");
        if (!pois.isArray()) {
            return List.of();
        }
        List<Poi> result = new ArrayList<>();
        for (JsonNode p : pois) {
            double[] ll = parseLocation(p.path("location").asText(""));
            String name = p.path("name").asText("");
            if (name.isBlank() || "[]".equals(name) || ll == null) {
                continue;
            }
            // 部分类型的文本搜索会在 bizext 中返回评分/价格
            JsonNode bizext = p.path("bizext");
            result.add(new Poi(
                    p.path("id").asText(""),
                    name,
                    p.path("type").asText(""),
                    nullIfArray(p.path("address")),
                    ll[0], ll[1],
                    textOrNull(bizext.path("fee")),
                    null,
                    firstPhoto(p),
                    textOrNull(bizext.path("rating")),
                    textOrNull(bizext.path("cost"))));
        }
        return result;
    }

    /**
     * POI 详情：补充门票、开放时间、图片等。
     * GET /v3/place/detail?id=xxx
     */
    public Optional<Poi> poiDetail(String id) {
        if (!isConfigured() || id == null || id.isBlank()) {
            return Optional.empty();
        }
        JsonNode root = get("/v3/place/detail", b -> b.addQueryParameter("id", id.trim()));
        if (!isOk(root)) {
            return Optional.empty();
        }
        JsonNode pois = root.path("pois");
        if (!pois.isArray() || pois.isEmpty()) {
            return Optional.empty();
        }
        JsonNode p = pois.get(0);
        double[] ll = parseLocation(p.path("location").asText(""));
        if (ll == null) {
            return Optional.empty();
        }
        JsonNode business = p.path("business");
        return Optional.of(new Poi(
                p.path("id").asText(id),
                p.path("name").asText(""),
                p.path("type").asText(""),
                nullIfArray(p.path("address")),
                ll[0], ll[1],
                firstText(business, "fee", "ticket"),
                firstText(business, "open_time", "opentime"),
                firstPhoto(p),
                firstText(business, "rating"),
                firstText(business, "cost", "price")));
    }

    // ---------------------------------------------------------------------
    // 内部工具
    // ---------------------------------------------------------------------

    /** 发起 GET 请求并解析 JSON；任何失败返回 null（节点级降级由 isOk/调用方处理） */
    private JsonNode get(String path, QueryConfigurer configurer) {
        try {
            HttpUrl parsed = HttpUrl.parse(baseUrl + path);
            if (parsed == null) {
                log.warn("高德接口地址非法：{}{}", baseUrl, path);
                return null;
            }
            HttpUrl.Builder builder = parsed.newBuilder()
                    .addQueryParameter("key", apiKey)
                    .addQueryParameter("output", "JSON");
            configurer.configure(builder);

            Request request = new Request.Builder().url(builder.build()).get().build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("高德接口调用失败 path={}, code={}", path, response.code());
                    return null;
                }
                JsonNode root = objectMapper.readTree(response.body().string());
                if (!isOk(root)) {
                    log.warn("高德接口返回业务失败 path={}, info={}, infocode={}",
                            path, root.path("info").asText(""), root.path("infocode").asText(""));
                }
                return root;
            }
        } catch (Exception e) {
            log.warn("高德接口调用异常 path={}: {}", path, e.getMessage());
            return null;
        }
    }

    /** 高德 status=1 表示成功 */
    private boolean isOk(JsonNode root) {
        return root != null && "1".equals(root.path("status").asText(""));
    }

    /** 解析 "lng,lat" 格式坐标 */
    private static double[] parseLocation(String location) {
        if (location == null || location.isBlank() || "[]".equals(location)) {
            return null;
        }
        String[] parts = location.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            double lng = Double.parseDouble(parts[0].trim());
            double lat = Double.parseDouble(parts[1].trim());
            if (lng == 0.0 && lat == 0.0) {
                return null;
            }
            return new double[]{lng, lat};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String firstPhoto(JsonNode poi) {
        JsonNode photos = poi.path("photos");
        if (photos.isArray() && !photos.isEmpty()) {
            String url = photos.get(0).path("url").asText("");
            return url.isBlank() ? null : url;
        }
        return null;
    }

    private static String firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            String value = textOrNull(node.path(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || node.isArray() || node.isObject()) {
            return null;
        }
        String text = node.asText("");
        return (text.isBlank() || "[]".equals(text)) ? null : text;
    }

    /** 高德缺字段时会返回空数组 []，统一按 null 处理 */
    private static String nullIfArray(JsonNode node) {
        return node != null && node.isArray() ? null : textOrNull(node);
    }
}
