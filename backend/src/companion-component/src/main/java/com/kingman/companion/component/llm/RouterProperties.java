package com.kingman.companion.component.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Model Router 配置（来自 {@code companion.llm.router.*}）
 *
 * <p>每个 {@link ModelTier} 对应一条有序的 {@link ModelConfig} 列表（fallback 链）。
 * 第一个模型失败/超时时，自动尝试下一个，直至链条耗尽。
 */
@Data
@Component
@ConfigurationProperties(prefix = "companion.llm.router")
public class RouterProperties {

    /**
     * 输入字符数超过此阈值时，将 tier 上调为 {@link ModelTier#LONG_CONTEXT}。
     * 默认 2000 字符。
     */
    private int longContextThreshold = 2000;

    /**
     * 各 tier 的模型 fallback 链。
     * YAML key 为 tier 枚举名（大写），value 为有序模型列表。
     */
    private Map<ModelTier, List<ModelConfig>> chains = new EnumMap<>(ModelTier.class);

    /** 获取指定 tier 的 fallback 链，不存在时返回空列表 */
    public List<ModelConfig> getChain(ModelTier tier) {
        return chains.getOrDefault(tier, new ArrayList<>());
    }
}
