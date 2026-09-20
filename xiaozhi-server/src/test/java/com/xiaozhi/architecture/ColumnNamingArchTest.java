package com.xiaozhi.architecture;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 库里的列名一律驼峰，与 DO 字段同名；mybatis 关着 map-underscore-to-camel-case，字段名就是列名。
 * 下划线列名会逼着 DO 逐个字段写 @TableField 指列，也没法继承 BaseDO 拿到 createTime/updateTime 的自动填充。
 */
class ColumnNamingArchTest {

    private static JavaClasses dataObjects;

    @BeforeAll
    static void importClasses() {
        dataObjects = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.xiaozhi")
            .that(DescribedPredicate.describe("reside in a dataobject package",
                javaClass -> javaClass.getPackageName().contains(".dataobject")));
    }

    @Test
    void dataObjectsAreActuallyScanned() {
        assertThat(dataObjects.size())
            .as("没扫到足够的 DO，列名规则会假绿")
            .isGreaterThanOrEqualTo(10);
    }

    @Test
    void columnsAreCamelCase() {
        fields().should(new ArchCondition<JavaField>("map to a camelCase column") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                String column = "";
                if (field.isAnnotatedWith(TableField.class)) {
                    column = field.getAnnotationOfType(TableField.class).value();
                } else if (field.isAnnotatedWith(TableId.class)) {
                    column = field.getAnnotationOfType(TableId.class).value();
                }
                if (column.contains("_")) {
                    events.add(SimpleConditionEvent.violated(field,
                        field.getFullName() + " 映射到下划线列 " + column + "，列名应为驼峰并与字段同名"));
                }
            }
        }).check(dataObjects);
    }
}
