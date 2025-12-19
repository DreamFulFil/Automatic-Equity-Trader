package tw.gc.auto.equity.trader.services.telegram.commands;

import tw.gc.auto.equity.trader.services.telegram.TelegramCommand;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommandContext;

/**
 * Pauses trading by preventing new position entries.
 * 
 * <p><b>Why separate from shutdown:</b> Pause is a soft stop that maintains system operation
 * and existing positions. It's a risk control mechanism for uncertain market conditions
 * or when the trader wants manual control.</p>
 * 
 * <p><b>Design Decision:</b> Existing positions continue to be managed (stop-loss, take-profit)
 * even when paused. Only new entry signals are blocked. This prevents abandoning active risk.</p>
 */
public class PauseCommand implements TelegramCommand {
    
    @Override
    public String getCommandName() {
        return "pause";
    }
    
    @Override
    public void execute(String args, TelegramCommandContext context) {
        if (context.getPauseHandler() != null) {
            context.getPauseHandler().accept(null);
        }
    }
    
    @Override
    public String getHelpText() {
        return "/pause - Pause trading";
    }
}
