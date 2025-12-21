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
public class ChangeStockSettingsCommand implements TelegramCommand {
    
    @Override
    public String getCommandName() {
        return "change-stock-settings";
    }
    
    @Override
    public void execute(String args, TelegramCommandContext context) {
        if (args == null || args.trim().isEmpty()) {
            sendError(context, "❌ Usage: /change-stock-settings <type> <number>\nExample: /change-stock-settings share 100\nExample: /change-stock-settings shareIncrement 10");
            return;
        }
        String[] parts = args.trim().split(" ");
        if (parts.length != 2) {
            sendError(context, "❌ Usage: /change-stock-settings <type> <number>");
            return;
        }
        String type = parts[0];
        int value;
        try {
            value = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            sendError(context, "❌ Value must be a number");
            return;
        }
        if (value <= 0) {
            sendError(context, "❌ Value must be positive");
            return;
        }
        try {
            StockSettings settings = context.getStockSettingsService().getSettings();
            if ("share".equalsIgnoreCase(type)) {
                context.getStockSettingsService().updateSettings(value, settings.getShareIncrement());
                sendMessage(context, "✅ Base shares updated to: " + value);
                log.info("📝 Base shares updated to {} via Telegram", value);
            } else if ("shareIncrement".equalsIgnoreCase(type)) {
                context.getStockSettingsService().updateSettings(settings.getShares(), value);
                sendMessage(context, "✅ Share increment updated to: " + value);
                log.info("📝 Share increment updated to {} via Telegram", value);
            } else {
                sendError(context, "❌ Type must be 'share' or 'shareIncrement'");
            }
        } catch (Exception e) {
            log.error("Failed to update stock settings", e);
            sendError(context, "❌ Failed to update stock settings");
        }
    }
    
    @Override
    public String getHelpText() {
        return "/change-stock-settings <type> <number> - Change stock settings. Type: share or shareIncrement";
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
