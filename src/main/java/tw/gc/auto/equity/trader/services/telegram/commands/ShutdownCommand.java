package tw.gc.auto.equity.trader.services.telegram.commands;

import tw.gc.auto.equity.trader.services.telegram.TelegramCommand;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommandContext;

/**
 * Gracefully shuts down the application.
 * 
 * <p><b>Why graceful shutdown:</b> Ensures all positions are closed, all logs are flushed,
 * and all database transactions are committed before exit. Prevents orphaned positions
 * or data corruption.</p>
 * 
 * <p><b>Design Decision:</b> Two-phase shutdown: first flatten positions and wait for
 * confirmation, then terminate the application. This provides observability and opportunity
 * to abort if needed.</p>
 */
public class ShutdownCommand implements TelegramCommand {
    
    @Override
    public String getCommandName() {
        return "shutdown";
    }
    
    @Override
    public void execute(String args, TelegramCommandContext context) {
        if (context.getShutdownHandler() != null) {
            context.getShutdownHandler().accept(null);
        }
    }
    
    @Override
    public String getHelpText() {
        return "/shutdown - Stop application";
    }
}
