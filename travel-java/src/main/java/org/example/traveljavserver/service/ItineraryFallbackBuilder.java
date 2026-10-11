package org.example.traveljavserver.service;

import org.example.dto.TravelRequestDTO;
import org.example.traveljavserver.vo.ItineraryVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 降级行程模板构建器。
 * 未配 LLM Key、LLM 调用失败或连续两次 JSON 解析失败时使用：
 * - 结构始终完整（success=true，前端可正常渲染），degraded=true
 * - 有真实高德候选 POI 时直接按候选排期（不编造），无候选时用“待重新生成”占位
 * - 预算分项全部 null（前端显示“待生成”），total 回填用户预算
 */
@Service
public class ItineraryFallbackBuilder {

    private static final List<String> PERIODS = List.of("上午", "下午", "晚上");

    /**
     * @param reason     降级原因（写入 degradeReason 与 tips）
     * @param candidates 高德真实景点候选；可为空列表（此时纯占位，不编造景点）
     */
    public ItineraryVO build(TravelRequestDTO dto, String reason,
                             List<ItineraryVO.Attraction> candidates) {
        ItineraryVO vo = new ItineraryVO();
        vo.setSuccess(true);
        vo.setError(null);
        vo.setCity(dto.getCity());
        vo.setDay(dto.getDays());
        if (dto.getTotalBudget() != null) {
            vo.setBudget(dto.getTotalBudget().intValue());
        }
        vo.setSummary(dto.getCity() + " · " + dto.getDays() + "天行程（降级模板）");
        vo.setDegraded(true);
        vo.setDegradeReason(reason);

        vo.setDays(buildDays(dto, candidates));
        vo.setBudgetBreakdown(buildBudget(dto));
        vo.setTips(buildTips(reason));
        vo.setWeather(List.of());
        vo.setHotelRecommendations(List.of());
        vo.setSourceFlags(ItineraryVO.SourceFlags.fallback());
        return vo;
    }

    /** 每天 上午/下午/晚上 三个时段；有候选时按顺序安排真实 POI，否则占位 */
    private List<ItineraryVO.DayPlan> buildDays(TravelRequestDTO dto,
                                                List<ItineraryVO.Attraction> candidates) {
        int days = dto.getDays() != null && dto.getDays() > 0 ? dto.getDays() : 1;
        // 候选依次消费：降级模板总共最多安排 days*3 个真实候选，其余时段占位
        int cursor = 0;
        List<ItineraryVO.Attraction> pool = (candidates == null) ? List.of() : candidates;

        List<ItineraryVO.DayPlan> result = new ArrayList<>();
        for (int d = 1; d <= days; d++) {
            ItineraryVO.DayPlan day = new ItineraryVO.DayPlan();
            day.setDay(d);
            day.setTitle("第" + d + "天");

            List<ItineraryVO.Slot> slots = new ArrayList<>();
            for (String period : PERIODS) {
                ItineraryVO.Slot slot = new ItineraryVO.Slot();
                slot.setPeriod(period);
                List<ItineraryVO.Attraction> attractions = new ArrayList<>();
                if (cursor < pool.size()) {
                    // 直接使用高德真实候选，不编造时长/费用/交通
                    ItineraryVO.Attraction real = pool.get(cursor++);
                    attractions.add(real);
                } else {
                    attractions.add(placeholder(dto.getCity()));
                }
                slot.setAttractions(attractions);
                slots.add(slot);
            }
            day.setSlots(slots);
            result.add(day);
        }
        return result;
    }

    /** 无真实候选时的占位景点：duration/cost/transport 均为 null */
    private ItineraryVO.Attraction placeholder(String city) {
        ItineraryVO.Attraction a = new ItineraryVO.Attraction();
        a.setName(city + "城市景点（待重新生成）");
        a.setDuration(null);
        a.setCost(null);
        a.setTransport(null);
        a.setDescription("当前为降级行程模板，该时段景点待重新生成，请点击“重新生成”获取大模型规划结果。");
        a.setPoiVerification("unverified");
        return a;
    }

    private ItineraryVO.BudgetBreakdown buildBudget(TravelRequestDTO dto) {
        ItineraryVO.BudgetBreakdown b = new ItineraryVO.BudgetBreakdown();
        b.setAccommodation(null);
        b.setDining(null);
        b.setTransport(null);
        b.setTickets(null);
        b.setOther(null);
        b.setTotal(dto.getTotalBudget() != null ? dto.getTotalBudget().intValue() : null);
        return b;
    }

    private List<String> buildTips(String reason) {
        List<String> tips = new ArrayList<>();
        if (reason != null && !reason.isBlank()) {
            tips.add("当前为降级行程模板：" + reason);
        }
        tips.add("行程结构仅供参考，可点击“重新生成”获取完整的大模型规划与预算明细。");
        tips.add("出行前请通过景区官方渠道核开放时间、门票与预约要求。");
        return tips;
    }
}
