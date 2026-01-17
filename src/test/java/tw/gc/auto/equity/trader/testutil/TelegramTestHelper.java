package tw.gc.auto.equity.trader.testutil;

import org.mockito.ArgumentCaptor;
import tw.gc.auto.equity.trader.services.TelegramService;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * Test utility for Telegram bot command handler testing.
 * Provides helpers for capturing command handlers and verifying messages.
 */
public class TelegramTestHelper {

    /**
     * Capture a command handler registered with TelegramService.
     * This allows invoking the handler directly in tests.
     *
     * @param service the TelegramService mock
     * @param command the command name (e.g., "/backtest")
     * @return the captured Consumer that handles the command
     */
    @SuppressWarnings("unchecked")
    public static Consumer<String> captureCommandHandler(TelegramService service, String command) {
        ArgumentCaptor<String> commandCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Consumer<String>> handlerCaptor = ArgumentCaptor.forClass(Consumer.class);
        
        verify(service, atLeastOnce()).registerCustomCommand(commandCaptor.capture(), handlerCaptor.capture());
        
        List<String> commands = commandCaptor.getAllValues();
        List<Consumer<String>> handlers = handlerCaptor.getAllValues();
        
        for (int i = 0; i < commands.size(); i++) {
            if (commands.get(i).equals(command)) {
                return handlers.get(i);
            }
        }
        
        throw new AssertionError("Command handler not found for: " + command);
    }

    /**
     * Verify that a specific message was sent via TelegramService.
     *
     * @param service the TelegramService mock
     * @param expectedContent the exact message content expected
     */
    public static void verifyMessageSent(TelegramService service, String expectedContent) {
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(service, atLeastOnce()).sendMessage(messageCaptor.capture());
        
        List<String> messages = messageCaptor.getAllValues();
        assertThat(messages)
            .as("Expected message to be sent")
            .anyMatch(msg -> msg.equals(expectedContent));
    }

    /**
     * Verify that a message containing all specified keywords was sent.
     *
     * @param service the TelegramService mock
     * @param keywords keywords that must all appear in at least one message
     */
    public static void verifyMessageContains(TelegramService service, String... keywords) {
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(service, atLeastOnce()).sendMessage(messageCaptor.capture());
        
        List<String> messages = messageCaptor.getAllValues();
        String allMessages = String.join(" ", messages);
        
        for (String keyword : keywords) {
            assertThat(allMessages)
                .as("Expected message to contain: " + keyword)
                .contains(keyword);
        }
    }

    /**
     * Verify that at least one message was sent.
     *
     * @param service the TelegramService mock
     */
    public static void verifyMessageSentAtLeastOnce(TelegramService service) {
        verify(service, atLeastOnce()).sendMessage(anyString());
    }

    /**
     * Get all messages sent to TelegramService.
     *
     * @param service the TelegramService mock
     * @return list of all captured messages
     */
    public static List<String> captureAllMessages(TelegramService service) {
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(service, atLeastOnce()).sendMessage(messageCaptor.capture());
        return messageCaptor.getAllValues();
    }
}
