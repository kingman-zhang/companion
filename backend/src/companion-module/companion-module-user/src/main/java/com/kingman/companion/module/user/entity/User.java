package com.kingman.companion.module.user.entity;

import com.kingman.companion.framework.common.AbstractBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 用户实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "users")
@CompoundIndex(name = "idx_open_id", def = "{'openId': 1}", unique = true)
public class User extends AbstractBaseEntity {

    private String openId;

    /** 订阅等级：free / premium */
    private String subscriptionTier = "free";
}
