package org.example.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
/**
 * 旅游行程推荐请求 DTO
 */
public class TravelRequestDTO {
    @NotNull(message = "城市不能为空")
    private String city;
    @NotNull(message = "天数不能为空")
    private Integer days;
    @NotNull(message = "总预算不能为空")
    private Double totalBudget;
    private String systemPrompt;
    private String userPrompt;
    private List<String> tips;
    private List<String> warnings;
}
