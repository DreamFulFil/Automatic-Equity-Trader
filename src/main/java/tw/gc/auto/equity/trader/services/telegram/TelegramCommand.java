package tw.gc.auto.equity.trader.services.telegram;

/**
 * Command Pattern interface for Telegram bot commands.
 * 
 * <p><b>Why Command Pattern:</b> Decouples command execution from the service layer,
 * making it easy to add new commands without modifying existing code (Open/Closed Principle).
 * Each command encapsulates its own validation and execution logic, promoting
 * Single Responsibility Principle.</p>
 * 
 * <p><b>Design Decision:</b> Commands are stateless and rely on dependency injection
 * for required services. This enables testing in isolation and reusability across contexts.</p>
 */
public interface TelegramCommand {
    
    /**
     * Returns the command name (e.g., "status", "pause").
     * 
     * @return command identifier without the leading slash
     */
    String getCommandName();
    
    /**
     * Executes the command with given arguments.
     * 
     * <p><b>Why separate context:</b> Passes execution context to avoid tight coupling.
     * Commands can access services through the context without directly depending on them.</p>
     * 
     * @param args command arguments (e.g., for "/change-stock 2330.TW", args would be "2330.TW")
     * @param context execution context containing services and state
     */
    void execute(String args, TelegramCommandContext context);
    
    /**
     * Returns help text for this command.
     * 
     * @return human-readable description of command usage
     */
    String getHelpText();
}
