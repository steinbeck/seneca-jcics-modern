#!/bin/bash
# This is the SENECA batch file for unix based machines
#  (c) 1999 - 2001 C. Steinbeck

# You might want to uncomment and edit the following lines if you did not 
# add the Java binaries to your systems PATH environment variable.

JAVA_PATH=
# JAVA_HOME=/usr/java/jdk1.3.1/bin
# JAVA_PATH=$JAVA_HOME/bin

# $JAVA_PATH/java -Dseneca.home=. -jar seneca.jar
java -Dseneca.home=. -jar seneca-client.jar
