-- Async projection fed by module events; (trip_id, version) makes redelivery harmless.
CREATE TABLE timeline_entries (
    trip_id      UUID         NOT NULL,
    version      BIGINT       NOT NULL,
    kind         TEXT         NOT NULL,
    ref_id       UUID         NOT NULL,
    summary      TEXT         NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL,
    undone_at    TIMESTAMPTZ,
    PRIMARY KEY (trip_id, version)
);

CREATE INDEX timeline_entries_ref_idx ON timeline_entries (trip_id, ref_id);
