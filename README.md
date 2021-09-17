# Genesis

Java addin that manages other Java Addins

# Build Genesis

1) Install Maven
2) Add Notes.jar to your maven repo, that will make it accessible to Maven

```
mvn install:install-file -Dfile=path\to\Notes.jar
```

3) Build Genesis.jar

```
mvn package
```

This should create a Genesis.jar for you which you can deploy on Domino server after all.

# How to register Genesis on Domino server

1) Upload file to Domino server (on Windows it's in the Domino executable folder).

JavaAddin\Genesis.jar

2) Register Java addin in Domino environment (if you already have Addins there, keep in mind that separator is semicolon on Windows and colon on Linux) 

```
JAVAUSERCLASSES=.\JavaAddin\Genesis.jar
```

# How to run Genesis

```
load runjava net.prominic.Genesis.App
```

# Example of commands

```
tell net.prominic.Genesis.App help
```

```
tell net.prominic.Genesis.App info
```
