package com.xiaozhi.role.dal;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.xiaozhi.role.dal.mysql.dataobject.RoleDO;
import com.xiaozhi.support.MybatisPlusTestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 角色切回本地 TTS / STT 时 ttsId / sttId 要写成 NULL。
 * updateById 默认跳过 null 字段，这两列不声明 ALWAYS 策略的话，切回本地保存后库里还是原来的第三方配置。
 */
class RoleDOUpdateStrategyTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestHelper.initTableInfo(RoleDO.class);
    }

    @Test
    void ttsAndSttColumnsAreAlwaysWrittenOnUpdate() {
        assertThat(updateStrategyOf("ttsId")).isEqualTo(FieldStrategy.ALWAYS);
        assertThat(updateStrategyOf("sttId")).isEqualTo(FieldStrategy.ALWAYS);
    }

    private static FieldStrategy updateStrategyOf(String property) {
        return TableInfoHelper.getTableInfo(RoleDO.class).getFieldList().stream()
                .filter(field -> field.getProperty().equals(property))
                .map(TableFieldInfo::getUpdateStrategy)
                .findFirst()
                .orElseThrow();
    }
}
