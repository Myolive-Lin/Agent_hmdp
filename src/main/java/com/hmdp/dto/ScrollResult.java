package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScrollResult {
    /**
     * Feed 滚动分页结果。
     *
     * minTime：当前页最小时间戳，下一页作为 maxTime 使用。
     * offset：minTime 相同数据的偏移量，防止翻页重复。
     */
    private List<?> list;
    private Long minTime;
    private Integer offset;


}
