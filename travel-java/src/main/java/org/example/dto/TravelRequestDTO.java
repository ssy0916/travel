package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.util.List;

@Data
/**
 * 旅游行程推荐请求 DTO
 */
public class TravelRequestDTO {
    @NotBlank(message = "城市不能为空")
    private String city;
    @NotNull(message = "天数不能为空")
    @Positive(message = "天数必须大于 0")
    private Integer days;
    @NotNull(message = "总预算不能为空")
    @PositiveOrZero(message = "总预算不能小于 0")
    private Double totalBudget;
    private String systemPrompt;
    private String userPrompt;
    private List<String> tips;
    private List<String> warnings;
}
