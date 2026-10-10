CREATE TRIGGER test_block_device_update_audit BEFORE INSERT ON audit_log
WHEN NEW.event_type = 'DEVICE_UPDATED'
BEGIN SELECT RAISE(ABORT, 'Device update audit blocked'); END
