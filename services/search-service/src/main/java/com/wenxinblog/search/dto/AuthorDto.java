package com.wenxinblog.search.dto;

/** 搜索结果作者嵌套对象（对齐前端 SearchPostResult.author: {id,username,displayName,avatar}）。 */
public record AuthorDto(String id, String username, String displayName, String avatar) {}
