package com.kingman.companion.framework.common;

import lombok.Data;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

/**
 * MongoDB Entity 基类
 * 提供主键、审计字段、软删除支持
 */
@Data
public abstract class AbstractBaseEntity {

    @Id
    private String id;

    @CreatedDate
    private LocalDateTime createTime;

    @LastModifiedDate
    private LocalDateTime modifyTime;

    @CreatedBy
    private String createUser;

    @LastModifiedBy
    private String modifyUser;

    /** 软删除标志，禁止物理删除 */
    private Boolean deleted = false;

    /** 租户隔离字段（多租户场景使用） */
    private String packageNo;
}
