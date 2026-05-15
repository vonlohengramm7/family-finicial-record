package com.familyledger.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String nickname;

    private String avatarUrl;

    @TableField("is_active")
    private Boolean isActive;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
