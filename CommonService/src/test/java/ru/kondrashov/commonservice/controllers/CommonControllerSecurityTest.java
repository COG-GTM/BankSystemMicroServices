package ru.kondrashov.commonservice.controllers;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.kondrashov.commonservice.config.SecurityConfig;
import ru.kondrashov.commonservice.controllers.dto.AccountRequestDTO;
import ru.kondrashov.commonservice.controllers.dto.PersonRequestDTO;
import ru.kondrashov.commonservice.services.CommonService;

import java.time.LocalDate;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommonController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "common.security.users[0].username=staff",
        "common.security.users[0].password={bcrypt}$2a$10$I0cfO/nWXAio2tHq/k4j5ejeajLsEzM4F1JEh6p1O2H8blTh.Fi3e",
        "common.security.users[0].roles=STAFF",
        "common.security.users[1].username=admin",
        "common.security.users[1].password={bcrypt}$2a$10$iRqGX4LQYe4DK3Yd8Ab/keEvMTNgq4DxXr/MUNW.FLGDE8KNQyC8i",
        "common.security.users[1].roles=STAFF,ADMIN"
})
class CommonControllerSecurityTest {

    private static final UUID PERSON_ID = UUID.fromString("8a1f3c52-0d1e-4b7a-9c55-3f0b2a6d7e10");
    private static final UUID ACCOUNT_ID = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CommonService commonService;

    @MockBean
    private Logger logger;

    @BeforeEach
    void setUp() {
        PersonRequestDTO person = new PersonRequestDTO();
        person.setId(PERSON_ID);
        person.setFirstName("Bill");
        person.setLastName("Johnson");
        person.setBirthdate(LocalDate.of(1990, 4, 20));
        person.setEmail("bill@example.com");
        person.setPhoneNumber("+1 234 567 89 01");
        AccountRequestDTO account = new AccountRequestDTO();
        account.setId(ACCOUNT_ID);
        account.setPersonId(PERSON_ID);
        account.setName("Main");
        account.setCreationDate(LocalDate.of(2021, 1, 1));
        when(commonService.getPeople()).thenReturn(Collections.singletonList(person));
        when(commonService.getPersonById(PERSON_ID)).thenReturn(person);
        when(commonService.getAccountsByPerson(PERSON_ID)).thenReturn(Collections.singletonList(account));
    }

    @Test
    void anonymousUserIsRedirectedToLoginForPeopleList() throws Exception {
        mvc.perform(get("/common/v1/info/people"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", endsWith("/login")));
        verify(commonService, never()).getPeople();
    }

    @Test
    void anonymousUserCannotViewPersonPii() throws Exception {
        mvc.perform(get("/common/v1/info/people/{id}", PERSON_ID))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", endsWith("/login")));
        verify(commonService, never()).getPersonById(any());
    }

    @Test
    void anonymousUserCannotDeletePersonEvenWithCsrfToken() throws Exception {
        mvc.perform(delete("/common/v1/delete/people/{id}", PERSON_ID).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", endsWith("/login")));
        verify(commonService, never()).deletePerson(any());
    }

    @Test
    void anonymousUserCannotCreatePerson() throws Exception {
        mvc.perform(post("/common/v1/create/people").with(csrf())
                        .param("firstName", "Eve").param("lastName", "Mallory")
                        .param("birthdate", "1990-01-01").param("email", "eve@example.com")
                        .param("phoneNumber", "123"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", endsWith("/login")));
        verify(commonService, never()).createPerson(any());
    }

    @Test
    void unknownPathsAreDenied() throws Exception {
        mvc.perform(get("/actuator/env").with(user("staff").roles("STAFF")))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginPageIsPublic() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void formLoginWithValidCredentialsSucceeds() throws Exception {
        mvc.perform(formLogin().user("staff").password("staff-pass"))
                .andExpect(authenticated().withUsername("staff").withRoles("STAFF"))
                .andExpect(redirectedUrl("/common/v1/info/people"));
    }

    @Test
    void formLoginWithWrongPasswordFails() throws Exception {
        mvc.perform(formLogin().user("staff").password("wrong"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void formLoginWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/login").param("username", "staff").param("password", "staff-pass"))
                .andExpect(status().isForbidden())
                .andExpect(unauthenticated());
    }

    @Test
    void staffCanViewPeopleAndPersonDetails() throws Exception {
        mvc.perform(get("/common/v1/info/people").with(user("staff").roles("STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bill Johnson")))
                .andExpect(content().string(containsString("Signed in as staff")));
        mvc.perform(get("/common/v1/info/people/{id}", PERSON_ID).with(user("staff").roles("STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("bill@example.com")));
    }

    @Test
    void staffDoesNotSeeDeleteButtons() throws Exception {
        MvcResult result = mvc.perform(get("/common/v1/info/people/{id}", PERSON_ID).with(user("staff").roles("STAFF")))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("/common/v1/delete/");
    }

    @Test
    void adminDeleteFormsCarryCsrfTokenAndHiddenMethod() throws Exception {
        MvcResult result = mvc.perform(get("/common/v1/info/people/{id}", PERSON_ID).with(user("admin").roles("STAFF", "ADMIN")))
                .andExpect(status().isOk())
                .andReturn();
        String html = result.getResponse().getContentAsString();
        int deleteForm = html.indexOf("action=\"/common/v1/delete/people/" + PERSON_ID + "\"");
        assertThat(deleteForm).isPositive();
        String deleteFormHtml = html.substring(deleteForm, html.indexOf("</form>", deleteForm));
        assertThat(deleteFormHtml).contains("name=\"_method\" value=\"DELETE\"").contains("name=\"_csrf\"");
    }

    @Test
    void staffCannotDeletePerson() throws Exception {
        mvc.perform(delete("/common/v1/delete/people/{id}", PERSON_ID).with(user("staff").roles("STAFF")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/common/v1/delete/people/{personId}/accounts/{accountId}", PERSON_ID, ACCOUNT_ID)
                        .with(user("staff").roles("STAFF")).with(csrf()))
                .andExpect(status().isForbidden());
        verify(commonService, never()).deletePerson(any());
        verify(commonService, never()).deleteAccount(any(), any());
    }

    @Test
    void adminDeleteWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(delete("/common/v1/delete/people/{id}", PERSON_ID).with(user("admin").roles("STAFF", "ADMIN")))
                .andExpect(status().isForbidden());
        verify(commonService, never()).deletePerson(any());
    }

    @Test
    void crossSiteHiddenMethodFormPostWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/common/v1/delete/people/{id}", PERSON_ID)
                        .param("_method", "DELETE")
                        .with(user("admin").roles("STAFF", "ADMIN")))
                .andExpect(status().isForbidden());
        verify(commonService, never()).deletePerson(any());
    }

    @Test
    void adminCanDeletePersonWithCsrfToken() throws Exception {
        mvc.perform(post("/common/v1/delete/people/{id}", PERSON_ID)
                        .param("_method", "DELETE")
                        .with(user("admin").roles("STAFF", "ADMIN")).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/common/v1/info/people"));
        verify(commonService).deletePerson(PERSON_ID);
    }

    @Test
    void staffUpdateRequiresCsrfToken() throws Exception {
        mvc.perform(put("/common/v1/update/people/{id}", PERSON_ID)
                        .with(user("staff").roles("STAFF"))
                        .param("firstName", "Bill").param("lastName", "Johnson")
                        .param("birthdate", "1990-04-20").param("email", "bill@example.com")
                        .param("phoneNumber", "123"))
                .andExpect(status().isForbidden());
        verify(commonService, never()).updatePerson(any(), any());
    }

    @Test
    void logoutRequiresPostWithCsrfToken() throws Exception {
        mvc.perform(post("/logout").with(user("staff").roles("STAFF")).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?logout"));
    }
}
