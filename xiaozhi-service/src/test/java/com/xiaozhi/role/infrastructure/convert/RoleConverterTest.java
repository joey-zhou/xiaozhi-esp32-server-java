package com.xiaozhi.role.infrastructure.convert;

import com.xiaozhi.role.dal.mysql.dataobject.RoleDO;
import com.xiaozhi.role.domain.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoleConverterTest {

    private final RoleConverter converter = new RoleConverter();

    @Test
    void preservesInactiveTimeoutAcrossDomainMappings() {
        RoleDO dataObject = roleDataObject(120);

        Role role = converter.toDomain(dataObject, List.of());

        assertThat(role.getInactiveTimeoutSeconds()).isEqualTo(120);
        assertThat(converter.toDataObject(role).getInactiveTimeoutSeconds()).isEqualTo(120);
        assertThat(converter.toBO(role, List.of()).getInactiveTimeoutSeconds()).isEqualTo(120);
    }

    @Test
    void defaultsInactiveTimeoutForLegacyRoleData() {
        Role role = converter.toDomain(roleDataObject(null), List.of());

        assertThat(role.getInactiveTimeoutSeconds()).isEqualTo(Role.DEFAULT_INACTIVE_TIMEOUT_SECONDS);
    }

    private RoleDO roleDataObject(Integer inactiveTimeoutSeconds) {
        RoleDO dataObject = new RoleDO();
        dataObject.setRoleId(1);
        dataObject.setUserId(2);
        dataObject.setRoleName("test-role");
        dataObject.setState("1");
        dataObject.setIsDefault("0");
        dataObject.setInactiveTimeoutSeconds(inactiveTimeoutSeconds);
        return dataObject;
    }
}
