package tw.gc.mtxfbot.agents;

import java.util.Map;
import java.util.StringJoiner;

/**
 * PromptFactory builds structured prompts for TutorBot use cases.
 */
public class PromptFactory {

    public String buildQuestionPrompt(String question) {
        return "You are a Taiwan lunch-session trading tutor. Focus on MTXF futures and MediaTek (2454.TW). " +
                "Provide concise, actionable guidance with numbers when possible.\n\nUser question: " + question;
    }

    public String buildInsightPrompt() {
        return "Generate a concise daily trading insight for Taiwan markets. " +
                "Cover momentum, risk management, and psychology. Keep under 180 words.";
    }

    public String buildWhatIfPrompt(String hypothesis, Map<String, Object> simulationResult) {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("You are a trading coach analyzing a hypothetical change.");
        joiner.add("Hypothesis: " + hypothesis);
        joiner.add("Baseline PnL: " + simulationResult.getOrDefault("baselinePnl", 0.0));
        joiner.add("Simulated PnL: " + simulationResult.getOrDefault("simulatedPnl", 0.0));
        joiner.add("Delta: " + simulationResult.getOrDefault("delta", 0.0));
        joiner.add("Trades analyzed: " + simulationResult.getOrDefault("tradesAnalyzed", 0));
        joiner.add("Provide a short explanation of the impact and any risks.");
        return joiner.toString();
    }
}
