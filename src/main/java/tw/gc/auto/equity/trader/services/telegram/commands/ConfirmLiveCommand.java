package tw.gc.auto.equity.trader.services.telegram.commands;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommand;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommandContext;

/**
 * Confirms and executes the switch to live trading mode.
 * 
 * <p><b>Why time-limited confirmation:</b> The 10-minute expiration ensures that eligibility
 * checks are recent. Market conditions or system state might change between check and confirmation.</p>
 * 
 * <p><b>Design Decision:</b> Requires prior /golive execution. Cannot directly switch to live
 * without passing eligibility checks. This is a critical safety mechanism.</p>
 */
@Slf4j
public class ConfirmLiveCommand implements TelegramCommand {
    
    @Override
    public String getCommandName() {
        return "confirmlive";
    }
    
    @Override
    public void execute(String args, TelegramCommandContext context) {
        if (context.getBotModeService() == null) {
            sendError(context, "⚠️ Bot mode service not initialized");
            return;
        }

        if (context.getBotModeService().isLiveMode()) {
            sendMessage(context, "ℹ️ Already in LIVE mode");
            return;
        }

        if (!context.getGoLiveStateManager().isValid()) {
            sendMessage(context, "🔁 Go-live check expired. Run /golive again to verify eligibility.");
            context.getGoLiveStateManager().clearPending();
            return;
        }

        context.getBotModeService().switchToLiveMode();
        context.getGoLiveStateManager().clearPending();
        sendMessage(context, "🟢 LIVE MODE ENABLED\nReal orders will be placed. Use /backtosim to return to simulation.");
    }
    
    @Override
    public String getHelpText() {
        return "/confirmlive - Confirm live switch";
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
