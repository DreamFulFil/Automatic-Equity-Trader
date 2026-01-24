package tw.gc.auto.equity.trader.services.telegram.commands;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommand;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommandContext;

/**
 * Displays help information for all available commands.
 * 
 * <p><b>Why centralized help:</b> Self-documenting interface that guides users through
 * available functionality. Essential for command-line interfaces where discoverability
 * is a challenge.</p>
 */
@Slf4j
public class HelpCommand implements TelegramCommand {
    
    @Override
    public String getCommandName() {
        return "help";
    }
    
    @Override
    public void execute(String args, TelegramCommandContext context) {
        StringBuilder help = new StringBuilder();
        help.append("📖 AVAILABLE COMMANDS\n");
        help.append("━━━━━━━━━━━━━━━━━━━━\n\n");

        if (context.getTelegramService() != null) {
            var service = context.getTelegramService();
            var registryLines = service.getRegistryHelpLines();
            if (!registryLines.isEmpty()) {
                help.append("✅ BUILT-IN COMMANDS:\n");
                for (String line : registryLines) {
                    help.append(line).append("\n");
                }
                help.append("\n");
            }

            var customNames = service.getCustomCommandNames();
            if (!customNames.isEmpty()) {
                help.append("🧰 LEGACY COMMANDS:\n");
                for (String cmd : customNames) {
                    help.append("/").append(cmd).append("\n");
                }
            }
        }
        
        if (context.getTelegramService() != null) {
            context.getTelegramService().sendMessage(help.toString());
        }
    }
    
    @Override
    public String getHelpText() {
        return "/help - Show all commands";
    }
}
