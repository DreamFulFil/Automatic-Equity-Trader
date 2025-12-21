package tw.gc.auto.equity.trader.services.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tw.gc.auto.equity.trader.services.telegram.commands.*;
import java.util.*;

/**
 * Central registry for all Telegram commands.
 * 
 * <p><b>Why centralized registry:</b> Provides single point of control for command registration
 * and lookup. Makes it trivial to add new commands without modifying dispatcher logic.</p>
 * 
 * <p><b>Design Decision:</b> Commands are registered at construction time (eager loading).
 * This ensures all commands are available immediately and fails fast if configuration is wrong.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TelegramCommandRegistry {
    
    private final Map<String, TelegramCommand> commands = new HashMap<>();
    
    /**
     * Register all built-in commands.
     * 
     * <p><b>Why explicit registration:</b> Clear visibility of all available commands.
     * Avoids classpath scanning magic that can hide dependencies.</p>
     */
    public void registerCommands() {
        // Trading control commands
        register(new StatusCommand());
        register(new PauseCommand());
        register(new ResumeCommand());
        register(new CloseCommand());
        register(new ShutdownCommand());
        register(new HelpCommand());
        
        // Agent commands
        register(new AgentCommand());
        register(new TalkCommand());
        register(new InsightCommand());
        
        // Live mode commands
        register(new GoLiveCommand());
        register(new ConfirmLiveCommand());
        register(new BackToSimCommand());
        
        // Configuration commands
        register(new ChangeStockSettingsCommand());
        register(new ChangeIncrementCommand());
        
        log.info("✅ Registered {} Telegram commands", commands.size());
    }
    
    /**
     * Register a single command.
     * 
     * @param command command instance to register
     */
    public void register(TelegramCommand command) {
        String name = command.getCommandName().toLowerCase();
        if (commands.containsKey(name)) {
            log.warn("⚠️ Overwriting existing command: {}", name);
        }
        commands.put(name, command);
        log.debug("Registered command: {}", name);
    }
    
    /**
     * Get command by name (case-insensitive).
     * 
     * @param commandName command name without leading slash
     * @return command instance or null if not found
     */
    public TelegramCommand getCommand(String commandName) {
        return commands.get(commandName.toLowerCase());
    }
    
    /**
     * Check if command exists.
     * 
     * @param commandName command name without leading slash
     * @return true if command is registered
     */
    public boolean hasCommand(String commandName) {
        return commands.containsKey(commandName.toLowerCase());
    }
    
    /**
     * Get all registered commands.
     * 
     * @return unmodifiable collection of all commands
     */
    public Collection<TelegramCommand> getAllCommands() {
        return Collections.unmodifiableCollection(commands.values());
    }
    
    /**
     * Get command count.
     * 
     * @return number of registered commands
     */
    public int getCommandCount() {
        return commands.size();
    }
}
