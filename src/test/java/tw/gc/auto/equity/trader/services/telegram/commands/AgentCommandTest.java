package tw.gc.auto.equity.trader.services.telegram.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.services.AgentService;
import tw.gc.auto.equity.trader.services.TelegramService;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommandContext;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentCommandTest {

    @Mock TelegramService telegramService;
    @Mock AgentService agentService;

    @Test
    void execute_agentServiceNull_sendsError() {
        TelegramCommandContext ctx = TelegramCommandContext.builder()
            .telegramService(telegramService)
            .agentService(null)
            .build();

        new AgentCommand().execute(null, ctx);

        verify(telegramService).sendMessage(contains("Agent service not initialized"));
    }

    @Test
    void execute_sendsAgentListMessage() {
        when(agentService.getAgentListMessage()).thenReturn("agents...");

        TelegramCommandContext ctx = TelegramCommandContext.builder()
            .telegramService(telegramService)
            .agentService(agentService)
            .build();

        new AgentCommand().execute(null, ctx);

        verify(telegramService).sendMessage("agents...");
    }
}
