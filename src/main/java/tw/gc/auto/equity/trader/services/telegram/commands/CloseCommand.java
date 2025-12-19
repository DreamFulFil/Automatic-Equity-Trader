package tw.gc.auto.equity.trader.services.telegram.commands;

import tw.gc.auto.equity.trader.services.telegram.TelegramCommand;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommandContext;

/**
 * Immediately flattens all positions.
 * 
 * <p><b>Why emergency flatten:</b> Provides manual override for traders to exit positions
 * during unexpected market events or strategy malfunctions. This is a panic button that
 * prioritizes capital preservation over strategy optimization.</p>
 * 
 * <p><b>Design Decision:</b> Uses market orders for immediate execution rather than limit orders.
 * Accepts slippage cost in exchange for certainty of exit.</p>
 */
public class CloseCommand implements TelegramCommand {
    
    @Override
    public String getCommandName() {
        return "close";
    }
    
    @Override
    public void execute(String args, TelegramCommandContext context) {
        if (context.getCloseHandler() != null) {
            context.getCloseHandler().accept(null);
        }
    }
    
    @Override
    public String getHelpText() {
        return "/close - Close position";
    }
}
