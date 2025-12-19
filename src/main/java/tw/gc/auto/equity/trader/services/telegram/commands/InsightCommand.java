package tw.gc.auto.equity.trader.services.telegram.commands;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.agents.TutorBotAgent;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommand;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommandContext;
import java.util.Map;

/**
 * Generates daily trading insight from TutorBot.
 * 
 * <p><b>Why daily insights:</b> Provides automated market context and educational content.
 * Helps traders stay informed without manual research.</p>
 * 
 * <p><b>Design Decision:</b> Separate from /talk to allow scheduled delivery.
 * Insights are proactive (generated on schedule) while /talk is reactive (user-initiated).</p>
 */
@Slf4j
public class InsightCommand implements TelegramCommand {
    
    @Override
    public String getCommandName() {
        return "insight";
    }
    
    @Override
    public void execute(String args, TelegramCommandContext context) {
        if (context.getAgentService() == null) {
            sendError(context, "⚠️ Agent service not initialized");
            return;
        }
        
        sendMessage(context, "💡 Generating insight...");
        
        TutorBotAgent tutor = context.getAgentService().getTutorBot();
        Map<String, Object> result = tutor.generateDailyInsight("telegram");
        
        if ((boolean) result.getOrDefault("success", false)) {
            String response = (String) result.get("response");
            int remaining = (int) result.getOrDefault("remaining", 0);
            sendMessage(context, String.format("💡 Daily Insight:\n\n%s\n\n[%d insights remaining today]", 
                    response, remaining));
        } else {
            sendError(context, "❌ " + result.getOrDefault("error", "Could not generate insight"));
        }
    }
    
    @Override
    public String getHelpText() {
        return "/insight - Daily insight";
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
