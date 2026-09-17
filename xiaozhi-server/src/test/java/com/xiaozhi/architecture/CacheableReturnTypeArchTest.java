package com.xiaozhi.architecture;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * 缓存方法不得构造 JDK 不可变集合。
 * <p>
 * 缓存值经 GenericJackson2JsonRedisSerializer 写进 Redis，多态类型信息按 NON_FINAL 策略写 {@code @class}。
 * {@code List.of()} / {@code Stream.toList()} / {@code Collections.emptyList()} 返回的都是 final 实现，
 * 拿不到 {@code @class}，写进去的是裸数组；回读时首元素会被当成类型 id 解析，抛 SerializationException。
 * 表现为「写缓存正常、下次命中就炸」，且 TTL 越长越难复现。
 * <p>
 * 正确写法是收成可变实现，例如 {@code Collectors.toCollection(ArrayList::new)} 或 {@code new ArrayList<>()}。
 * <p>
 * 扫描范围是整个 com.xiaozhi。依赖只在 xiaozhi-server 声明的 archunit，且要一次扫全部业务模块的编译产物，
 * 所以与其余架构测试一样留在 server 模块。
 */
class CacheableReturnTypeArchTest {

    /** 全部返回 final 不可变实现的工厂方法，按「类名.方法名」比对 */
    private static final Set<String> IMMUTABLE_FACTORIES = Set.of(
        "java.util.List.of",
        "java.util.Set.of",
        "java.util.Map.of",
        "java.util.Map.ofEntries",
        "java.util.List.copyOf",
        "java.util.Set.copyOf",
        "java.util.Map.copyOf",
        "java.util.stream.Stream.toList",
        "java.util.Collections.emptyList",
        "java.util.Collections.emptySet",
        "java.util.Collections.emptyMap",
        "java.util.Collections.singletonList",
        "java.util.Collections.singleton",
        "java.util.Collections.singletonMap"
    );

    private static JavaClasses xiaozhiClasses;

    @BeforeAll
    static void importClasses() {
        xiaozhiClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.xiaozhi");
    }

    @Test
    void cacheableMethodsDoNotBuildImmutableCollections() {
        ArchRule rule = noMethods()
            .that().areAnnotatedWith(Cacheable.class)
            .or().areAnnotatedWith(CachePut.class)
            .should(buildImmutableCollection())
            .because("缓存值要写进 Redis，JDK 不可变集合是 final 实现、带不上 @class，回读时会抛序列化异常");

        rule.check(xiaozhiClasses);
    }

    private static ArchCondition<JavaMethod> buildImmutableCollection() {
        return new ArchCondition<>("构造 JDK 不可变集合") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                for (JavaCall<?> call : method.getCallsFromSelf()) {
                    String target = call.getTarget().getOwner().getName() + "." + call.getTarget().getName();
                    if (IMMUTABLE_FACTORIES.contains(target)) {
                        events.add(SimpleConditionEvent.violated(method,
                            method.getFullName() + " 调用了 " + target
                                + "，返回不可变集合会让缓存回读时抛序列化异常，"
                                + "改用 Collectors.toCollection(ArrayList::new) 或 new ArrayList<>()"));
                    }
                }
            }
        };
    }
}
