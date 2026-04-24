package com.kingman.companion.framework.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页结果包装
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IPage<T> {

    private List<T> records;
    private long total;
    private long current;
    private long size;

    public static <T> IPage<T> of(List<T> records, long total, long current, long size) {
        return new IPage<>(records, total, current, size);
    }
}
