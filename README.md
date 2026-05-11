# Genesis

Java addin for HCL Domino that manages other Java Addins. Installs, updates, and signs addins from a remote catalog (`gc.nsf`).

# Build

1) Install Maven.
2) Add `Notes.jar` to your local Maven repository:

```
mvn install:install-file -Dfile=path/to/Notes.jar -DgroupId=lotus.notes -DartifactId=notes -Dversion=10.0 -Dpackaging=jar
```

3) Build the jar:

```
mvn package
```

The resulting `Genesis-1.0.1.jar` lands in `target/`.

# Deploy on Domino server

1) Upload `Genesis-1.0.1.jar` to the JavaAddin folder on the Domino server (on Windows it's under the executable directory, on Linux it's under the notesdata directory):

```
JavaAddin/Genesis/Genesis-1.0.1.jar
```

2) Register in `notes.ini`:

```
JavaUserClassesExt=GJA_Genesis
GJA_Genesis=JavaAddin/Genesis/Genesis-1.0.1.jar
```

If you already have other addins registered, add Genesis to the existing list:

```
JavaUserClassesExt=GJA_Genesis,GJA_DominoMeter,GJA_DesignSync
```

3) Create the config file at `JavaAddin/Genesis/config.txt`:

```
#Fri Feb 13 14:46:43 CET 2026
version=1.0.1
runjava=Genesis
```

# Run

Default catalog (`https://appstore.dominogenesis.com/gc.nsf`):

```
load runjava Genesis
```

With a custom catalog — three accepted forms:

```
load runjava Genesis appstore
load runjava Genesis dev
load runjava Genesis https://your-server.example.com/gc.nsf
```

`appstore` and `dev` are aliases for the production and development catalogs respectively. Any other value is treated as a full URL.

## Catalog persistence (config.txt)

The resolved catalog URL is written to `JavaAddin/Genesis/config.txt` under the key `catalog`. On the next restart Genesis reads this value, so **you only need to pass the argument once**:

```
load runjava Genesis https://your-server.example.com/gc.nsf
# ...later...
load runjava Genesis            # uses the saved URL automatically
```

Resolution order at startup:

1. Argument passed to `load runjava Genesis <value>` — if present, used and **persisted to config.txt** (overwrites any prior saved value).
2. `catalog` key in `config.txt` — used if no argument was passed.
3. Hardcoded default `https://appstore.dominogenesis.com/gc.nsf` — used if neither of the above is set.

`config.txt` is a standard Java properties file. A typical post-install state:

```
#Fri Feb 13 14:46:43 CET 2026
version=1.0.1
runjava=Genesis
active=1
catalog=https://appstore.dominogenesis.com/gc.nsf
```

You can edit `config.txt` directly to change the saved catalog without restarting with a new argument — the next restart will pick it up.

# Commands

Run from the Domino console:

```
tell Genesis help
tell Genesis info
tell Genesis check
tell Genesis state
tell Genesis list
tell Genesis install <id> [params...]
tell Genesis update <name> [params...]
tell Genesis sign <dbpath>
tell Genesis runjson <filepath>
tell Genesis origin <host> <secret> <command>
```

## origin — run a command against a different catalog

`origin` lets you execute any of the above commands against a catalog **other than the one Genesis was started with**, without restarting the addin. Useful for one-off installs from a private/staging catalog.

Syntax:

```
tell Genesis origin <host> <secret> <command>
```

- `<host>` — full catalog URL (e.g. `https://other-catalog.example.com/gc.nsf`).
- `<secret>` — access secret for the catalog, or `-` if the catalog is public.
- `<command>` — any normal Genesis command (`install`, `update`, `list`, `check`, ...).

Examples:

```
# Install dominometer from a private catalog (no secret)
tell Genesis origin https://private-catalog.example.com/gc.nsf - install dominometer prod

# Same, but the private catalog requires a secret
tell Genesis origin https://private-catalog.example.com/gc.nsf mySecret install dominometer prod

# List apps available in another catalog
tell Genesis origin https://other-catalog.example.com/gc.nsf - list

# Verify connectivity to another catalog
tell Genesis origin https://other-catalog.example.com/gc.nsf - check
```

`origin` does **not** change the default catalog — it only re-routes the single command that follows. The catalog saved in `config.txt` is unaffected. To permanently switch catalogs, restart Genesis with the new URL as an argument (see [Catalog persistence](#catalog-persistence-configtxt)).
