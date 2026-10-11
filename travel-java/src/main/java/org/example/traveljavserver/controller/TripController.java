package org.example.traveljavserver.controller;

import jakarta.validation.Valid;
import org.example.dto.TripSaveRequest;
import org.example.traveljavserver.service.TripService;
import org.example.traveljavserver.vo.Result;
import org.example.traveljavserver.vo.TripPageVO;
import org.example.traveljavserver.vo.TripVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 行程持久化接口。
 * 通过 X-Device-Id 头做匿名设备隔离；未携带时归入 anonymous。
 */
@RestController
@RequestMapping("/api/travel/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    /** 分页查询（可选按城市筛选）：GET /api/travel/trips?page=0&size=10&city=北京 */
    @GetMapping
    public Result<TripPageVO> list(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String city) {
        return Result.ok(tripService.list(device(deviceId), city, page, size));
    }

    /** 行程详情（含完整行程 JSON） */
    @GetMapping("/{id}")
    public Result<TripVO> get(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @PathVariable Long id) {
        TripVO vo = tripService.get(device(deviceId), id);
        return vo != null ? Result.ok(vo) : Result.fail(404, "行程不存在或无权访问", "行程不存在或无权访问");
    }

    /** 保存行程（内容完全相同时不重复保存，返回已存在记录且 duplicated=true） */
    @PostMapping
    public Result<TripVO> create(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @Valid @RequestBody TripSaveRequest request) {
        return Result.ok(tripService.create(device(deviceId), request));
    }

    /** 编辑写回：更新备注；如传 itinerary 则整体替换行程内容 */
    @PutMapping("/{id}")
    public Result<TripVO> update(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @PathVariable Long id,
            @Valid @RequestBody TripSaveRequest request) {
        TripVO vo = tripService.update(device(deviceId), id, request);
        return vo != null ? Result.ok(vo) : Result.fail(404, "行程不存在或无权访问", "行程不存在或无权访问");
    }

    /** 删除行程 */
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @PathVariable Long id) {
        boolean removed = tripService.delete(device(deviceId), id);
        return removed ? Result.ok(true) : Result.fail(404, "行程不存在或无权访问", "行程不存在或无权访问");
    }

    private String device(String deviceId) {
        return (deviceId != null && !deviceId.isBlank()) ? deviceId.trim() : TripService.DEFAULT_DEVICE_ID;
    }
}
