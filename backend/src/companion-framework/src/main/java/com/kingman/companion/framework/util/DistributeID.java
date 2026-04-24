package com.kingman.companion.framework.util;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;

/**
 * 分布式 ID 生成器（Snowflake 算法）
 */
public class DistributeID {

    private static final Snowflake SNOWFLAKE = IdUtil.getSnowflake(1, 1);

    private DistributeID() {
    }

    /**
     * 生成全局唯一 ID（字符串形式）
     */
    public static String generate() {
        return SNOWFLAKE.nextIdStr();
    }

    /**
     * 生成全局唯一 ID（long 形式）
     */
    public static long generateLong() {
        return SNOWFLAKE.nextId();
    }
}
