package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.config.TradingProperties;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractScalingServiceCoveragePatchTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private TelegramService telegramService;
    @Mock
    private TradingStateService tradingStateService;

    private ContractScalingService service;

    @BeforeEach
    void setUp() {
        service = new ContractScalingService(restTemplate, objectMapper, telegramService, new TradingProperties(), tradingStateService);
    }

    @Test
    void updateContractSizing_handlesBridgeFailure() throws Exception {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenThrow(new RuntimeException("fail"));

        service.updateContractSizing();
        verify(telegramService).sendMessage(contains("Contract sizing failed"));
    }
}
