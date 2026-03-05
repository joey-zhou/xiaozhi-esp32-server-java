# ORM 数据访问层迁移文档

## 迁移概述

本次迁移将项目的 ORM 数据访问层从 MyBatis Mapper 模式全面迁移到 Spring Data JPA 风格。

**迁移完成时间**: 2026 年 3 月 4 日

## 迁移范围

### 1. Entity 类更新

所有 Entity 类已添加 JPA 注解，与数据库表结构保持一致：

| Entity 类 | 数据库表 | 主键策略 |
|----------|---------|---------|
| SysUser | sys_user | IDENTITY |
| SysMessage | sys_message | IDENTITY |
| SysDevice | sys_device | 手动指定 (device_id) |
| SysRole | sys_role | IDENTITY |
| SysConfig | sys_config | IDENTITY |
| SysTemplate | sys_template | IDENTITY |
| SysAuthRole | sys_auth_role | IDENTITY |
| SysPermission | sys_permission | IDENTITY |
| SysRolePermission | sys_role_permission | IDENTITY |
| SysUserAuth | sys_user_auth | IDENTITY |
| SysSummary | sys_summary | 复合主键 (device_id, role_id, last_message_timestamp) |
| SysMcpToolExclude | sys_mcp_tool_exclude | IDENTITY |
| SysAgent | sys_agent | IDENTITY |

### 2. Repository 接口创建

已创建以下 Spring Data JPA Repository 接口：

| Repository | 继承接口 | 位置 |
|-----------|---------|------|
| UserRepository | JpaRepository<SysUser, Integer> | com.xiaozhi.repository |
| MessageRepository | JpaRepository<SysMessage, Integer> | com.xiaozhi.repository |
| DeviceRepository | JpaRepository<SysDevice, String> | com.xiaozhi.repository |
| RoleRepository | JpaRepository<SysRole, Integer> | com.xiaozhi.repository |
| ConfigRepository | JpaRepository<SysConfig, Integer> | com.xiaozhi.repository |
| TemplateRepository | JpaRepository<SysTemplate, Integer> | com.xiaozhi.repository |
| AuthRoleRepository | JpaRepository<SysAuthRole, Integer> | com.xiaozhi.repository |
| PermissionRepository | JpaRepository<SysPermission, Integer> | com.xiaozhi.repository |
| RolePermissionRepository | JpaRepository<SysRolePermission, Integer> | com.xiaozhi.repository |
| SysUserAuthRepository | JpaRepository<SysUserAuth, Long> | com.xiaozhi.repository |
| SummaryRepository | JpaRepository<SysSummary, String> | com.xiaozhi.repository |
| McpToolExcludeRepository | JpaRepository<SysMcpToolExclude, Long> | com.xiaozhi.repository |

### 3. Service 层更新

已更新以下 Service 实现类，将 Mapper 引用替换为 Repository：

| Service 实现类 | 状态 |
|--------------|------|
| SysUserServiceImpl | ✅ 已更新 |
| SysMessageServiceImpl | ✅ 已更新 |
| SysDeviceServiceImpl | ✅ 已更新 |
| SysRoleServiceImpl | ✅ 已更新 |
| SysConfigServiceImpl | ✅ 已更新 |
| SysTemplateServiceImpl | ✅ 已更新 |
| SysAuthRoleServiceImpl | ✅ 已更新 |
| SysPermissionServiceImpl | ✅ 已更新 |
| SysSummaryServiceImpl | ✅ 已更新 |
| McpToolExcludeServiceImpl | ✅ 已更新 |
| SysUserAuthServiceImpl | ✅ 已更新 |
| SysAgentServiceImpl | ✅ 已更新 |

### 4. 配置文件更新

**TransactionConfig.java** 已更新：
- 添加 `@EnableJpaRepositories` 启用 JPA Repository 扫描
- 添加 `@EnableJpaAuditing` 启用 JPA 审计功能

## 方法命名映射

### UserMapper → UserRepository

| 原 MyBatis 方法 | 新 Spring Data JPA 方法 |
|---------------|----------------------|
| selectUserByUsername | findByUsername |
| selectUserByWxOpenId | findByWxOpenId |
| selectUserByEmail | findByEmail |
| selectUserByTel | findByTel |
| selectUserByUserId | findById |
| queryUsers | findUsersWithStats (@Query) |
| queryCaptcha | countValidCaptcha (@Query) |

### MessageMapper → MessageRepository

| 原 MyBatis 方法 | 新 Spring Data JPA 方法 |
|---------------|----------------------|
| findById | findByIdAndActive (@Query) |
| find | findHistoryMessages (@Query) |
| findAfter | findMessagesAfter (@Query) |
| query | findMessages (@Query) |
| saveAll | saveAll (内置方法) |
| delete | deleteByDeviceAndUser (@Query) |
| updateMessageByAudioFile | updateAudioPath (@Query) |

### DeviceMapper → DeviceRepository

| 原 MyBatis 方法 | 新 Spring Data JPA 方法 |
|---------------|----------------------|
| selectDeviceById | findDeviceById (@Query) |
| query | findDevices (@Query) |
| queryVerifyCode | findVerifyCode (@Query) |
| update | updateDevice (@Query) |
| add | save (内置方法) |
| delete | deleteDevice (@Query) |
| updateCode | updateCode (@Query) |
| insertCode | insertCode (@Query) |
| batchUpdate | batchUpdateDevices (@Query) |

## 归档文件

原有 Mapper 文件已移动到以下目录：
```
src/main/java/com/xiaozhi/mapper/legacy/
```

包含以下文件：
- UserMapper.xml
- MessageMapper.xml
- DeviceMapper.xml
- RoleMapper.xml
- ConfigMapper.xml
- TemplateMapper.xml
- AuthRoleMapper.xml
- PermissionMapper.xml
- RolePermissionMapper.xml
- SysUserAuthMapper.xml
- SummaryMapper.xml
- McpToolExcludeMapper.xml

## 依赖配置

pom.xml 中已包含 Spring Data JPA 依赖：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

## 注意事项

1. **事务管理**: 所有 `@Transactional` 注解继续正常工作，事务管理器配置为 `DataSourceTransactionManager`

2. **缓存**: `@Cacheable` 和 `@CacheEvict` 注解继续正常工作

3. **分页**: PageHelper 仍然可用，但建议使用 Spring Data 的 `Pageable` 接口

4. **复杂查询**: 使用 `@Query` 注解配合 JPQL 或原生 SQL 实现

5. **驼峰命名**: Entity 类字段使用驼峰命名，自动映射到数据库下划线命名

## 后续工作

1. 移除对 MyBatis 的依赖（可选）
2. 清理 pom.xml 中不再需要的 MyBatis 相关配置
3. 进行全面的功能测试
4. 性能测试和优化

## 回滚方案

如需回滚到 MyBatis 版本：
1. 从 `legacy` 目录恢复 Mapper XML 文件
2. 恢复 Service 实现类中的 Mapper 引用
3. 恢复 Entity 类到原始状态
