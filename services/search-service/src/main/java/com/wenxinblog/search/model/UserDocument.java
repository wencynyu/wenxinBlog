package com.wenxinblog.search.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDocument {
    private String id;
    @JsonProperty("display_name")
    private String displayName;
    private String username;
    private String bio;
    @JsonProperty("avatar_url")
    private String avatarUrl;
    @JsonProperty("follower_count")
    private int followerCount;
    @JsonProperty("post_count")
    private int postCount;
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
