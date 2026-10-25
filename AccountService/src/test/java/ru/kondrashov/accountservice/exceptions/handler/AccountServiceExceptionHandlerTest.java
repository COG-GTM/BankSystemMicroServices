package ru.kondrashov.accountservice.exceptions.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.kondrashov.accountservice.exceptions.AccountNotFoundException;
import ru.kondrashov.accountservice.exceptions.BillNotFoundException;
import ru.kondrashov.accountservice.exceptions.NotEnoughMoneyException;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountServiceExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new AccountServiceExceptionHandler())
                .build();
    }

    @Test
    void accountNotFound_bodyMatchesHttpStatusAndExceptionClass() throws Exception {
        mockMvc.perform(get("/account"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"))
                .andExpect(jsonPath("$.httpCode").value(404))
                .andExpect(jsonPath("$.exception").value(AccountNotFoundException.class.getSimpleName()));
    }

    @Test
    void billNotFound_bodyMatchesHttpStatusAndExceptionClass() throws Exception {
        mockMvc.perform(get("/bill"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Bill not found"))
                .andExpect(jsonPath("$.httpCode").value(404))
                .andExpect(jsonPath("$.exception").value(BillNotFoundException.class.getSimpleName()));
    }

    @Test
    void notEnoughMoney_bodyMatchesHttpStatusAndExceptionClass() throws Exception {
        mockMvc.perform(get("/money"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Not enough money"))
                .andExpect(jsonPath("$.httpCode").value(400))
                .andExpect(jsonPath("$.exception").value(NotEnoughMoneyException.class.getSimpleName()));
    }

    @Test
    void validationFailure_returnsSameTemplateWithFieldErrors() throws Exception {
        mockMvc.perform(post("/validated")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("name: must not be blank"))
                .andExpect(jsonPath("$.httpCode").value(400))
                .andExpect(jsonPath("$.exception").value("MethodArgumentNotValidException"));
    }

    @Test
    void unreadableBody_returnsSameTemplateWithoutParserDetails() throws Exception {
        mockMvc.perform(post("/validated")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bad Request"))
                .andExpect(jsonPath("$.httpCode").value(400))
                .andExpect(jsonPath("$.exception").value("HttpMessageNotReadableException"));
    }

    @Test
    void unsupportedMethod_returnsSameTemplate() throws Exception {
        mockMvc.perform(post("/account"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.httpCode").value(405))
                .andExpect(jsonPath("$.exception").value("HttpRequestMethodNotSupportedException"));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/account")
        void account() {
            throw new AccountNotFoundException("Account not found");
        }

        @GetMapping("/bill")
        void bill() {
            throw new BillNotFoundException("Bill not found");
        }

        @GetMapping("/money")
        void money() {
            throw new NotEnoughMoneyException("Not enough money");
        }

        @PostMapping("/validated")
        void validated(@RequestBody @Valid Payload payload) {
        }
    }

    static class Payload {

        @NotBlank
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
