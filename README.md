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

1) Upload Genesis.jar to the JavaAddin folder on Domino server (on Windows it's under the executable directory, on Linux it's under the notesdata directory):

```
JavaAddin/Genesis/Genesis-1.0.0.jar
```

2) Register in notes.ini:

```
JavaUserClassesExt=GJA_Genesis
GJA_Genesis=JavaAddin/Genesis/Genesis-1.0.0.jar
```

If you already have other addins registered, add Genesis to the existing list:

```
JavaUserClassesExt=GJA_Genesis,GJA_DominoMeter,GJA_DesignSync
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
