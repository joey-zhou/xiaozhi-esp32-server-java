# Flyway 数据库迁移脚本

## 说明

- Flyway 会在应用启动时自动执行未执行的迁移脚本
- 脚本按版本号顺序执行（V1 → V2 → V3...）
- 已执行的脚本会记录在 `flyway_schema_history` 表中
- **禁止修改已执行的脚本**，否则校验失败

## 命名规则

```
V{版本号}__{描述}.sql
```

示例：
- `V1__init.sql` - 初始化数据库
- `V2__add_xxx.sql` - 添加某个字段

## 当前迁移

- `V1__init.sql` - 完整数据库初始化（基线版本）

## 新增迁移

1. 创建新脚本，版本号从 V2 开始递增：`V2__description.sql`
2. 编写 SQL（只包含变更，不要包含完整建表）
3. 提交到 Git
4. 启动应用，Flyway 自动执行

## 已有数据库

如果数据库已存在，Flyway 会：
1. 创建 `flyway_schema_history` 表
2. 标记 V0 为 baseline（因为配置了 `baseline-on-migrate: true`）
3. 只执行版本号 > 0 的新脚本

> **注意**：如果旧数据库存在 `flyway_schema_history` 表，需要先清空该表再启动应用。

## 查看迁移历史

```sql
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```
