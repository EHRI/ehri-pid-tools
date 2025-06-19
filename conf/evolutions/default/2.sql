#--- !Ups

INSERT INTO pids (ptype, value, target, client)
VALUES ('DOI', '10.14454/fxws-0523', 'https://example.com/pid-test-1', 'system');

INSERT INTO pids (ptype, value, target, client)
VALUES ('DOI', '10.14454/fxws-0524', 'https://example.com/pid-test-2', 'system');

INSERT INTO pids (ptype, value, target, client)
VALUES ('DOI', '10.14454/1234/1234/1234/1234', 'https://example.com/pid-test-3', 'system');

INSERT INTO pids (ptype, value, target, client)
VALUES( 'ARK', '12345/12345678', 'https://example.com/pid-test-4', 'system');

INSERT INTO pids (ptype, value, target, client)
VALUES( 'ARK', '12345/56781234', 'https://example.com/pid-test-5', 'system');

INSERT INTO pids (ptype, value, target, client)
VALUES( 'ARK', '12345/1234/1234/1234/1234', 'https://example.com/pid-test-6', 'system');

INSERT INTO tombstones (pid_id, deleted_at, client, reason)
VALUES (2, CURRENT_TIMESTAMP, 'system', 'Test DOI deletion');

INSERT INTO tombstones (pid_id, deleted_at, client, reason)
VALUES (5, CURRENT_TIMESTAMP, 'system', 'Test ARK deletion');

# --- !Downs

DELETE FROM tombstones;
DELETE FROM pids;