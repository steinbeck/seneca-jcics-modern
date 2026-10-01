REM This is the SENECA batch file for MS based machines
REM  (c) 1999, 2000 C. Steinbeck

REM You might want to uncomment and edit the following lines if you did not 
REM add the Java binaries to your systems PATH environment variable.

REM set JAVA_HOME="c:\program files\javasoft\jre\1.3\bin"
REM set JAVA_PATH=%JAVA_HOME%/bin

REM %JAVA_PATH%/java -Dseneca.home=. -jar seneca.jar

java -Dseneca.home=. -jar seneca-client.jar
