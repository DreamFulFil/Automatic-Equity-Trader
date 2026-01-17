package tw.gc.auto.equity.trader.services.telegram.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.StockSettings;
import tw.gc.auto.equity.trader.services.StockSettingsService;
import tw.gc.auto.equity.trader.services.TelegramService;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommandContext;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChangeIncrementCommandTest {

    @Mock TelegramService telegramService;
    @Mock StockSettingsService stockSettingsService;

    @Test
    void execute_noArgs_sendsUsage() {
        TelegramCommandContext ctx = TelegramCommandContext.builder()
            .telegramService(telegramService)
            .stockSettingsService(stockSettingsService)
            .build();

        new ChangeIncrementCommand().execute(" ", ctx);

        verify(telegramService).sendMessage(contains("Usage: /change-increment"));
        verify(stockSettingsService, never()).updateSettings(anyInt(), anyInt());
    }

    @Test
    void execute_invalidNumber_sendsError() {
        TelegramCommandContext ctx = TelegramCommandContext.builder()
            .telegramService(telegramService)
            .stockSettingsService(stockSettingsService)
            .build();

        new ChangeIncrementCommand().execute("abc", ctx);

        verify(telegramService).sendMessage(contains("Invalid number"));
    }

    @Test
    void execute_negative_sendsError() {
        TelegramCommandContext ctx = TelegramCommandContext.builder()
            .telegramService(telegramService)
            .stockSettingsService(stockSettingsService)
            .build();

        new ChangeIncrementCommand().execute("-1", ctx);

        verify(telegramService).sendMessage(contains("Increment must be positive"));
    }

    @Test
    void execute_success_updatesSettingsAndSendsConfirmation() {
        when(stockSettingsService.getSettings()).thenReturn(StockSettings.builder().shares(100).shareIncrement(10).build());

        TelegramCommandContext ctx = TelegramCommandContext.builder()
            .telegramService(telegramService)
            .stockSettingsService(stockSettingsService)
            .build();

        new ChangeIncrementCommand().execute("50", ctx);

        verify(stockSettingsService).updateSettings(100, 50);
        verify(telegramService).sendMessage(contains("Share increment updated"));
    }

    @Test
    void execute_updateThrows_sendsError() {
        when(stockSettingsService.getSettings()).thenReturn(StockSettings.builder().shares(100).shareIncrement(10).build());
        doThrow(new RuntimeException("boom")).when(stockSettingsService).updateSettings(anyInt(), anyInt());

        TelegramCommandContext ctx = TelegramCommandContext.builder()
            .telegramService(telegramService)
            .stockSettingsService(stockSettingsService)
            .build();

        new ChangeIncrementCommand().execute("50", ctx);

        verify(telegramService).sendMessage(contains("Failed to update increment"));
    }
}
