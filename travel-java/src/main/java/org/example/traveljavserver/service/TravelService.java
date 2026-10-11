package org.example.traveljavserver.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.example.dto.TravelRequestDTO;
import org.example.traveljavserver.utils.LLMutils;
import org.example.traveljavserver.vo.ItineraryVO;
import org.example.traveljavserver.vo.StreamChunkVO;
import org.example.traveljavserver.vo.StreamDoneVO;
import org.example.traveljavserver.vo.StreamErrorVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Service
public class TravelService {

    private static final Logger log = LoggerFactory.getLogger(TravelService.class);

    /** 自由对话（/chat）的系统提示词（中文人设） */
    private static final String SYSTEM_PROMPT = """
            你是一位专业、热情的中文旅游规划助手。
            请始终使用中文回答，根据用户的问题给出实用、具体的出行建议。
            """;

    /** 结构化行程生成的系统提示词：强制只输出约定结构的 JSON */
    private static final String ITINERARY_SYSTEM_PROMPT = """
            你是一位专业、严谨的中文旅游规划助手。请根据用户给出的城市、天数和预算，规划合理、具体、可执行的旅行行程。

            你必须只输出一个 JSON 对象，不要输出 Markdown 代码块、不要输出任何解释或多余文字。JSON 结构如下：
            {
              "summary": "字符串，例如：北京 · 2天行程",
              "days": [
                {
                  "day": 1,
                  "title": "第1天",
                  "slots": [
                    {
                      "period": "上午",
                      "attractions": [
                        {
                          "name": "景点名称",
                          "duration": "建议游玩时长，如 3-4小时",
                          "cost": "门票或费用说明",
                          "transport": "具体交通方式与地铁/公交线路",
                          "description": "景点介绍与游玩建议"
                        }
                      ]
                    }
                  ]
                }
              ],
              "budgetBreakdown": {
                "accommodation": 0,
                "dining": 0,
                "transport": 0,
                "tickets": 0,
                "other": 0,
                "total": 0
              },
              "tips": ["实用提示1", "实用提示2"]
            }
            要求：
            1. 每天必须包含 上午、下午、晚上 三个时段，每个时段安排 1-2 个景点。
            2. budgetBreakdown 中各项为整数金额（元），各项之和必须等于 total，且 total 不超过用户总预算。
            3. 高德提供景点候选时优先选用候选名称；不要编造门票、开放时间、天气或实时交通。
               未提供路线规划数据时，交通只写一般出行建议，不得声称是已核实的线路/站点。
               门票、开放时间等信息不确定时请明确提示以景区官方信息为准。
            4. 始终使用中文。
            """;

    @Value("${server.llm.api-key}")
    private String apiKey;
    @Value("${server.llm.base-url}")
    private String baseUrl;
    @Value("${server.llm.model}")
    private String model;

    /** 模型可能返回多余字段，反序列化时忽略未知属性，避免直接失败 */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private LLMutils llmUtils;
    private final ItineraryFallbackBuilder fallbackBuilder;
    private final AmapTravelDataService amapTravelDataService;
    private final CityKnowledgeBase cityKnowledgeBase;

    public TravelService(ItineraryFallbackBuilder fallbackBuilder, AmapTravelDataService amapTravelDataService,
                         CityKnowledgeBase cityKnowledgeBase) {
        this.fallbackBuilder = fallbackBuilder;
        this.amapTravelDataService = amapTravelDataService;
        this.cityKnowledgeBase = cityKnowledgeBase;
    }

    @PostConstruct
    public void init() {
        // @Value 注入在构造之后才完成，所以这里初始化 LLMutils
        this.llmUtils = new LLMutils(apiKey, baseUrl, model);
    }

    /**
     * 行程推荐（结构化）。
     * 未配置 Key、调用失败或连续两次 JSON 解析失败时，返回结构完整的降级模板，
     * 始终 HTTP 200，通过 degraded/sourceFlags 标识数据来源。
     */
    public ItineraryVO recommend(TravelRequestDTO dto) {
        AmapTravelDataService.TravelData travelData = amapTravelDataService.load(dto.getCity());
        CityKnowledgeBase.Knowledge knowledge = cityKnowledgeBase.load(dto.getCity());
        if (!llmUtils.isConfigured()) {
            ItineraryVO fallback = fallbackBuilder.build(dto,
                    "未配置大模型 API Key（环境变量 LLM_API_KEY），当前为降级行程模板",
                    travelData.attractions().orElse(List.of()));
            amapTravelDataService.enrich(fallback, travelData);
            return fallback;
        }

        // 第一次：JSON 模式生成
        ItineraryVO itinerary = parseItinerary(
                llmUtils.chat(ITINERARY_SYSTEM_PROMPT,
                        buildItineraryUserPrompt(dto, false, travelData, knowledge), true));

        if (itinerary == null) {
            // 解析失败重试一次（不做字符串截取硬解），并保留第一次的失败原因
            String firstError = llmUtils.getLastError();
            log.info("行程 JSON 首次解析失败，重试一次。city={}, firstError={}", dto.getCity(), firstError);
            itinerary = parseItinerary(
                    llmUtils.chat(ITINERARY_SYSTEM_PROMPT,
                            buildItineraryUserPrompt(dto, true, travelData, knowledge), true));
            if (itinerary == null) {
                String reason = llmUtils.getLastError();
                if (reason == null) {
                    reason = firstError != null ? firstError : "大模型返回内容无法解析为行程结构";
                }
                ItineraryVO fallback = fallbackBuilder.build(dto, reason,
                        travelData.attractions().orElse(List.of()));
                amapTravelDataService.enrich(fallback, travelData);
                return fallback;
            }
        }

        // 服务端回填请求要素，防止模型漏字段；并标记数据来源
        itinerary.setSuccess(true);
        itinerary.setError(null);
        itinerary.setCity(dto.getCity());
        itinerary.setDay(dto.getDays());
        if (dto.getTotalBudget() != null) {
            itinerary.setBudget(dto.getTotalBudget().intValue());
        }
        if (itinerary.getSummary() == null || itinerary.getSummary().isBlank()) {
            itinerary.setSummary(dto.getCity() + " · " + dto.getDays() + "天行程");
        }
        if (itinerary.getTips() == null) {
            itinerary.setTips(List.of());
        }
        if (itinerary.getBudgetBreakdown() == null) {
            itinerary.setBudgetBreakdown(new ItineraryVO.BudgetBreakdown());
        }
        itinerary.setDegraded(false);
        itinerary.setSourceFlags(ItineraryVO.SourceFlags.real());
        if (knowledge != null) {
            itinerary.getSourceFlags().setKnowledge("real");
        }
        amapTravelDataService.enrich(itinerary, travelData);
        return itinerary;
    }

    /** 用 Jackson 解析模型返回；days 缺失/为空视为无效，返回 null 触发重试或降级 */
    private ItineraryVO parseItinerary(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            ItineraryVO vo = objectMapper.readValue(content, ItineraryVO.class);
            if (vo.getDays() == null || vo.getDays().isEmpty()) {
                log.warn("行程 JSON 缺少有效的 days 字段");
                return null;
            }
            return vo;
        } catch (Exception e) {
            log.warn("行程 JSON 解析失败：{}", e.getMessage());
            return null;
        }
    }

    /** 拼出结构化行程的用户提示词；retry=true 时追加只输出 JSON 的强调语 */
    private String buildItineraryUserPrompt(TravelRequestDTO dto, boolean retry,
                                            AmapTravelDataService.TravelData travelData,
                                            CityKnowledgeBase.Knowledge knowledge) {
        StringBuilder sb = new StringBuilder()
                .append("目的地城市：").append(dto.getCity()).append('\n')
                .append("行程天数：").append(dto.getDays()).append('\n')
                .append("总预算（元）：").append(dto.getTotalBudget()).append('\n')
                .append(cityKnowledgeBase.toPromptContext(knowledge))
                .append(amapTravelDataService.promptContext(travelData))
                .append("\n请按约定结构输出行程 JSON。");
        if (retry) {
            sb.append("\n注意：上一次返回无法被解析，本次请严格只输出 JSON 对象本身，")
                    .append("不要包含代码块标记（```）或任何说明文字。");
        }
        return sb.toString();
    }

    /**
     * 流式对话（SSE）。
     * 未配置 Key 或大模型不可用时，向前端推送一条中文 error 事件后正常结束，不抛异常。
     */
    public SseEmitter chatStream(String message) {
        SseEmitter emitter = new SseEmitter(180_000L);

        if (!llmUtils.isConfigured()) {
            sendErrorAndComplete(emitter,
                    "大模型 API Key 未配置（环境变量 LLM_API_KEY），请配置后重启服务");
            return emitter;
        }

        new Thread(() -> {
            try {
                String fullContent = llmUtils.chatStream(SYSTEM_PROMPT, message, content -> {
                    try {
                        String json = objectMapper.writeValueAsString(StreamChunkVO.of(content));
                        emitter.send(SseEmitter.event().data(json));
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                });
                if (fullContent == null) {
                    sendErrorAndComplete(emitter, llmUtils.getLastError());
                    return;
                }
                emitter.send(SseEmitter.event()
                        .data(objectMapper.writeValueAsString(new StreamDoneVO())));
                emitter.complete();
            } catch (Exception e) {
                sendErrorAndComplete(emitter, "流式对话异常：" + e.getMessage());
            }
        }, "llm-sse-thread").start();
        return emitter;
    }

    private void sendErrorAndComplete(SseEmitter emitter, String message) {
        try {
            String errorJson = objectMapper.writeValueAsString(StreamErrorVO.of(message));
            emitter.send(SseEmitter.event().data(errorJson));
        } catch (Exception ignored) {
            // 推送错误失败时无需再处理
        } finally {
            emitter.complete();
        }
    }
}
