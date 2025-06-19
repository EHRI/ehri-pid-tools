#--- !Ups

-- Create an enum type for PID types
CREATE TYPE pid_type AS ENUM ('DOI', 'ARK', 'HANDLE', 'PURL', 'URN');

-- This file is used to populate the database with data for testing purposes
CREATE TABLE pids (
    id serial PRIMARY KEY,                                          -- the primary key
    ptype pid_type NOT NULL,                                         -- DOI, ARK, etc.
    value varchar(1024) UNIQUE NOT NULL,                            -- the actual PID value
    target text NOT NULL,                                           -- the URL to which the PID resolves
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,  -- when the PID was created
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,  -- when the PID was last updated
    client varchar(100) NOT NULL,                                    -- the user who created the PID
    UNIQUE (ptype, value)                                      -- ensure unique combination of type and value
);

CREATE TABLE tombstones (
    id serial PRIMARY KEY,                                          -- the primary key
    pid_id integer NOT NULL REFERENCES pids(id) ON DELETE CASCADE,  -- the PID that was deleted
    deleted_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,  -- when the PID was deleted
    client varchar(100) NOT NULL,                                   -- the user who deleted the PID
    reason text NOT NULL,                                           -- the reason for deletion
    UNIQUE (pid_id)                                                 -- ensure unique PID in tombstones
);

-- Composite index for lookups by type and value
CREATE INDEX pids_type_value_idx ON pids (ptype, value);

-- Index for quick lookups by target URL
CREATE INDEX pids_target_idx ON pids (target);

-- Automatically update timestamps
CREATE OR REPLACE FUNCTION update_modified_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;;
RETURN NEW;;
END;;
$$ language plpgsql;

CREATE TRIGGER update_pids_modtime
    BEFORE UPDATE ON pids
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();

# --- !Downs

DROP TRIGGER update_pids_modtime ON pids;
DROP FUNCTION update_modified_column();
DROP TABLE tombstones;
DROP INDEX pids_target_idx;
DROP INDEX pids_type_value_idx;
DROP TABLE pids;
DROP TYPE pid_type;
