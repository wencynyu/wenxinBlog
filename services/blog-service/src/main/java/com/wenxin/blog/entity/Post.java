package com.wenxin.blog.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Table("posts")
public class Post {
    @Id
    private UUID id;
    private UUID authorId;
    private String title;
    private String content;
    private String summary;
    private String coverImage;
    private String status;
    private Boolean featured = false;
    private Integer viewCount = 0;
    private Integer likeCount = 0;
    private Integer commentCount = 0;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // join authors 表填充（findPublishedWithAuthor 等查询）
    @Transient
    private String authorUsername;
    @Transient
    private String authorDisplayName;
    @Transient
    private String authorAvatarUrl;

    // join post_tags + tags 填充（PostService 二次查询）
    @Transient
    private List<String> tags;

    // 便于前端序列化（author 嵌套对象）
    @Transient
    private AuthorInfo author;

    // 当前用户视角状态（getPost 时按 X-User-Id 回填，未登录为 false）。
    // @Getter(onMethod_=@JsonProperty(...)) 给 Lombok 生成的 getter 固定 wire 字段名为 isLiked/isFavorited：
    // 否则 Jackson 把 isXxx() 的 is 前缀去掉输出成 liked/favorited，与前端 Post 类型不一致，
    // 导致刷新后点赞/收藏状态丢失。（注解放字段上会产生重复字段，必须放 getter。）
    @Transient
    @Getter(onMethod_ = @JsonProperty("isLiked"))
    private boolean isLiked;
    @Transient
    @Getter(onMethod_ = @JsonProperty("isFavorited"))
    private boolean isFavorited;

    @Data
    public static class AuthorInfo {
        private String id;
        private String username;
        private String displayName;
        private String avatar;

        public AuthorInfo() {}

        public AuthorInfo(String id, String username, String displayName, String avatar) {
            this.id = id;
            this.username = username;
            this.displayName = displayName;
            this.avatar = avatar;
        }
    }
}
