package tw.gc.auto.equity.trader.services.telegram.commands;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.StockSettings;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommand;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommandContext;

/**
 * Changes base share quantity for stock trading.
 * 
 * <p><b>Why dynamic configuration:</b> Allows position sizing adjustments without code changes
 * or restarts. Traders can adapt to changing account size or risk tolerance on the fly.</p>
 * 
 * <p><b>Design Decision:</b> Persists to database immediately. Configuration is durable
 * and survives application restarts.</p>
 */
@Slf4j
public class ChangeShareCommand implements TelegramCommand {
    
    @Override
    public String getCommandName() {
        return "change-share";
    }
    
    @Override
    public void execute(String args, TelegramCommandContext context) {
        if (args == null || args.trim().isEmpty()) {
            sendError(context, "❌ Usage: /change-share <number>\nExample: /change-share 1000");
            return;
        }
        
        try {
            int shares = Integer.parseInt(args.trim());
            if (shares <= 0) {
                sendError(context, "❌ Shares must be positive");
                return;
            }
            
            StockSettings settings = context.getStockSettingsService().getSettings();
            context.getStockSettingsService().updateSettings(shares, settings.getShareIncrement());
            sendMessage(context, "✅ Base shares updated to: " + shares);
            log.info("📝 Base shares updated to {} via Telegram", shares);
            
        } catch (NumberFormatException e) {
            sendError(context, "❌ Invalid number: " + args);
        } catch (Exception e) {
            log.error("Failed to update shares", e);
            sendError(context, "❌ Failed to update shares");
        }
    }
    
    @Override
    public String getHelpText() {
        return "/change-share <number> - Change base shares";
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
