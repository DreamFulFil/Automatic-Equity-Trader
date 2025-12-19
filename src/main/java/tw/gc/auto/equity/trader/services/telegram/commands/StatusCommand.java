package tw.gc.auto.equity.trader.services.telegram.commands;

import tw.gc.auto.equity.trader.services.telegram.TelegramCommand;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommandContext;

/**
 * Displays bot status including position, P&L, and equity.
 * 
 * <p><b>Why this exists:</b> Primary monitoring command for traders to check system health
 * and current positions. Aggregates data from multiple services to provide a comprehensive
 * snapshot without requiring UI access.</p>
 */
public class StatusCommand implements TelegramCommand {
    
    @Override
    public String getCommandName() {
        return "status";
    }
    
    @Override
    public void execute(String args, TelegramCommandContext context) {
        if (context.getStatusHandler() != null) {
            context.getStatusHandler().accept(null);
        }
    }
    
    @Override
    public String getHelpText() {
        return "/status - Bot status & position";
    }
}
