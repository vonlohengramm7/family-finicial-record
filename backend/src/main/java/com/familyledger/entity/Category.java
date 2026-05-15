package com.familyledger.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("category")
public class Category {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;

    private String name;

    private String icon;

    private Integer sortOrder;

    @TableField("is_active")
    private Boolean isActive;
}
