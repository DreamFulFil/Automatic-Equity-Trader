package tw.gc.auto.equity.trader.services.telegram.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.services.BotModeService;
import tw.gc.auto.equity.trader.services.TelegramService;
import tw.gc.auto.equity.trader.services.telegram.GoLiveStateManager;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommandContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfirmLiveCommandTest {

    @Mock TelegramService telegramService;
    @Mock BotModeService botModeService;

    @Test
    void execute_botModeServiceNull_sendsError() {
        TelegramCommandContext ctx = TelegramCommandContext.builder()
            .telegramService(telegramService)
            .botModeService(null)
            .goLiveStateManager(new GoLiveStateManager())
            .build();

        new ConfirmLiveCommand().execute(null, ctx);

        verify(telegramService).sendMessage(contains("Bot mode service not initialized"));
    }

    @Test
    void execute_alreadyLive_sendsInfo() {
        when(botModeService.isLiveMode()).thenReturn(true);

        TelegramCommandContext ctx = TelegramCommandContext.builder()
            .telegramService(telegramService)
            .botModeService(botModeService)
            .goLiveStateManager(new GoLiveStateManager())
            .build();

        new ConfirmLiveCommand().execute(null, ctx);

        verify(telegramService).sendMessage(contains("Already in LIVE mode"));
    }

    @Test
    void execute_expired_sendsMessageAndClearsPending() {
        when(botModeService.isLiveMode()).thenReturn(false);

        GoLiveStateManager state = new GoLiveStateManager();
        TelegramCommandContext ctx = TelegramCommandContext.builder()
            .telegramService(telegramService)
            .botModeService(botModeService)
            .goLiveStateManager(state)
            .build();

        state.markPending();
        // force invalid
        state.clearPending();

        new ConfirmLiveCommand().execute(null, ctx);

        verify(telegramService).sendMessage(contains("Go-live check expired"));
        assertThat(state.isGoLivePending()).isFalse();
    }

    @Test
    void execute_valid_switchesAndSendsMessage() {
        when(botModeService.isLiveMode()).thenReturn(false);

        GoLiveStateManager state = new GoLiveStateManager();
        state.markPending();

        TelegramCommandContext ctx = TelegramCommandContext.builder()
            .telegramService(telegramService)
            .botModeService(botModeService)
            .goLiveStateManager(state)
            .build();

        new ConfirmLiveCommand().execute(null, ctx);

        verify(botModeService).switchToLiveMode();
        verify(telegramService).sendMessage(contains("LIVE MODE ENABLED"));
        assertThat(state.isGoLivePending()).isFalse();
    }
}
