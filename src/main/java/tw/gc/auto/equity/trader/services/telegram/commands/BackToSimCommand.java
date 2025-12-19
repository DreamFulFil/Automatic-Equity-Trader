package tw.gc.auto.equity.trader.services.telegram.commands;

import tw.gc.auto.equity.trader.services.telegram.TelegramCommand;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommandContext;

/**
 * Switches back to simulation mode from live trading.
 * 
 * <p><b>Why easy downgrade:</b> Traders should be able to retreat to simulation without
 * barriers. This encourages experimentation and provides psychological safety.</p>
 * 
 * <p><b>Design Decision:</b> No confirmation required for sim mode (unlike live mode).
 * Simulation is inherently safer and doesn't risk capital.</p>
 */
public class BackToSimCommand implements TelegramCommand {
    
    @Override
    public String getCommandName() {
        return "backtosim";
    }
    
    @Override
    public void execute(String args, TelegramCommandContext context) {
        if (context.getBotModeService() == null) {
            sendError(context, "⚠️ Bot mode service not initialized");
            return;
        }
        
        if (context.getBotModeService().isSimulationMode()) {
            sendMessage(context, "ℹ️ Already in SIMULATION mode!");
            return;
        }
        
        context.getBotModeService().switchToSimulationMode();
        sendMessage(context, "✅ Switched back to SIMULATION mode\nNo real money at risk.");
    }
    
    @Override
    public String getHelpText() {
        return "/backtosim - Switch to simulation";
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
