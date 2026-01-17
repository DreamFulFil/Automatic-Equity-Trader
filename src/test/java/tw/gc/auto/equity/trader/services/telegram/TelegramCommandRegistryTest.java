package tw.gc.auto.equity.trader.services.telegram;

import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class TelegramCommandRegistryTest {

    @Test
    void registerCommands_shouldRegisterBuiltins_andSupportLookups() {
        TelegramCommandRegistry registry = new TelegramCommandRegistry();

        registry.registerCommands();

        assertTrue(registry.getCommandCount() > 0);
        assertTrue(registry.hasCommand("status"));
        assertTrue(registry.hasCommand("STATUS"));
        assertNotNull(registry.getCommand("status"));
        assertNotNull(registry.getCommand("STATUS"));

        Collection<TelegramCommand> all = registry.getAllCommands();
        assertEquals(registry.getCommandCount(), all.size());
        assertThrows(UnsupportedOperationException.class, () -> all.clear());
    }

    @Test
    void register_shouldOverwriteWhenSameNameDifferentCase() {
        TelegramCommandRegistry registry = new TelegramCommandRegistry();

        TelegramCommand c1 = new TelegramCommand() {
            @Override public String getCommandName() { return "HeLp"; }
            @Override public void execute(String args, TelegramCommandContext context) { }
            @Override public String getHelpText() { return "h"; }
        };

        TelegramCommand c2 = new TelegramCommand() {
            @Override public String getCommandName() { return "help"; }
            @Override public void execute(String args, TelegramCommandContext context) { }
            @Override public String getHelpText() { return "h2"; }
        };

        registry.register(c1);
        assertEquals(1, registry.getCommandCount());

        registry.register(c2);
        assertEquals(1, registry.getCommandCount());
        assertSame(c2, registry.getCommand("HELP"));
    }
}
