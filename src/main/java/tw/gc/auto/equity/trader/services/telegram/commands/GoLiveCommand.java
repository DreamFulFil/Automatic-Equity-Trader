package tw.gc.auto.equity.trader.services.telegram.commands;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.agents.RiskManagerAgent;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommand;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommandContext;
import java.util.Map;

/**
 * Checks eligibility for switching to live trading mode.
 * 
 * <p><b>Why strict eligibility:</b> Prevents premature live trading that could result in losses.
 * Requires minimum track record (win rate, drawdown, trade count) before risking real capital.</p>
 * 
 * <p><b>Design Decision:</b> Two-step confirmation process (check, then confirm) creates
 * a deliberate friction point. This is intentional to prevent accidental live switches.</p>
 */
@Slf4j
public class GoLiveCommand implements TelegramCommand {
    
    @Override
    public String getCommandName() {
        return "golive";
    }
    
    @Override
    public void execute(String args, TelegramCommandContext context) {
        if (context.getAgentService() == null || context.getBotModeService() == null) {
            sendError(context, "⚠️ Services not initialized");
            return;
        }
        
        if (context.getBotModeService().isLiveMode()) {
            sendMessage(context, "ℹ️ Already in LIVE mode!\nUse /backtosim to switch back to simulation.");
            return;
        }
        
        RiskManagerAgent riskManager = context.getAgentService().getRiskManager();
        Map<String, Object> result = riskManager.checkGoLiveEligibility();
        
        boolean eligible = (boolean) result.getOrDefault("eligible", false);
        
        StringBuilder sb = new StringBuilder();
        sb.append("🎯 GO-LIVE ELIGIBILITY CHECK\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n\n");
        sb.append(String.format("Total Trades: %d\n", result.get("total_trades")));
        sb.append(String.format("Win Rate: %s %s\n", result.get("win_rate"), 
                (boolean) result.get("win_rate_ok") ? "✅" : "❌"));
        sb.append(String.format("Max Drawdown: %s %s\n", result.get("max_drawdown"),
                (boolean) result.get("drawdown_ok") ? "✅" : "❌"));
        sb.append(String.format("Has Enough Trades: %s\n\n", 
                (boolean) result.get("has_enough_trades") ? "✅" : "❌"));
        sb.append(String.format("Requirements: %s\n\n", result.get("requirements")));
        
        if (eligible) {
            sb.append("🟢 ELIGIBLE FOR LIVE TRADING!\n");
            sb.append("⚠️ Type /confirmlive within 10 minutes to switch (real money at risk!)");
            context.getGoLiveStateManager().markPending();
        } else {
            sb.append("🔴 NOT YET ELIGIBLE\n");
            sb.append("Keep trading in simulation to build your track record.");
            context.getGoLiveStateManager().clearPending();
        }
        
        sendMessage(context, sb.toString());
    }
    
    @Override
    public String getHelpText() {
        return "/golive - Check live eligibility";
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
