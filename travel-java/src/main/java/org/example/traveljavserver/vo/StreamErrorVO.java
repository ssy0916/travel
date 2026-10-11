package org.example.traveljavserver.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式返回的错误信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StreamErrorVO {
    private String type = "error";
    private String error;

    public static StreamErrorVO of(String error) {
        return new StreamErrorVO("error", error);
    }
}
