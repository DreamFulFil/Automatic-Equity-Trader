package tw.gc.auto.equity.trader.services.telegram.commands;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.StockSettings;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommand;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommandContext;

/**
 * Changes share increment per 20k equity for stock trading.
 * 
 * <p><b>Why equity-based scaling:</b> Implements dynamic position sizing based on account equity.
 * As equity grows, position sizes increase proportionally, maintaining consistent risk exposure.</p>
 * 
 * <p><b>Design Decision:</b> 20k TWD increments are Taiwan-specific, chosen to align with
 * typical retail account sizes and margin requirements.</p>
 */
@Slf4j
public class ChangeIncrementCommand implements TelegramCommand {
    
    @Override
    public String getCommandName() {
        return "change-increment";
    }
    
    @Override
    public void execute(String args, TelegramCommandContext context) {
        if (args == null || args.trim().isEmpty()) {
            sendError(context, "❌ Usage: /change-increment [number]\nExample: /change-increment 100");
            return;
        }
        
        try {
            int increment = Integer.parseInt(args.trim());
            if (increment <= 0) {
                sendError(context, "❌ Increment must be positive");
                return;
            }
            
            StockSettings settings = context.getStockSettingsService().getSettings();
            context.getStockSettingsService().updateSettings(settings.getShares(), increment);
            sendMessage(context, "✅ Share increment updated to: " + increment);
            log.info("📝 Share increment updated to {} via Telegram", increment);
            
        } catch (NumberFormatException e) {
            sendError(context, "❌ Invalid number: " + args);
        } catch (Exception e) {
            log.error("Failed to update increment", e);
            sendError(context, "❌ Failed to update increment");
        }
    }
    
    @Override
    public String getHelpText() {
        return "/change-increment [number] - Change share increment";
    }
    
    private void sendMessage(TelegramCommandContext context, String message) {
        if (context.getTelegramService() != null) {
            context.getTelegramService().sendMessage(message);
        }
    }
    
    private void sendError(TelegramCommandContext context, String message) {
        sendMessage(context, message);
    }
}
