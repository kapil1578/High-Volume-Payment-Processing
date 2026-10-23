package com.hvpps.ingestion;

import com.hvpps.ingestion.api.PaymentController;
import com.hvpps.ingestion.core.PaymentIngestionService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentValidationTest {
    @Autowired MockMvc mvc;
    @MockBean PaymentIngestionService service;

    @ParameterizedTest
    @ValueSource(strings = {
        "{}",
        "{\"requestId\":\"r\",\"accountId\":\"a\",\"amount\":0,\"currency\":\"USD\",\"merchantId\":\"m\"}",
        "{\"requestId\":\"r\",\"accountId\":\"a\",\"amount\":12,\"currency\":\"US\",\"merchantId\":\"m\"}",
        "{\"requestId\":\" \",\"accountId\":\"a\",\"amount\":12,\"currency\":\"USD\",\"merchantId\":\"m\"}",
        "not-json"
    })
    void invalidPayloadNeverReachesPublication(String body) throws Exception {
        mvc.perform(post("/api/payments").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
