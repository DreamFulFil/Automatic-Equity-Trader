package tw.gc.auto.equity.trader.services.telegram.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.agents.RiskManagerAgent;
import tw.gc.auto.equity.trader.services.AgentService;
import tw.gc.auto.equity.trader.services.BotModeService;
import tw.gc.auto.equity.trader.services.TelegramService;
import tw.gc.auto.equity.trader.services.telegram.GoLiveStateManager;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommandContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoLiveCommandTest {

    @Mock TelegramService telegramService;
    @Mock AgentService agentService;
    @Mock BotModeService botModeService;
    @Mock RiskManagerAgent riskManagerAgent;

    @Test
    void execute_servicesNotInitialized_sendsError() {
        TelegramCommandContext ctx = TelegramCommandContext.builder()
            .telegramService(telegramService)
            .agentService(null)
            .botModeService(null)
            .goLiveStateManager(new GoLiveStateManager())
            .build();

        new GoLiveCommand().execute(null, ctx);

        verify(telegramService).sendMessage(contains("Services not initialized"));
    }

    @Test
    void execute_alreadyLive_sendsInfo() {
        when(botModeService.isLiveMode()).thenReturn(true);

        TelegramCommandContext ctx = TelegramCommandContext.builder()
            .telegramService(telegramService)
            .agentService(agentService)
            .botModeService(botModeService)
            .goLiveStateManager(new GoLiveStateManager())
            .build();

        new GoLiveCommand().execute(null, ctx);

        verify(telegramService).sendMessage(contains("Already in LIVE mode"));
    }

    @Test
    void execute_eligible_marksPendingAndSendsMessage() {
        when(botModeService.isLiveMode()).thenReturn(false);
        when(agentService.getRiskManager()).thenReturn(riskManagerAgent);

        when(riskManagerAgent.checkGoLiveEligibility()).thenReturn(Map.of(
            "eligible", true,
            "total_trades", 100,
            "win_rate", "60%",
            "win_rate_ok", true,
            "max_drawdown", "5%",
            "drawdown_ok", true,
            "has_enough_trades", true,
            "requirements", "ok"
        ));

        GoLiveStateManager state = new GoLiveStateManager();
        TelegramCommandContext ctx = TelegramCommandContext.builder()
            .telegramService(telegramService)
            .agentService(agentService)
            .botModeService(botModeService)
            .goLiveStateManager(state)
            .build();

        new GoLiveCommand().execute(null, ctx);

        verify(telegramService).sendMessage(contains("ELIGIBLE FOR LIVE TRADING"));
        assertThat(state.isGoLivePending()).isTrue();
    }

    @Test
    void execute_notEligible_clearsPendingAndSendsMessage() {
        when(botModeService.isLiveMode()).thenReturn(false);
        when(agentService.getRiskManager()).thenReturn(riskManagerAgent);

        when(riskManagerAgent.checkGoLiveEligibility()).thenReturn(Map.of(
            "eligible", false,
            "total_trades", 1,
            "win_rate", "0%",
            "win_rate_ok", false,
            "max_drawdown", "50%",
            "drawdown_ok", false,
            "has_enough_trades", false,
            "requirements", "need more"
        ));

        GoLiveStateManager state = new GoLiveStateManager();
        state.markPending();

        TelegramCommandContext ctx = TelegramCommandContext.builder()
            .telegramService(telegramService)
            .agentService(agentService)
            .botModeService(botModeService)
            .goLiveStateManager(state)
            .build();

        new GoLiveCommand().execute(null, ctx);

        verify(telegramService).sendMessage(contains("NOT YET ELIGIBLE"));
        assertThat(state.isGoLivePending()).isFalse();
    }
}
