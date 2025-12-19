package tw.gc.auto.equity.trader.services.telegram.commands;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommand;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommandContext;

/**
 * Lists available AI agents and their capabilities.
 * 
 * <p><b>Why agent visibility:</b> Makes AI capabilities discoverable. Users need to know
 * what agents are available and how to interact with them.</p>
 */
@Slf4j
public class AgentCommand implements TelegramCommand {
    
    @Override
    public String getCommandName() {
        return "agent";
    }
    
    @Override
    public void execute(String args, TelegramCommandContext context) {
        if (context.getAgentService() == null) {
            sendError(context, "⚠️ Agent service not initialized");
            return;
        }
        sendMessage(context, context.getAgentService().getAgentListMessage());
    }
    
    @Override
    public String getHelpText() {
        return "/agent - List agents";
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
