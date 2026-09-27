package com.xiaozhi.architecture;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 在空库上用只有库级权限的账号把全部 Flyway 脚本真跑一遍，再核对跑完的表结构装得下每个 DO 的字段。
 * 脚本完整执行只发生在新用户首次启动：开发者自己的库早就迁移过了，脚本里引用不存在的表或列、
 * 需要全局权限的语句（CREATE USER、GRANT）、写死的库名，本地都不会暴露，只有新用户启动才炸。
 *
 * <p>连接信息走 XIAOZHI_MIGRATION_TEST_URL / _USERNAME / _PASSWORD，CI 的 backend 工作流挂了 MySQL service，
 * 库名故意不叫 xiaozhi，脚本里写死库名会直接失败。本地没配就跳过，CI 里没配直接失败，免得工作流改坏了还假绿。
 *
 * <p>只能指向空库，且每次跑前都要重建：V1 带 DROP TABLE，指向有数据的库会把数据删光，所以非空库直接拒跑。
 */
@EnabledIf(value = "shouldRun", disabledReason = "未配置 XIAOZHI_MIGRATION_TEST_URL，跳过迁移校验")
class FlywayMigrationGuardTest {

    private static final String URL = System.getenv("XIAOZHI_MIGRATION_TEST_URL");

    private static Flyway flyway;

    /** CI 里不看有没有配库一律执行，没配就在下面断言失败 */
    static boolean shouldRun() {
        return System.getenv("CI") != null || (URL != null && !URL.isBlank());
    }

    @BeforeAll
    static void migrateEmptySchema() throws SQLException {
        assertThat(URL)
            .as("CI 里没配 XIAOZHI_MIGRATION_TEST_URL，迁移校验会被静默跳过")
            .isNotBlank();
        // 与 application.yml 的 spring.flyway 保持一致
        flyway = Flyway.configure()
            .dataSource(URL, System.getenv("XIAOZHI_MIGRATION_TEST_USERNAME"), System.getenv("XIAOZHI_MIGRATION_TEST_PASSWORD"))
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .encoding(StandardCharsets.UTF_8)
            .validateOnMigrate(false)
            .load();

        assertThat(query("SELECT TABLE_NAME, '' FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()"))
            .as("迁移校验只能指向空库，V1 会 DROP TABLE")
            .isEmpty();
        flyway.migrate();
    }

    @Test
    void everyScriptAppliesOnEmptySchema() {
        MigrationInfo[] migrations = flyway.info().all();

        assertThat(migrations)
            .as("没扫到迁移脚本，本条规则会退化成空真")
            .hasSizeGreaterThan(30);
        assertThat(migrations).allSatisfy(migration -> assertThat(migration.getState())
            .as(migration.getScript())
            .isEqualTo(MigrationState.SUCCESS));
    }

    /**
     * 脚本全跑通还不够：DO 加了字段却没补脚本，新库照样缺列，要到查询时才报 Unknown column。
     */
    @Test
    void schemaCoversEveryDataObjectField() throws SQLException {
        Map<String, Set<String>> columnsByTable = query(
            "SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()");

        List<Class<?>> dataObjects = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.xiaozhi")
            .stream()
            .filter(javaClass -> javaClass.isAnnotatedWith(TableName.class))
            .<Class<?>>map(JavaClass::reflect)
            .toList();
        assertThat(dataObjects)
            .as("没扫到足够的 DO，本条规则会假绿")
            .hasSizeGreaterThanOrEqualTo(10);

        // 与生产一致：map-underscore-to-camel-case 关着，字段名就是列名
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(false);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");

        List<String> missing = new ArrayList<>();
        for (Class<?> dataObject : dataObjects) {
            TableInfo tableInfo = TableInfoHelper.initTableInfo(assistant, dataObject);
            Set<String> columns = columnsByTable.getOrDefault(tableInfo.getTableName(), Set.of());
            List<String> expected = new ArrayList<>();
            if (tableInfo.havePK()) {
                expected.add(tableInfo.getKeyColumn());
            }
            tableInfo.getFieldList().forEach(field -> expected.add(field.getColumn()));
            expected.stream()
                .map(column -> column.replace("`", ""))
                .filter(column -> !columns.contains(column))
                .forEach(column -> missing.add(
                    tableInfo.getTableName() + "." + column + "（" + dataObject.getSimpleName() + "）"));
        }
        assertThat(missing)
            .as("DO 里有、迁移跑完的库里没有的列")
            .isEmpty();
    }

    /** 查两列结果，按第一列分组收第二列 */
    private static Map<String, Set<String>> query(String sql) throws SQLException {
        Map<String, Set<String>> grouped = new HashMap<>();
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                grouped.computeIfAbsent(resultSet.getString(1), key -> new HashSet<>()).add(resultSet.getString(2));
            }
        }
        return grouped;
    }
}
