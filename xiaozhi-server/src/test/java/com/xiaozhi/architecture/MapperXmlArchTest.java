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

    /** 单引号与双引号都接受，group(2) 是类全名。 */
    private static final Pattern RESULT_TYPE = Pattern.compile("resultType\\s*=\\s*([\"'])([^\"']+)\\1");

    private static final Pattern DTO_PACKAGE = Pattern.compile("\\.model\\.(req|resp)\\.");

    private static final Pattern NAMESPACE =
        Pattern.compile("<mapper\\s+namespace\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern STATEMENT_ID =
        Pattern.compile("<(?:select|insert|update|delete)\\s+[^>]*\\bid\\s*=\\s*[\"']([^\"']+)[\"']");

    /**
     * 单表且 MyBatis-Plus 能表达的语句禁止写进 Mapper XML，这份清单冻结的是当前已核实
     * 「确需 XML」的存量语句（JOIN/子查询/批量等 MP 原生表达不了）。
     * 新增或删除语句都要显式更新这里，逼着改动者先判断是否该走 MP 而不是顺手加进 XML。
     */
    private static final Set<String> STATEMENT_INVENTORY = Set.of(
        "com.xiaozhi.authrolepermission.dal.mysql.mapper.AuthRolePermissionMapper#insertBatch",
        "com.xiaozhi.device.dal.mysql.mapper.DeviceMapper#selectPage",
        // 会话与摘要两个列表页 JOIN 设备/角色表直出名字
        "com.xiaozhi.message.dal.mysql.mapper.ConversationMapper#selectPage",
        "com.xiaozhi.summary.dal.mysql.mapper.SummaryMapper#selectPage",
        "com.xiaozhi.message.dal.mysql.mapper.MessageMapper#selectPage",
        "com.xiaozhi.role.dal.mysql.mapper.RoleMapper#selectPage",
        "com.xiaozhi.user.dal.mysql.mapper.UserMapper#selectPage"
    );

    /** key 是资源 URI 全串，value 是文件全文。 */
    private static Map<String, String> mapperXml;

    @BeforeAll
    static void loadMapperXml() throws IOException {
        mapperXml = new LinkedHashMap<>();
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml")) {
            try (InputStream in = resource.getInputStream()) {
                mapperXml.put(resource.getURI().toString(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
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
            .filter(e -> !dtoResultTypes(e.getValue()).isEmpty())
            .collect(Collectors.toMap(Map.Entry::getKey, e -> dtoResultTypes(e.getValue())));

        assertThat(offending)
            .as("resultType 直指 Req/Resp 会让 Service 返回 web 出参，SQL 直出的附加列应落到包内 XxxProjection")
            .isEmpty();
    }

    @Test
    void mapperXmlStatementInventoryIsFrozen() {
        Set<String> actual = new TreeSet<>();
        for (String xml : mapperXml.values()) {
            Matcher nsMatcher = NAMESPACE.matcher(xml);
            String namespace = nsMatcher.find() ? nsMatcher.group(1) : "";
            Matcher idMatcher = STATEMENT_ID.matcher(xml);
            while (idMatcher.find()) {
                actual.add(namespace + "#" + idMatcher.group(1));
            }
        }
        assertThat(actual)
            .as("Mapper XML 新增/删除了语句：先确认新语句是否'单表且 MyBatis-Plus 能表达'——能表达就不该写进 XML；"
                + "确实需要写 XML（JOIN/子查询等）就把变化同步进 STATEMENT_INVENTORY")
            .isEqualTo(STATEMENT_INVENTORY);
    }

    /** 按类全名判断，resultType 落在 ..model.req.. / ..model.resp.. 下即违规。 */
    private static Set<String> dtoResultTypes(String xml) {
        Set<String> types = new TreeSet<>();
        Matcher matcher = RESULT_TYPE.matcher(xml);
        while (matcher.find()) {
            String type = matcher.group(2);
            if (DTO_PACKAGE.matcher(type).find()) {
                types.add(type);
            }
        }
        return types;
    }
}
