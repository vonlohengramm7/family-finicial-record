package com.familyledger.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("auto_transaction")
public class AutoTransaction {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long categoryId;

    private BigDecimal amount;

    private String note;

    private String mode;

    private Integer monthlyDay;

    private Integer weeklyDay;

    private Integer yearlyMonth;

    private Integer yearlyDay;

    private LocalDate nextRunDate;

    @TableField("is_active")
    private Boolean isActive;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
