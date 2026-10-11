package org.example.traveljavserver.controller;

import jakarta.validation.Valid;
import org.example.dto.ChatRequestDTO;
import org.example.dto.TravelRequestDTO;
import org.example.traveljavserver.service.TravelService;
import org.example.traveljavserver.vo.ItineraryVO;
import org.example.traveljavserver.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/travel")
public class TravelController {
    private final TravelService travelService;

    @Autowired
    public TravelController(TravelService travelService) {
        this.travelService = travelService;
    }

    /** 健康检查接口（不依赖大模型，始终可用） */
    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.ok("你好，旅游服务运行正常");
    }

    /**
     * 行程推荐（结构化）。
     * 始终返回 HTTP 200：大模型正常时 data.degraded=false；
     * 未配置 Key / 调用失败 / 解析失败时 data.degraded=true（降级模板），原因见 data.degradeReason。
     */
    @PostMapping("/recommend")
    public Result<ItineraryVO> recommend(@Valid @RequestBody TravelRequestDTO travelRequestDTO) {
        ItineraryVO itinerary = travelService.recommend(travelRequestDTO);
        return Result.ok(itinerary);
    }

    /** 流式对话（SSE，大模型不可用时推送中文错误事件） */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody ChatRequestDTO chatRequestDTO) {
        return travelService.chatStream(chatRequestDTO.getMessage());
    }
}
