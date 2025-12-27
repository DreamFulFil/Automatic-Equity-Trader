package tw.gc.auto.equity.trader.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.ContractScalingService;
import tw.gc.auto.equity.trader.RiskManagementService;
import tw.gc.auto.equity.trader.RiskSettingsService;
import tw.gc.auto.equity.trader.ShioajiSettingsService;
import tw.gc.auto.equity.trader.StockSettingsService;
import tw.gc.auto.equity.trader.TelegramService;
import tw.gc.auto.equity.trader.config.TradingProperties;
import tw.gc.auto.equity.trader.repositories.DailyStatisticsRepository;
import tw.gc.auto.equity.trader.services.DataLoggingService;
import tw.gc.auto.equity.trader.services.EndOfDayStatisticsService;
import tw.gc.auto.equity.trader.services.LlmService;

@Component
@Getter
@RequiredArgsConstructor
public class TradingContext {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TelegramService telegramService;
    private final TradingProperties tradingProperties;
    private final ApplicationContext applicationContext;
    private final ContractScalingService contractScalingService;
    private final RiskManagementService riskManagementService;
    private final StockSettingsService stockSettingsService;
    private final RiskSettingsService riskSettingsService;
    private final DataLoggingService dataLoggingService;
    private final EndOfDayStatisticsService endOfDayStatisticsService;
    private final DailyStatisticsRepository dailyStatisticsRepository;
    private final ShioajiSettingsService shioajiSettingsService;
    private final LlmService llmService;
}
