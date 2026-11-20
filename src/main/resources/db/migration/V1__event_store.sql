-- Append-only event store. One row per event; the unique key is the optimistic-concurrency check.
CREATE TABLE events (
    position     BIGSERIAL    PRIMARY KEY,
    stream_id    UUID         NOT NULL,
    stream_type  TEXT         NOT NULL,
    version      BIGINT       NOT NULL CHECK (version > 0),
    type         TEXT         NOT NULL,
    payload      JSONB        NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL,
    recorded_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT events_stream_version_uk UNIQUE (stream_id, version)
);

CREATE INDEX events_stream_occurred_idx ON events (stream_id, occurred_at);

-- Events are facts: forbid UPDATE and DELETE at the database level.
CREATE FUNCTION events_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'events is append-only';
END;
$$;

CREATE TRIGGER events_no_update BEFORE UPDATE OR DELETE ON events
    FOR EACH ROW EXECUTE FUNCTION events_append_only();
