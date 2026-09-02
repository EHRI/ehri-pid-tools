#--- !Ups

-- Caches the result of periodically checking PID target URLs.
CREATE TABLE target_checks (
    ptype pid_type NOT NULL,
    value varchar(1024) NOT NULL,
    target text NOT NULL,
    checked_at timestamp with time zone NOT NULL,
    status_code integer,
    ok boolean NOT NULL,
    error text,
    PRIMARY KEY (ptype, value),
    FOREIGN KEY (ptype, value) REFERENCES pids (ptype, value) ON DELETE CASCADE
);

# --- !Downs

DROP TABLE target_checks;
