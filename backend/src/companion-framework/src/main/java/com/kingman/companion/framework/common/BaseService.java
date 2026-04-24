package com.kingman.companion.framework.common;

/**
 * 泛型 CRUD 服务接口
 *
 * @param <D>      DTO 类型
 * @param <TQuery> 查询条件类型
 */
public interface BaseService<D, TQuery> {

    D findById(String id);

    IPage<D> page(TQuery query);

    D save(D dto);

    D update(D dto);

    void deleteById(String id);
}
