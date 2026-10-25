package ru.kondrashov.commonservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import ru.kondrashov.commonservice.controllers.dto.TransferResponseDTO;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class CommonServiceImplTransferTest {

    private static final String TRANSFER_URL = "http://localhost:8081/v1/people/%s/accounts/%s/bills/%s/transfer/%s";

    private MockRestServiceServer server;
    private CommonServiceImpl commonService;

    private final UUID personId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID sourceBillId = UUID.randomUUID();
    private final UUID beneficiaryBillId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        commonService = new CommonServiceImpl(restTemplate, LogManager.getLogger(), new ObjectMapper());
        ReflectionTestUtils.setField(commonService, "transferUrl", TRANSFER_URL);
    }

    @Test
    void createTransferPutsToAccountServiceTransferEndpoint() {
        server.expect(requestTo(String.format(TRANSFER_URL, personId, accountId, sourceBillId, beneficiaryBillId)))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.amount").value(25.5))
                .andExpect(jsonPath("$.message").value("Rent"))
                .andExpect(jsonPath("$.beneficiaryBillId").doesNotExist())
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        commonService.createTransfer(personId, accountId, sourceBillId, transfer());

        server.verify();
    }

    @Test
    void accountServiceErrorBecomesTransferRejectedWithItsMessage() {
        server.expect(requestTo(String.format(TRANSFER_URL, personId, accountId, sourceBillId, beneficiaryBillId)))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Not enough money for payment on bill with ID: x\",\"httpCode\":400,\"exception\":\"NotEnoughMoneyException\"}"));

        TransferRejectedException e = catchThrowableOfType(
                () -> commonService.createTransfer(personId, accountId, sourceBillId, transfer()), TransferRejectedException.class);

        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getMessage()).isEqualTo("Not enough money for payment on bill with ID: x");
    }

    @Test
    void errorWithoutJsonBodyFallsBackToStatus() {
        server.expect(requestTo(String.format(TRANSFER_URL, personId, accountId, sourceBillId, beneficiaryBillId)))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("down"));

        TransferRejectedException e = catchThrowableOfType(
                () -> commonService.createTransfer(personId, accountId, sourceBillId, transfer()), TransferRejectedException.class);

        assertThat(e.getMessage()).isEqualTo("Account service responded with 503 SERVICE_UNAVAILABLE");
    }

    private TransferResponseDTO transfer() {
        TransferResponseDTO transfer = new TransferResponseDTO();
        transfer.setCurrency(Currency.getInstance("USD"));
        transfer.setAmount(new BigDecimal("25.50"));
        transfer.setMessage("Rent");
        transfer.setBeneficiaryBillId(beneficiaryBillId);
        return transfer;
    }
}
