-- The audit trail is append-only: rows can be inserted but never changed or removed.
CREATE FUNCTION reject_token_audit_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'token_audit is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER token_audit_append_only
    BEFORE UPDATE OR DELETE ON token_audit
    FOR EACH ROW EXECUTE FUNCTION reject_token_audit_change();
