package com.wenxin.blog.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("tags")
public class Tag {
    @Id
    private Integer id;
    private String name;
    private String slug;
    private String description;
    private Integer postCount = 0;
}
