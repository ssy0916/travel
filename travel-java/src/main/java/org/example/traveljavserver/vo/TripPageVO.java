package org.example.traveljavserver.vo;

import lombok.Data;

import java.util.List;

/**
 * 行程分页返回。
 */
@Data
public class TripPageVO {

    private List<TripVO> list;
    private long total;
    private int page;
    private int size;
    private boolean hasMore;
}
