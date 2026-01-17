package tw.gc.auto.equity.trader.services.telegram.commands;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tw.gc.auto.equity.trader.entities.StockSettings;
import tw.gc.auto.equity.trader.services.StockSettingsService;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommandContext;
import tw.gc.auto.equity.trader.services.TelegramService;

import static org.mockito.Mockito.*;

class ChangeStockSettingsCommandTest {
    private ChangeStockSettingsCommand command;
    private TelegramCommandContext context;
    private StockSettingsService stockSettingsService;
    private TelegramService telegramService;
    private StockSettings stockSettings;

    @BeforeEach
    void setUp() {
        command = new ChangeStockSettingsCommand();
        context = mock(TelegramCommandContext.class);
        stockSettingsService = mock(StockSettingsService.class);
        telegramService = mock(TelegramService.class);
        stockSettings = StockSettings.builder().shares(70).shareIncrement(27).build();
        when(context.getStockSettingsService()).thenReturn(stockSettingsService);
        when(context.getTelegramService()).thenReturn(telegramService);
        when(stockSettingsService.getSettings()).thenReturn(stockSettings);
    }

    @Test
    void testChangeShareSuccess() {
        command.getCommandName();
        command.getHelpText();

        command.execute("share 123", context);
        verify(stockSettingsService).updateSettings(123, 27);
        verify(telegramService).sendMessage(contains("Base shares updated to: 123"));
    }

    @Test
    void testChangeShareIncrementSuccess() {
        command.execute("shareIncrement 15", context);
        verify(stockSettingsService).updateSettings(70, 15);
        verify(telegramService).sendMessage(contains("Share increment updated to: 15"));
    }

    @Test
    void testInvalidType() {
        command.execute("invalidtype 10", context);
        verify(telegramService).sendMessage(contains("Type must be 'share' or 'shareIncrement'"));
    }

    @Test
    void testInvalidNumber() {
        command.execute("share abc", context);
        verify(telegramService).sendMessage(contains("Value must be a number"));
    }

    @Test
    void testNegativeValue() {
        command.execute("share -5", context);
        verify(telegramService).sendMessage(contains("Value must be positive"));
    }

    @Test
    void testMissingArgs() {
        command.getCommandName();
        command.getHelpText();

        command.execute("", context);
        verify(telegramService).sendMessage(contains("Usage: /change-stock-settings"));
    }

    @Test
    void testWrongArgCount() {
        command.execute("share", context);
        verify(telegramService).sendMessage(contains("Usage: /change-stock-settings"));
    }

    @Test
    void testExceptionPath() {
        when(stockSettingsService.getSettings()).thenThrow(new RuntimeException("db down"));
        command.execute("share 123", context);
        verify(telegramService).sendMessage(contains("Failed to update stock settings"));
    }

    @Test
    void testNoTelegramService_shouldNotThrow() {
        when(context.getTelegramService()).thenReturn(null);
        command.execute("", context);
    }
}
