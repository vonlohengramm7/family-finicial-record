package com.familyledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("state_snapshot")
public class StateSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String domain;
    private String snapshotKey;
    private String payloadJson;
    private String source;
    private LocalDateTime observedAt;
    private LocalDateTime freshUntil;
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String errorCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
