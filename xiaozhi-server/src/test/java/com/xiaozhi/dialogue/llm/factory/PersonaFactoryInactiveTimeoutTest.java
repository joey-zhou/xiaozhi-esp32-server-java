package com.xiaozhi.dialogue.llm.factory;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.dialogue.runtime.Persona;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonaFactoryInactiveTimeoutTest {

    @Test
    void initializesSessionTimeoutBeforeReturningExistingPersona() {
        PersonaFactory factory = new PersonaFactory();
        ChatSession session = mock(ChatSession.class);
        DeviceBO device = new DeviceBO();
        RoleBO role = new RoleBO();
        role.setInactiveTimeoutSeconds(45);
        Persona persona = mock(Persona.class);
        when(session.getPersona()).thenReturn(persona);

        Persona result = factory.buildPersona(session, device, role);

        assertThat(result).isSameAs(persona);
        verify(session).setInactiveTimeoutSeconds(45);
    }
}
