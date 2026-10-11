package org.example.traveljavserver.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式返回的内容分片
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StreamChunkVO {
    private String type = "chunk";
    private String content;

    public static StreamChunkVO of(String content) {
        return new StreamChunkVO("chunk", content);
    }
}
