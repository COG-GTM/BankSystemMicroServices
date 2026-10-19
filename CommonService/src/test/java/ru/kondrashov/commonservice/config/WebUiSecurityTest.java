package ru.kondrashov.commonservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.kondrashov.commonservice.services.CommonService;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "person-service.client.password=service-password-0001",
        "common-service.security.users[0].username=operator",
        "common-service.security.users[0].password=operator-password-0001"
})
@AutoConfigureMockMvc
class WebUiSecurityTest {

    private static final UUID ID = UUID.fromString("c5a6757f-fec4-41f3-a6a6-eb03d70c0d6f");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommonService commonService;

    @Test
    void anonymousUsersAreSentToLogin() throws Exception {
        mockMvc.perform(get("/common/v1/info/people"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(post("/common/v1/delete/people/{id}", ID).param("_method", "DELETE").with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("**/login"));

        verifyNoInteractions(commonService);
    }

    @Test
    void operatorCanSignInAndBrowse() throws Exception {
        mockMvc.perform(formLogin().user("operator").password("operator-password-0001"))
                .andExpect(redirectedUrl("/common/v1/info/people"));
        mockMvc.perform(formLogin().user("operator").password("wrong-password-00001"))
                .andExpect(redirectedUrl("/login?error"));

        when(commonService.getPeople()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/common/v1/info/people").with(user("operator").roles(SecurityConfig.OPERATOR)))
                .andExpect(status().isOk());
    }

    @Test
    void stateChangingRequestsRequireCsrfToken() throws Exception {
        mockMvc.perform(post("/common/v1/delete/people/{id}", ID).param("_method", "DELETE")
                        .with(user("operator").roles(SecurityConfig.OPERATOR)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(commonService);

        mockMvc.perform(post("/common/v1/delete/people/{id}", ID).param("_method", "DELETE")
                        .with(user("operator").roles(SecurityConfig.OPERATOR)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        verify(commonService).deletePerson(ID);
    }
}
