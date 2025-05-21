#--- !Ups

INSERT INTO pids (ptype, value, target, client)
VALUES ('DOI', '10.14454/fxws-0523', 'https://example.com/pid-test-1', 'system');

INSERT INTO pids (ptype, value, target, client)
VALUES ('DOI', '10.14454/fxws-0524', 'https://example.com/pid-test-2', 'system');

INSERT INTO pids (ptype, value, target, client)
VALUES ('DOI', '10.14454/1234/1234/1234/1234', 'https://example.com/pid-test-3', 'system');

INSERT INTO tombstones (pid_id, deleted_at, client, reason)
VALUES (2, CURRENT_TIMESTAMP, 'system', 'Test deletion');

# --- !Downs

DELETE FROM tombstones;
DELETE FROM pids;