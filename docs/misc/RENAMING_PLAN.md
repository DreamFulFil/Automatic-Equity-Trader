# Global Renaming Plan
 AutomaticEquityTrader
 tw.gc.auto.equity.trader

## Renaming Strategy

### Phase 1: File and Directory Names
 Automatic-Equity-Trader
 start-auto-trader.fish
3. Java package directories

### Phase 2: Package Declarations
 tw.gc.auto.equity.trader

### Phase 3: Code References
 autotrader (lowercase identifiers)
 AUTOTRADER (constants)
 AutoTrader (class names where applicable)

### Phase 4: String Literals & Messages
 "Auto Trader"
 "Automatic Equity Trader"
 "auto-equity-trader"

### Phase 5: Configuration Files
- pom.xml artifactId
- Database names
- Log file names

## Execution Order
1. Create new package structure
2. Move and rename Java files with package updates
3. Update all imports
4. Rename scripts and config files
5. Update string literals
6. Update tests
7. Rebuild and test

## Files Requiring Special Attention
- pom.xml (artifactId, name, description)
- application.yml (logging, database paths)
- README.md (complete rewrite)
 start-auto-trader.fish
- run-tests.sh (if it has hardcoded paths)
