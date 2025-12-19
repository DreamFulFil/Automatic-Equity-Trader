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
        
        help.append("📊 TRADING CONTROL:\n");
        help.append("/status - Bot status & position\n");
        help.append("/pause - Pause trading\n");
        help.append("/resume - Resume trading\n");
        help.append("/close - Close position\n");
        help.append("/shutdown - Stop application\n\n");
        
        help.append("🤖 AI AGENTS:\n");
        help.append("/agent - List agents\n");
        help.append("/talk <question> - Ask TutorBot\n");
        help.append("/insight - Daily insight\n\n");
        
        help.append("🟢 LIVE MODE:\n");
        help.append("/golive - Check live eligibility\n");
        help.append("/confirmlive - Confirm live switch\n");
        help.append("/backtosim - Switch to simulation\n\n");
        
        help.append("⚙️ CONFIGURATION:\n");
        help.append("/change-share <number> - Change base shares\n");
        help.append("/change-increment <number> - Change share increment\n");
        
        if (context.getTelegramService() != null) {
            context.getTelegramService().sendMessage(help.toString());
        }
    }
    
    @Override
    public String getHelpText() {
        return "/help - Show all commands";
    }
}
