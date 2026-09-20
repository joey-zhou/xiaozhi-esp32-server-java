package com.xiaozhi.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget.CodeUnitCallTarget;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.xiaozhi.utils.DateUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 取当前时间、取系统时区只能经 {@link DateUtils}。时间列是不带时区的 DATETIME，
 * 各处自己取时钟、自己选时区做 Instant 与 LocalDateTime 的互转，换个部署环境就会出现同一张表里两种口径的时间。
 */
class TimeSourceArchTest {

    /** 这些类上名为 now 的静态方法，不论带不带 Clock/ZoneId 参数都算绕开 DateUtils */
    private static final Set<String> NOW_OWNERS = Set.of(
        LocalDateTime.class.getName(), LocalDate.class.getName(), LocalTime.class.getName(), Instant.class.getName(),
        ZonedDateTime.class.getName(), OffsetDateTime.class.getName(), Year.class.getName(), YearMonth.class.getName());

    private static JavaClasses xiaozhiClasses;

    @BeforeAll
    static void importClasses() {
        xiaozhiClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.xiaozhi");
    }

    @Test
    void productionClassesAreActuallyScanned() {
        assertThat(xiaozhiClasses.contain(DateUtils.class))
            .as("没扫到 DateUtils 说明类路径不对，下面的规则会假绿")
            .isTrue();
    }

    @Test
    void currentTimeComesOnlyFromDateUtils() {
        ArchRule rule = noClasses()
            .that().doNotHaveFullyQualifiedName(DateUtils.class.getName())
            .should().callCodeUnitWhere(bypassesDateUtils())
            .because("取当前时间走 DateUtils.now()/today()/instant()/millis()，Instant 与 LocalDateTime 互转走 "
                + "DateUtils.toDateTime()/toInstant()；算耗时与超时用 System.nanoTime() 取起点、DateUtils.elapsedMillis() 取经过时间；"
                + "第三方 SDK 要 Date 的地方用 Date.from(DateUtils.instant())");

        rule.check(xiaozhiClasses);
    }

    @Test
    void modelTimeFieldsAreNamedLikeTheColumns() {
        ArchRule rule = noFields()
            .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("DO")
            .or().areDeclaredInClassesThat().haveSimpleNameEndingWith("BO")
            .or().areDeclaredInClassesThat().haveSimpleNameEndingWith("Req")
            .or().areDeclaredInClassesThat().haveSimpleNameEndingWith("Resp")
            .should().haveNameMatching(".*[a-z]At")
            .because("库里的时间列一律是 createTime/updateTime 这种 xxxTime，落库与对外模型跟着列名走；"
                + "同一个概念这边叫 createdAt 那边叫 createTime，转换器就得逐个写显式映射，前端也要记两套名字");

        rule.check(xiaozhiClasses);
    }

    private static DescribedPredicate<JavaCall<?>> bypassesDateUtils() {
        return new DescribedPredicate<>("read the system clock or the system default zone directly") {
            @Override
            public boolean test(JavaCall<?> call) {
                CodeUnitCallTarget target = call.getTarget();
                String owner = target.getOwner().getName();
                String name = target.getName();
                if ("now".equals(name) && NOW_OWNERS.contains(owner)) {
                    return true;
                }
                if (ZoneId.class.getName().equals(owner) && "systemDefault".equals(name)) {
                    return true;
                }
                if (Clock.class.getName().equals(owner) && name.startsWith("system")) {
                    return true;
                }
                // 当时间戳用的走 DateUtils.millis()；算耗时的拿两次墙上时间相减会被系统对时带偏，走 nanoTime
                if (System.class.getName().equals(owner) && "currentTimeMillis".equals(name)) {
                    return true;
                }
                if (Calendar.class.getName().equals(owner) && "getInstance".equals(name)) {
                    return true;
                }
                // 无参构造取的是当前时间，new Date(long) 只是换个类型装同一个时刻
                return Date.class.getName().equals(owner) && "<init>".equals(name)
                    && target.getRawParameterTypes().isEmpty();
            }
        };
    }
}
