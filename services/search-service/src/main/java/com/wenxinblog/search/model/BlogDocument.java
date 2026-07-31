package com.wenxinblog.search.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "wenxinblog-blog")
public class BlogDocument {
    @Id
    private String id;
    @Field(type = FieldType.Text)
    private String title;
    @Field(type = FieldType.Text)
    private String content;
    @Field(type = FieldType.Text)
    private String summary;
    @Field(name = "author_id", type = FieldType.Keyword)
    private String authorId;
    @Field(name = "author_name", type = FieldType.Keyword)
    private String authorName;
    @Field(type = FieldType.Keyword)
    private List<String> tags;
    @Field(type = FieldType.Keyword)
    private String category;
    @Field(type = FieldType.Keyword)
    private String status;
    @Field(name = "view_count", type = FieldType.Integer)
    private int viewCount;
    @Field(name = "like_count", type = FieldType.Integer)
    private int likeCount;
    @Field(name = "comment_count", type = FieldType.Integer)
    private int commentCount;
    @Field(name = "published_at", type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime publishedAt;
    @Field(name = "created_at", type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime createdAt;
}
