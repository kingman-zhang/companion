package com.kingman.companion.framework.common;

import java.util.List;

/**
 * MapStruct DTO ↔ Entity 映射接口规范
 *
 * @param <D> DTO 类型
 * @param <E> Entity 类型
 */
public interface EntityMapper<D, E> {

    D toDto(E entity);

    E toEntity(D dto);

    List<D> toDto(List<E> entityList);

    List<E> toEntity(List<D> dtoList);
}
