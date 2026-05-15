package com.familyledger.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("project")
public class Project {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private LocalDate startDate;

    private LocalDate endDate;

    private BigDecimal budget;

    @TableField("is_archived")
    private Boolean isArchived;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
