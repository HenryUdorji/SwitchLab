CREATE TABLE tenants (
    id         uuid PRIMARY KEY,
    name       text        NOT NULL,
    plan       text        NOT NULL DEFAULT 'free',
    status     text        NOT NULL DEFAULT 'active',
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE merchants (
    id        uuid PRIMARY KEY,
    tenant_id uuid    NOT NULL REFERENCES tenants (id),
    mid       char(15) NOT NULL,
    name      text    NOT NULL,
    mcc       char(4),
    currency  char(3) NOT NULL DEFAULT '566',
    country   char(3) NOT NULL DEFAULT '566',
    UNIQUE (tenant_id, mid)
);

CREATE TABLE listeners (
    id       serial PRIMARY KEY,
    dialect  text    NOT NULL,
    port     int     NOT NULL UNIQUE,
    tls      boolean NOT NULL DEFAULT false,
    enabled  boolean NOT NULL DEFAULT true
);

CREATE TABLE terminals (
    id           uuid PRIMARY KEY,
    tenant_id    uuid        NOT NULL REFERENCES tenants (id),
    merchant_id  uuid        NOT NULL REFERENCES merchants (id),
    tid          char(8)     NOT NULL UNIQUE,
    dialect      text        NOT NULL,
    status       text        NOT NULL DEFAULT 'active',
    last_seen_at timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now()
);

INSERT INTO listeners (dialect, port) VALUES ('generic87', ${generic87Port}), ('nibss', ${nibssPort});
