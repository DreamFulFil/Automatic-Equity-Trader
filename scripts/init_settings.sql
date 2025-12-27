-- Initialize stock and risk settings in database
-- Run this script after the application starts for the first time

-- Insert default stock settings
INSERT INTO stock_settings (shares, share_increment, updated_at, version)
VALUES (70, 27, NOW(), 0)
ON CONFLICT DO NOTHING;

-- Insert default risk settings
INSERT INTO risk_settings (max_position, daily_loss_limit, weekly_loss_limit, max_hold_minutes, updated_at, version)
VALUES (1, 1500, 7000, 45, NOW(), 0)
ON CONFLICT DO NOTHING;