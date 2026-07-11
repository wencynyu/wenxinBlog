package com.wenxin.blog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 分页响应，对齐前端 PaginatedResponse<T>（{items,total,page,pageSize,totalPages}）。
 */
@Data
@AllArgsConstructor
public class PaginatedResponse<T> {
    private List<T> items;
    private long total;
    private int page;
    private int pageSize;
    private int totalPages;

    public static <T> PaginatedResponse<T> of(List<T> items, int page, int size, long total) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PaginatedResponse<>(items, total, page, size, totalPages);
    }
}
