[![Build Status](https://github.com/EHRI/ehri-pid-tools/workflows/CI/badge.svg)](https://github.com/EHRI/ehri-pid-tools/actions?query=workflow%3ACI)
[![Scala](https://img.shields.io/badge/scala-2.13-DC322F.svg?logo=scala)](https://www.scala-lang.org/)
[![Play Framework](https://img.shields.io/badge/play-3.0-blue.svg?logo=playframework)](https://www.playframework.com/)

# EHRI PID Tools

Tools for registering, resolving, and monitoring Persistent Identifiers (PIDs) used by the
[European Holocaust Research Infrastructure (EHRI)](https://www.ehri-project.eu).

## Main functionality

- **DOI registration & proxy** — a thin proxy in front of the [DataCite](https://datacite.org)
  REST API, handling registration, updates, and tombstoning of DOIs for EHRI resources.
- **DOI landing pages** — public landing pages for each DOI, with schema.org-structured
  metadata, citation export, and content-negotiated JSON/JSON:API responses.
- **DOI target health monitoring** — a scheduled background check that DOI targets still
  resolve, with results on a health dashboard (`/health`) and a per-DOI detail page
  (`/dois/health`).
- **PID policy page** — the EHRI PID policy, covering (so far) DOIs, ARKs, ORCID, ROR, and e-ISSN.

## Requirements

- JDK 21
- [sbt](https://www.scala-sbt.org/)
- PostgreSQL (a `docker-compose.yml` is provided for local development)

## Running locally

```shell
docker compose up -d   # start Postgres
sbt run                # starts the app on http://localhost:9000
```

Two config files are excluded from version control and need to be created locally:
`conf/doi.conf` (DataCite API credentials) and `conf/clients.conf` (auth client secrets).
See `conf/application.conf` and `conf/test.conf` for the expected shape.

## Running tests

```shell
docker compose -f docker-compose.test.yml up -d
sbt test
```

## Tech stack

Scala 2.13 · Play Framework 3 · Anorm · PostgreSQL · Pekko

## TODO

- Improve the DOI search page...
- Add more details to the landing page 
