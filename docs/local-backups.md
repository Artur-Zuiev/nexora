# Local PostgreSQL backups and recovery (development only)

Nexora now has **opt-in** local backup, verification and non-destructive restore
commands. The script works with the repository's `docker-compose.yml`, Python 3.10+
and Docker Desktop/Compose. No scheduled jobs, storage account or cloud services
are created. Run from the repository root.

## Backup and verify

Stop editing demo data while taking a backup. A PostgreSQL custom-format dump is
transactionally consistent at the database level, but application activity may
continue after the snapshot.

```sh
python scripts/db_backup.py backup --output backups/nexora-demo.dump
python scripts/db_backup.py verify --input backups/nexora-demo.dump
```

Each dump gets a matching `.sha256` checksum file. Existing backup paths are
never intentionally overwritten. The script verifies that PostgreSQL can read
the archive before reporting success. It does not verify all business-level data,
and a checksum does not provide authenticity against an attacker able to replace
both files.

On Windows, `py -3 scripts/db_backup.py ...` can be used instead of `python`.

**Backups include all tenants, records, users and password hashes.** Keep them
private and encrypted at rest, outside source control. The `backups/` directory
is ignored by Git; do not share dumps by email or publish them. Treat backups as
sensitive data, even if the demo currently contains only synthetic records.

## Restore into a NEW database, leaving live `nexora` untouched

```sh
python scripts/db_backup.py restore --input backups/nexora-demo.dump --database nexora_recovered --confirm RESTORE:nexora_recovered
```

The script rejects `nexora` and PostgreSQL system database names and refuses
to replace or drop any existing database. It requires an explicit confirmation
matching the new database name. If PostgreSQL restoration fails partway through,
the **partial newly created database remains** for inspection and manual cleanup.
A successful restore **does not switch Nexora's database connection**; inspect
the restored data and perform any changeover manually, after separate review.

The CI exercise uses synthetic data only: dump, checksum verification, restore
to a new database, readback, rejection of existing destinations and checksum
tampering. There is no production-grade backup policy, automated encryption,
remote redundancy, disaster recovery guarantee, retention schedule, or verified
restore across PostgreSQL major versions. The scripts are deliberately local-only.
