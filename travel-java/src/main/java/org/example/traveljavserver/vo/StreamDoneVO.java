package org.example.traveljavserver.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式返回的结束标记
 */
@Data
@NoArgsConstructor
public class StreamDoneVO {
    private String type = "done";
}
