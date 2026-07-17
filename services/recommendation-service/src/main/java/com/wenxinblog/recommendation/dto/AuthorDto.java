package com.wenxinblog.recommendation.dto;

/**
 * 推荐响应里的作者信息，对齐前端 recommend.ts 的 author 形状。
 */
public record AuthorDto(
        String id,
        String username,
        String displayName,
        String avatar
) {}
