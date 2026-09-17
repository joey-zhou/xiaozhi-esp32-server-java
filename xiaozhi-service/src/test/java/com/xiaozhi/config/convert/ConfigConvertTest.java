package com.xiaozhi.config.convert;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.req.ConfigCreateReq;
import com.xiaozhi.common.model.req.ConfigUpdateReq;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 密钥字段的空白串必须规范成 null。
 * <p>
 * 前端编辑配置时密钥框留空表示「保持原值」，传过来的是空串；一旦原样落到 BO，
 * 更新就会把库里真实的 apiKey/apiSecret/ak/sk 覆盖成空串，该配置立刻不可用，
 * 而且原值已经没了。这四个字段的 blankToNull 是这条产品行为的唯一实现。
 */
class ConfigConvertTest {

    private final ConfigConvert convert = Mappers.getMapper(ConfigConvert.class);

    @Test
    void blankSecretsBecomeNullOnCreate() {
        ConfigCreateReq req = new ConfigCreateReq();
        req.setConfigName("我的模型");
        req.setConfigType("llm");
        req.setApiKey("   ");
        req.setApiSecret("");
        req.setAk(null);
        req.setSk("\t");

        ConfigBO bo = convert.toBO(req);

        assertThat(bo.getConfigName()).isEqualTo("我的模型");
        assertThat(bo.getApiKey()).isNull();
        assertThat(bo.getApiSecret()).isNull();
        assertThat(bo.getAk()).isNull();
        assertThat(bo.getSk()).isNull();
    }

    @Test
    void blankSecretsBecomeNullOnUpdate() {
        ConfigUpdateReq req = new ConfigUpdateReq();
        req.setApiKey("");
        req.setApiSecret("  ");

        ConfigBO bo = convert.toBO(req);

        assertThat(bo.getApiKey())
            .as("留空表示保持原值，原样落成空串会把库里真实密钥覆盖掉且不可恢复")
            .isNull();
        assertThat(bo.getApiSecret()).isNull();
    }

    @Test
    void realSecretsPassThroughUntouched() {
        ConfigCreateReq req = new ConfigCreateReq();
        req.setApiKey("sk-real-key");
        req.setAk("AKID");

        ConfigBO bo = convert.toBO(req);

        assertThat(bo.getApiKey()).isEqualTo("sk-real-key");
        assertThat(bo.getAk()).isEqualTo("AKID");
    }

    @Test
    void requestCannotDecideItsOwnIdOrOwner() {
        ConfigBO bo = convert.toBO(new ConfigCreateReq());

        assertThat(bo.getConfigId()).isNull();
        assertThat(bo.getUserId()).as("归属由登录态决定，请求带过来的一律不认").isNull();
        assertThat(bo.getCreateTime()).isNull();
        assertThat(bo.getUpdateTime()).isNull();
    }
}
