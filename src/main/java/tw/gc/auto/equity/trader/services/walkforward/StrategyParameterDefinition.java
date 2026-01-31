package tw.gc.auto.equity.trader.services.walkforward;

/**
 * Defines an optimizable parameter for a trading strategy.
 * Used by the {@link ParameterOptimizer} to explore the parameter space.
 * 
 * <p>Example usage:
 * <pre>
 * var rsiPeriod = new StrategyParameterDefinition("period", 7, 21, 2, 14);
 * var oversold = new StrategyParameterDefinition("oversold", 20.0, 40.0, 5.0, 30.0);
 * </pre>
 * 
 * @param name The parameter name (must match strategy constructor/setter names)
 * @param minValue Minimum value in the search space (inclusive)
 * @param maxValue Maximum value in the search space (inclusive)
 * @param step Step size for grid search (smaller = finer but slower)
 * @param defaultValue Default value if no optimization is performed
 * 
 * @since 2026-01-30 Phase 1: Walk-Forward Optimization Framework
 */
public record StrategyParameterDefinition(
    String name,
    double minValue,
    double maxValue,
    double step,
    double defaultValue
) {
    /**
     * Compact constructor with validation.
     */
    public StrategyParameterDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Parameter name cannot be null or blank");
        }
        if (minValue > maxValue) {
            throw new IllegalArgumentException("minValue (%s) cannot be greater than maxValue (%s)"
                .formatted(minValue, maxValue));
        }
        if (step <= 0) {
            throw new IllegalArgumentException("step must be positive, got: %s".formatted(step));
        }
        if (defaultValue < minValue || defaultValue > maxValue) {
            throw new IllegalArgumentException("defaultValue (%s) must be between minValue (%s) and maxValue (%s)"
                .formatted(defaultValue, minValue, maxValue));
        }
    }
    
    /**
     * Creates a parameter definition for an integer parameter.
     * 
     * @param name Parameter name
     * @param minValue Minimum integer value
     * @param maxValue Maximum integer value
     * @param step Step size (typically 1)
     * @param defaultValue Default integer value
     * @return A new parameter definition
     */
    public static StrategyParameterDefinition ofInt(String name, int minValue, int maxValue, int step, int defaultValue) {
        return new StrategyParameterDefinition(name, minValue, maxValue, step, defaultValue);
    }
    
    /**
     * Creates a parameter definition for a double/percentage parameter.
     * 
     * @param name Parameter name
     * @param minValue Minimum value
     * @param maxValue Maximum value
     * @param step Step size
     * @param defaultValue Default value
     * @return A new parameter definition
     */
    public static StrategyParameterDefinition ofDouble(String name, double minValue, double maxValue, double step, double defaultValue) {
        return new StrategyParameterDefinition(name, minValue, maxValue, step, defaultValue);
    }
    
    /**
     * Calculates the number of discrete values in the search space.
     * 
     * @return Number of possible values
     */
    public int gridSize() {
        return (int) Math.floor((maxValue - minValue) / step) + 1;
    }
    
    /**
     * Returns the value at a specific index in the grid.
     * 
     * @param index Zero-based index
     * @return The parameter value at that index
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public double valueAt(int index) {
        int size = gridSize();
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index %d out of range [0, %d)".formatted(index, size));
        }
        return Math.min(minValue + (index * step), maxValue);
    }
    
    /**
     * Generates all possible values in the search space.
     * 
     * @return Array of all parameter values
     */
    public double[] allValues() {
        int size = gridSize();
        double[] values = new double[size];
        for (int i = 0; i < size; i++) {
            values[i] = valueAt(i);
        }
        return values;
    }
}
