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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "wenxinblog-user")
public class UserDocument {
    @Id
    private String id;
    @Field(name = "display_name", type = FieldType.Keyword)
    private String displayName;
    @Field(type = FieldType.Keyword)
    private String username;
    @Field(type = FieldType.Text)
    private String bio;
    @Field(name = "avatar_url", type = FieldType.Keyword)
    private String avatarUrl;
    @Field(name = "follower_count", type = FieldType.Integer)
    private int followerCount;
    @Field(name = "post_count", type = FieldType.Integer)
    private int postCount;
    @Field(name = "created_at", type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime createdAt;
}
