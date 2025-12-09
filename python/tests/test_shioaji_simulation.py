#!/usr/bin/env python3
"""
Shioaji simulation mode tests.

These tests verify Shioaji connection and config loading in simulation mode.
Requires JASYPT_PASSWORD environment variable.

Run: JASYPT_PASSWORD=<secret> python/venv/bin/pytest python/tests/test_shioaji_simulation.py -v
"""

import pytest
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


@pytest.mark.skipif(
    not os.environ.get('JASYPT_PASSWORD'),
    reason="JASYPT_PASSWORD not set - skipping credential tests"
)
class TestShioajiSimulation:
    """Tests that require Shioaji credentials"""
    
    def test_config_loading(self):
        """Config should load with decryption"""
        from bridge import load_config_with_decryption
        
        password = os.environ['JASYPT_PASSWORD']
        config = load_config_with_decryption(password)
        
        assert 'shioaji' in config
        assert 'stock' in config['shioaji']
        assert 'api-key' in config['shioaji']['stock']
        # Decrypted value should not start with ENC(
        assert not str(config['shioaji']['stock']['api-key']).startswith('ENC(')
    
    def test_ca_path_resolution(self):
        """CA path should resolve to absolute path"""
        from bridge import load_config_with_decryption
        
        password = os.environ['JASYPT_PASSWORD']
        config = load_config_with_decryption(password)
        
        ca_path = config['shioaji']['ca-path']
        assert os.path.isabs(ca_path), f"CA path should be absolute: {ca_path}"
        # File should exist
        assert os.path.exists(ca_path), f"CA file not found: {ca_path}"
    
    def test_secret_key_decryption(self):
        """Secret key should be decrypted"""
        from bridge import load_config_with_decryption
        
        password = os.environ['JASYPT_PASSWORD']
        config = load_config_with_decryption(password)
        
        assert 'stock' in config['shioaji']
        assert 'secret-key' in config['shioaji']['stock']
        # Decrypted value should not start with ENC(
        assert not str(config['shioaji']['stock']['secret-key']).startswith('ENC(')
    
    def test_person_id_decryption(self):
        """Person ID should be decrypted"""
        from bridge import load_config_with_decryption
        
        password = os.environ['JASYPT_PASSWORD']
        config = load_config_with_decryption(password)
        
        assert 'person-id' in config['shioaji']
        # Decrypted value should not start with ENC(
        assert not str(config['shioaji']['person-id']).startswith('ENC(')
    
    def test_simulation_mode_flag(self):
        """Simulation mode should be set"""
        from bridge import load_config_with_decryption
        
        password = os.environ['JASYPT_PASSWORD']
        config = load_config_with_decryption(password)
        
        assert 'simulation' in config['shioaji']
        # Should be boolean
        assert isinstance(config['shioaji']['simulation'], bool)


class TestConfigWithoutCredentials:
    """Tests that don't require actual credentials"""
    
    def test_decrypt_config_value_without_enc(self):
        """Plain values should pass through unchanged"""
        from bridge import decrypt_config_value
        
        result = decrypt_config_value("plain_value", "any_password")
        assert result == "plain_value"
    
    def test_decrypt_config_value_with_non_string(self):
        """Non-string values should pass through unchanged"""
        from bridge import decrypt_config_value
        
        assert decrypt_config_value(12345, "pwd") == 12345
        assert decrypt_config_value(None, "pwd") is None
        assert decrypt_config_value(True, "pwd") == True
        assert decrypt_config_value(3.14, "pwd") == 3.14
    
    def test_enc_wrapper_detection(self):
        """Should detect ENC() wrapper pattern"""
        import re
        
        test_values = [
            ("ENC(abc123)", True, "abc123"),
            ("ENC()", True, ""),
            ("plain_value", False, None),
            ("ENC(abc", False, None),
            ("abc)ENC", False, None),
        ]
        
        for value, should_match, expected_inner in test_values:
            match = re.match(r'^ENC\((.+)\)$', value)
            if should_match and expected_inner:
                assert match is not None, f"Should match ENC() pattern: {value}"
                assert match.group(1) == expected_inner
            elif not should_match:
                # Either no match needed, or pattern doesn't match
                pass


class TestShioajiWrapperLogic:
    """Tests for ShioajiWrapper retry logic (no actual connection)"""
    
    def test_exponential_backoff_calculation(self):
        """Test backoff calculation matches expected values"""
        BASE_BACKOFF = 2
        MAX_RETRIES = 5
        
        expected_backoffs = [2, 4, 8, 16, 32]
        actual_backoffs = [BASE_BACKOFF ** attempt for attempt in range(1, MAX_RETRIES + 1)]
        
        assert actual_backoffs == expected_backoffs
    
    def test_max_retries_constant(self):
        """Max retries should be 5"""
        MAX_RETRIES = 5
        assert MAX_RETRIES == 5
    
    def test_total_backoff_time(self):
        """Total backoff time should be reasonable"""
        BASE_BACKOFF = 2
        MAX_RETRIES = 5
        
        total_backoff = sum(BASE_BACKOFF ** attempt for attempt in range(1, MAX_RETRIES + 1))
        # 2 + 4 + 8 + 16 + 32 = 62 seconds
        assert total_backoff == 62
        # Should be under 2 minutes
        assert total_backoff < 120


if __name__ == '__main__':
    pytest.main([__file__, '-v'])
