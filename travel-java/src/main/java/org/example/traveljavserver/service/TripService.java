package org.example.traveljavserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.TripSaveRequest;
import org.example.traveljavserver.entity.Trip;
import org.example.traveljavserver.repository.TripRepository;
import org.example.traveljavserver.vo.TripPageVO;
import org.example.traveljavserver.vo.TripVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 行程持久化服务：分页查询、城市筛选、查看、保存（去重）、编辑写回、删除。
 * 所有操作按 deviceId 隔离（匿名设备标识，非真实账号体系）。
 */
@Service
public class TripService {

    private static final Logger log = LoggerFactory.getLogger(TripService.class);
    /** 未携带 X-Device-Id 头时的兜底标识 */
    public static final String DEFAULT_DEVICE_ID = "anonymous";

    private final TripRepository tripRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TripService(TripRepository tripRepository) {
        this.tripRepository = tripRepository;
    }

    /** 分页查询（可选按城市筛选），按创建时间倒序；列表不返回 payload */
    public TripPageVO list(String deviceId, String city, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Trip> result = (city != null && !city.isBlank())
                ? tripRepository.findByDeviceIdAndCity(deviceId, city, pageable)
                : tripRepository.findByDeviceId(deviceId, pageable);

        TripPageVO vo = new TripPageVO();
        List<TripVO> items = result.getContent().stream().map(t -> toVO(t, false, false)).toList();
        vo.setList(items);
        vo.setTotal(result.getTotalElements());
        vo.setPage(page);
        vo.setSize(size);
        vo.setHasMore(result.hasNext());
        return vo;
    }

    /** 查看详情（含完整行程 JSON） */
    public TripVO get(String deviceId, Long id) {
        return tripRepository.findByIdAndDeviceId(id, deviceId)
                .map(t -> toVO(t, true, false))
                .orElse(null);
    }

    /**
     * 保存行程。内容完全相同的行程（按 payload SHA-256）在同一设备下不重复保存，
     * 返回已存在记录且 duplicated=true。
     */
    @Transactional
    public TripVO create(String deviceId, TripSaveRequest request) {
        Map<String, Object> itinerary = request.getItinerary();
        if (itinerary == null || itinerary.isEmpty()) {
            throw new IllegalArgumentException("行程内容不能为空");
        }
        String payload = toJson(itinerary);
        String hash = sha256(payload);

        var existing = tripRepository.findByDeviceIdAndPayloadHash(deviceId, hash);
        if (existing.isPresent()) {
            return toVO(existing.get(), false, true);
        }

        Trip trip = new Trip();
        trip.setDeviceId(deviceId);
        trip.setCity(textOr(itinerary.get("city"), "未知城市"));
        trip.setDay(intOr(itinerary.get("day")));
        trip.setBudget(intOr(itinerary.get("budget")));
        trip.setSummary(textOr(itinerary.get("summary"), null));
        trip.setDegraded(Boolean.TRUE.equals(itinerary.get("degraded")));
        trip.setNote(request.getNote());
        trip.setPayloadHash(hash);
        trip.setPayload(payload);
        trip.setCreatedAt(Instant.now());
        trip.setUpdatedAt(Instant.now());
        return toVO(tripRepository.save(trip), false, false);
    }

    /**
     * 编辑写回：支持更新备注；如同时传 itinerary 则整体替换行程内容并同步元字段。
     * 找不到记录返回 null。
     */
    @Transactional
    public TripVO update(String deviceId, Long id, TripSaveRequest request) {
        var optional = tripRepository.findByIdAndDeviceId(id, deviceId);
        if (optional.isEmpty()) {
            return null;
        }
        Trip trip = optional.get();
        if (request.getNote() != null) {
            trip.setNote(request.getNote());
        }
        Map<String, Object> itinerary = request.getItinerary();
        if (itinerary != null && !itinerary.isEmpty()) {
            String payload = toJson(itinerary);
            trip.setPayload(payload);
            trip.setPayloadHash(sha256(payload));
            trip.setCity(textOr(itinerary.get("city"), trip.getCity()));
            Integer day = intOr(itinerary.get("day"));
            if (day != null) trip.setDay(day);
            Integer budget = intOr(itinerary.get("budget"));
            if (budget != null) trip.setBudget(budget);
            trip.setSummary(textOr(itinerary.get("summary"), trip.getSummary()));
            trip.setDegraded(Boolean.TRUE.equals(itinerary.get("degraded")));
        }
        trip.setUpdatedAt(Instant.now());
        return toVO(tripRepository.save(trip), false, false);
    }

    /** 删除（仅限本设备记录），返回是否删除成功 */
    @Transactional
    public boolean delete(String deviceId, Long id) {
        var optional = tripRepository.findByIdAndDeviceId(id, deviceId);
        if (optional.isEmpty()) {
            return false;
        }
        tripRepository.delete(optional.get());
        return true;
    }

    private TripVO toVO(Trip trip, boolean withData, boolean duplicated) {
        TripVO vo = new TripVO();
        vo.setId(trip.getId());
        vo.setCity(trip.getCity());
        vo.setDay(trip.getDay());
        vo.setBudget(trip.getBudget());
        vo.setSummary(trip.getSummary());
        vo.setDegraded(trip.getDegraded());
        vo.setNote(trip.getNote());
        vo.setDuplicated(duplicated);
        vo.setCreatedAt(trip.getCreatedAt().toEpochMilli());
        vo.setUpdatedAt(trip.getUpdatedAt().toEpochMilli());
        if (withData) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = objectMapper.readValue(trip.getPayload(), Map.class);
                vo.setData(data);
            } catch (Exception e) {
                log.warn("行程 payload 反序列化失败 id={}: {}", trip.getId(), e.getMessage());
            }
        }
        return vo;
    }

    private String toJson(Map<String, Object> itinerary) {
        try {
            return objectMapper.writeValueAsString(itinerary);
        } catch (Exception e) {
            throw new IllegalArgumentException("行程内容无法序列化", e);
        }
    }

    private static String textOr(Object value, String fallback) {
        return (value instanceof String s && !s.isBlank()) ? s : fallback;
    }

    private static Integer intOr(Object value) {
        if (value instanceof Number n) return n.intValue();
        return null;
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
