package com.xiaozhi.config.convert;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.req.ConfigCreateReq;
import com.xiaozhi.common.model.req.ConfigTestReq;
import com.xiaozhi.common.model.req.ConfigUpdateReq;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住 Req → BO 边界：密钥字段的空白串一律规范成 null，非密钥字段原样透传。
 */
class ConfigConvertBlankSecretTest {

    private final ConfigConvert configConvert = Mappers.getMapper(ConfigConvert.class);

    @Test
    void createReqBlankSecretsBecomeNull() {
        ConfigBO bo = configConvert.toBO(createReqWithBlankSecrets());

        assertBlankSecretsAreNull(bo);
        assertThat(bo.getConfigName()).isEmpty();
        assertThat(bo.getApiUrl()).isEmpty();
    }

    @Test
    void updateReqBlankSecretsBecomeNull() {
        ConfigBO bo = configConvert.toBO(updateReqWithBlankSecrets());

        assertBlankSecretsAreNull(bo);
        assertThat(bo.getApiUrl()).isEqualTo("https://form.example.com");
    }

    @Test
    void testReqBlankSecretsBecomeNull() {
        ConfigBO bo = configConvert.toBO(testReqWithBlankSecrets());

        assertBlankSecretsAreNull(bo);
        assertThat(bo.getConfigId()).isEqualTo(7);
    }

    @Test
    void filledSecretsArePassedThrough() {
        ConfigBO bo = configConvert.toBO(testReqWithFilledSecrets());

        assertThat(bo.getApiKey()).isEqualTo("form-key");
        assertThat(bo.getApiSecret()).isEqualTo("form-secret");
        assertThat(bo.getAk()).isEqualTo("form-ak");
        assertThat(bo.getSk()).isEqualTo("form-sk");
    }

    private static void assertBlankSecretsAreNull(ConfigBO bo) {
        assertThat(bo.getApiKey()).isNull();
        assertThat(bo.getApiSecret()).isNull();
        assertThat(bo.getAk()).isNull();
        assertThat(bo.getSk()).isNull();
    }

    private static ConfigCreateReq createReqWithBlankSecrets() {
        ConfigCreateReq req = new ConfigCreateReq();
        req.setConfigName("");
        req.setApiUrl("");
        req.setApiKey("");
        req.setApiSecret("   ");
        req.setAk("");
        req.setSk("\t");
        return req;
    }

    private static ConfigUpdateReq updateReqWithBlankSecrets() {
        ConfigUpdateReq req = new ConfigUpdateReq();
        req.setApiUrl("https://form.example.com");
        req.setApiKey("");
        req.setApiSecret("   ");
        req.setAk("");
        req.setSk("\t");
        return req;
    }

    private static ConfigTestReq testReqWithBlankSecrets() {
        ConfigTestReq req = new ConfigTestReq();
        req.setConfigId(7);
        req.setApiKey("");
        req.setApiSecret("   ");
        req.setAk("");
        req.setSk("\t");
        return req;
    }

    private static ConfigTestReq testReqWithFilledSecrets() {
        ConfigTestReq req = new ConfigTestReq();
        req.setApiKey("form-key");
        req.setApiSecret("form-secret");
        req.setAk("form-ak");
        req.setSk("form-sk");
        return req;
    }
}
