#!/bin/bash
# Run the Strategy Demonstration
# This script compiles and runs the StrategyDemonstration class to show different trading strategies in action.


echo "Compiling project..."
jenv exec mvn compile

echo "Running Strategy Demonstration..."
mvn exec:java -Dexec.mainClass="tw.gc.auto.equity.trader.strategy.StrategyDemonstration" -Dexec.classpathScope="test"
