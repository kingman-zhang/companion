package com.kingman.companion.module.rewrite.entity;

import com.kingman.companion.framework.common.AbstractBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * 消息改写记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "rewrite_records")
public class RewriteRecord extends AbstractBaseEntity {

    private String sessionId;
    private String userId;
    /** 原始消息，10–1000 字 */
    private String originalMessage;
    /** 固定 3 个版本：gentle / direct / brief */
    private List<RewriteVariant> variants;
}
