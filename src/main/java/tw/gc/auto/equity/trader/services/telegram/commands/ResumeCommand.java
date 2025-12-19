package tw.gc.auto.equity.trader.services.telegram.commands;

import tw.gc.auto.equity.trader.services.telegram.TelegramCommand;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommandContext;

/**
 * Resumes trading after pause.
 * 
 * <p><b>Why validation matters:</b> Resume is blocked if weekly loss limits are hit or during
 * earnings blackout periods. This enforces risk management policies even when trader requests override.</p>
 */
public class ResumeCommand implements TelegramCommand {
    
    @Override
    public String getCommandName() {
        return "resume";
    }
    
    @Override
    public void execute(String args, TelegramCommandContext context) {
        if (context.getResumeHandler() != null) {
            context.getResumeHandler().accept(null);
        }
    }
    
    @Override
    public String getHelpText() {
        return "/resume - Resume trading";
    }
}
