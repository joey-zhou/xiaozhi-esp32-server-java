package com.xiaozhi.common.model.bo;

/** 跨实例「某归属在某角色下的记忆删了」的载荷 */
public record MemoryScopeBO(String ownerId, Integer roleId) {
}
