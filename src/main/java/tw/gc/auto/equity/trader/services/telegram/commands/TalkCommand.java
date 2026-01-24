package tw.gc.auto.equity.trader.services.telegram.commands;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.agents.TutorBotAgent;
import tw.gc.auto.equity.trader.entities.AgentInteraction.InteractionType;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommand;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommandContext;
import java.util.HashMap;
import java.util.Map;

/**
 * Ask TutorBot a trading-related question.
 * 
 * <p><b>Why rate limiting:</b> Prevents abuse of AI resources and encourages thoughtful questions.
 * Daily quota ensures fair usage across all users.</p>
 * 
 * <p><b>Design Decision:</b> Questions are logged for analytics and model improvement.
 * This helps understand user needs and refine agent responses over time.</p>
 */
@Slf4j
public class TalkCommand implements TelegramCommand {
    
    @Override
    public String getCommandName() {
        return "talk";
    }
    
    @Override
    public void execute(String args, TelegramCommandContext context) {
        if (context.getAgentService() == null) {
            sendError(context, "⚠️ Agent service not initialized");
            return;
        }
        
        String question = args != null ? args.trim() : "";
        
        if (question.isEmpty()) {
            sendMessage(context, "❓ Usage: /talk [your question]\n\nExample: /talk What is momentum trading?");
            return;
        }
        
        sendMessage(context, "🤔 Thinking...");
        
        TutorBotAgent tutor = context.getAgentService().getTutorBot();
        Map<String, Object> input = new HashMap<>();
        input.put("question", question);
        input.put("userId", "telegram");
        input.put("type", InteractionType.QUESTION);
        
        Map<String, Object> result = tutor.safeExecute(input);
        
        if ((boolean) result.getOrDefault("success", false)) {
            String response = (String) result.get("response");
            int remaining = (int) result.getOrDefault("remaining", 0);
            sendMessage(context, String.format("📚 TutorBot:\n\n%s\n\n[%d questions remaining today]", 
                    response, remaining));
        } else {
            sendError(context, "❌ " + result.getOrDefault("error", "TutorBot unavailable"));
        }
    }
    
    @Override
    public String getHelpText() {
        return "/talk [question] - Ask TutorBot";
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
