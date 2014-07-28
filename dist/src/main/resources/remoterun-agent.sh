#!/bin/bash

ENV_NAME=$1
JAVA=${JAVA_HOME:+${JAVA_HOME}/bin/}java

$JAVA -jar lib/remoterun-agent-${project.version}.jar
