# DSR separation and retained data

DSR is an independent Android app in the private https://github.com/sampathmannam/dsr repository.
DailyBeat no longer includes its screen, navigation route, parser, import service, source viewer,
case workflow or PDFBox dependency. Source, tests and the expansion roadmap were preserved in
that repository before removal here.

## Existing data is retained

The DailyBeat database keeps its DSR entity definitions, compatibility DAO and migration chain.
These are dormant compatibility storage, not an active feature. No DSR tables or files are dropped.
Schema 7 preserves records from earlier development builds; schema 6 sources are retained with
explicitly marked legacy snapshots. Do not downgrade or clear the database.

Production `com.dailybeat.app` and regular QA `com.dailybeat.app.qa` must not be uninstalled,
cleared or targeted by destructive tests. Their private `dsr_imports` folder is unchanged.
The standalone `com.dsr.app` uses a different database and Android sandbox. It does not silently
copy case reviews or private files. Import the original PDF through its document picker;
a future transfer feature must be explicit and preserve the original records.

DSR records remain excluded from DailyBeat cloud backups. The independent app also disables
Android backup and device transfer. Never put operational PDFs or databases into Git or CI artifacts.
