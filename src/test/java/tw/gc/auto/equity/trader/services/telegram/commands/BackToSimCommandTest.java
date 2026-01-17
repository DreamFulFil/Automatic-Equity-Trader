package tw.gc.auto.equity.trader.services.telegram.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.services.BotModeService;
import tw.gc.auto.equity.trader.services.TelegramService;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommandContext;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackToSimCommandTest {

    @Mock TelegramService telegramService;
    @Mock BotModeService botModeService;

    @Test
    void execute_botModeServiceNull_sendsError() {
        TelegramCommandContext ctx = TelegramCommandContext.builder()
            .telegramService(telegramService)
            .botModeService(null)
            .build();

        new BackToSimCommand().execute(null, ctx);

        verify(telegramService).sendMessage(contains("Bot mode service not initialized"));
    }

    @Test
    void execute_alreadySimulation_sendsInfo() {
        when(botModeService.isSimulationMode()).thenReturn(true);
        TelegramCommandContext ctx = TelegramCommandContext.builder()
            .telegramService(telegramService)
            .botModeService(botModeService)
            .build();

        new BackToSimCommand().execute(null, ctx);

        verify(telegramService).sendMessage(contains("Already in SIMULATION"));
        verify(botModeService, never()).switchToSimulationMode();
    }

    @Test
    void execute_switchesToSimulation_sendsConfirmation() {
        when(botModeService.isSimulationMode()).thenReturn(false);
        TelegramCommandContext ctx = TelegramCommandContext.builder()
            .telegramService(telegramService)
            .botModeService(botModeService)
            .build();

        new BackToSimCommand().execute(null, ctx);

        verify(botModeService).switchToSimulationMode();
        verify(telegramService).sendMessage(contains("Switched back to SIMULATION"));
    }
}
