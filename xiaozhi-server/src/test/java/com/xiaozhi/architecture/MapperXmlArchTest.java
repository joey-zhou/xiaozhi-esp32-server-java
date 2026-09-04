package com.xiaozhi.architecture;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 扫 classpath 上全部 Mapper XML，钉住 resultType 不得指向 Req/Resp。
 */
class MapperXmlArchTest {

    private static final Pattern RESULT_TYPE = Pattern.compile("resultType\\s*=\\s*\"([^\"]+)\"");

    private static final Set<String> RESP_RESULT_TYPES = Set.of(
        "MessageResp", "ConversationResp");

    /** key 是 mapper/ 起的相对路径，value 是文件全文。 */
    private static Map<String, String> mapperXml;

    @BeforeAll
    static void loadMapperXml() throws IOException {
        mapperXml = new LinkedHashMap<>();
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml")) {
            String uri = resource.getURI().toString();
            try (InputStream in = resource.getInputStream()) {
                mapperXml.put(uri.substring(uri.lastIndexOf("/mapper/") + 1),
                    new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void mapperXmlIsActuallyScanned() {
        assertThat(mapperXml)
            .as("classpath*:mapper/**/*.xml 没扫到足够的文件，resultType 规则会假绿")
            .hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void resultTypeDoesNotPointToReqOrResp() {
        Map<String, Set<String>> offending = mapperXml.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey,
                e -> dtoResultTypes(e.getValue()).stream()
                    .filter(type -> !RESP_RESULT_TYPES.contains(simpleName(type)))
                    .collect(Collectors.toCollection(TreeSet::new))))
            .entrySet().stream()
            .filter(e -> !e.getValue().isEmpty())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertThat(offending)
            .as("resultType 直指 Req/Resp 会让 Service 返回 web 出参，SQL 直出的附加列应落到包内 XxxProjection")
            .isEmpty();
    }

    @Test
    void respResultTypesMatchTheRegistryExactly() {
        Set<String> actual = mapperXml.values().stream()
            .flatMap(xml -> dtoResultTypes(xml).stream())
            .map(MapperXmlArchTest::simpleName)
            .collect(Collectors.toSet());

        assertThat(actual)
            .as("resultType 指向 Req/Resp 的集合变了，请同步改 RESP_RESULT_TYPES")
            .containsExactlyInAnyOrderElementsOf(RESP_RESULT_TYPES);
    }

    private static Set<String> dtoResultTypes(String xml) {
        Set<String> types = new TreeSet<>();
        Matcher matcher = RESULT_TYPE.matcher(xml);
        while (matcher.find()) {
            String type = matcher.group(1);
            if (type.contains(".model.resp.") || type.contains(".model.req.")) {
                types.add(type);
            }
        }
        return types;
    }

    private static String simpleName(String fullName) {
        return fullName.substring(fullName.lastIndexOf('.') + 1);
    }
}
