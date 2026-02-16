# Genesis

Java addin for HCL Domino that manages other Java Addins.

# Build

1) Install Maven
2) Add Notes.jar to your local Maven repository:

```
mvn install:install-file -Dfile=path/to/Notes.jar -DgroupId=lotus.notes -DartifactId=notes -Dversion=10.0 -Dpackaging=jar
```

3) Build Genesis.jar:

```
mvn package
```

# Deploy on Domino server

1) Upload Genesis.jar to the Domino program directory:

```
JavaAddin/Genesis.jar
```

2) Add to notes.ini (semicolon separator on Windows, colon on Linux):

```
JAVAUSERCLASSES=./JavaAddin/Genesis.jar
```

3) Create config file at `JavaAddin/Genesis/config.txt`:

```
#Fri Feb 13 14:46:43 CET 2026
version=1.0.0
runjava=Genesis
```

# Run

```
load runjava Genesis
```

With a custom catalog:

```
load runjava Genesis appstore
load runjava Genesis dev
load runjava Genesis https://your-server.com/gc.nsf
```

# Commands

```
tell Genesis help
tell Genesis info
tell Genesis check
tell Genesis state
tell Genesis list
tell Genesis install <id>
tell Genesis update <name>
tell Genesis sign <dbpath>
tell Genesis runjson <filepath>
```
