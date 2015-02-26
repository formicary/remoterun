#!/bin/bash
exec ${JAVA_HOME:+${JAVA_HOME}/bin/}java -jar lib/remoterun-agent-${project.version}.jar
