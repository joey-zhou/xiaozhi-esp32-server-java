package com.xiaozhi.config.infrastructure.convert;

import com.xiaozhi.config.dal.mysql.dataobject.ConfigDO;
import com.xiaozhi.config.domain.AiConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigConverterTest {

    private final ConfigConverter converter = new ConfigConverter();

    @Test
    void imageInputSurvivesTheRoundTrip() {
        ConfigDO saved = new ConfigDO();
        saved.setConfigType("llm");
        saved.setImageInput(true);

        AiConfig config = converter.toDomain(saved);

        assertThat(config.getImageInput()).isTrue();
        assertThat(converter.toDO(config).getImageInput()).isTrue();
        assertThat(converter.toBO(config).getImageInput()).isTrue();
    }
}
