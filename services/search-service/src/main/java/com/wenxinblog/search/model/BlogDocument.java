package com.wenxinblog.search.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlogDocument {
    private String id;
    private String title;
    private String content;
    private String summary;
    @JsonProperty("author_id")
    private String authorId;
    @JsonProperty("author_name")
    private String authorName;
    private List<String> tags;
    private String category;
    private String status;
    @JsonProperty("view_count")
    private int viewCount;
    @JsonProperty("like_count")
    private int likeCount;
    @JsonProperty("comment_count")
    private int commentCount;
    @JsonProperty("published_at")
    private String publishedAt;
    @JsonProperty("created_at")
    private String createdAt;
}
