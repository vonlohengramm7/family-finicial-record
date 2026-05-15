package com.familyledger.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Data
@TableName("transaction")
public class Transaction {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long categoryId;

    private Long projectId;

    private String tags;

    private BigDecimal amount;

    private LocalDate transDate;

    private LocalTime transTime;

    private String note;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
