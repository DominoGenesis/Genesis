# AddInDirector

Java addin that manages other Java Addins

# Build AddInDirector

1) Install Maven
2) Add Notes.jar to your maven repo, that will make it accessible to Maven

```
mvn install:install-file -Dfile=path\to\Notes.jar
```

3) Build AddInDirector.jar

```
mvn package
```

This should create a AddInDirector.jar for you which you can deploy on Domino server after all.

# How to register AddInDirector on Domino server

1) Upload file to Domino server (on Windows it's in the Domino executable folder).

JavaAddin\AddInDirector.jar

2) Register Java addin in Domino environment (if you already have Addins there, keep in mind that separator is semicolon on Windows and colon on Linux) 

```
JAVAUSERCLASSES=.\JavaAddin\AddInDirector.jar
```

# How to run AddInDirector

```
load runjava net.prominic.AddInDirector.App
```

# Example of commands

```
tell net.prominic.AddInDirector.App help
```

```
tell net.prominic.AddInDirector.App info
```
