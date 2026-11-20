-- Inline projections of the trip streams. Disposable: they can be rebuilt by replaying events.
CREATE TABLE trip_summaries (
    trip_id            UUID         PRIMARY KEY,
    name               TEXT         NOT NULL,
    base_currency      CHAR(3)      NOT NULL,
    member_count       INT          NOT NULL,
    total_spent_minor  BIGINT       NOT NULL,
    version            BIGINT       NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL
);

CREATE TABLE trip_balances (
    trip_id        UUID    NOT NULL REFERENCES trip_summaries (trip_id) ON DELETE CASCADE,
    member_id      UUID    NOT NULL,
    member_name    TEXT    NOT NULL,
    position       INT     NOT NULL,
    balance_minor  BIGINT  NOT NULL,
    PRIMARY KEY (trip_id, member_id)
);
