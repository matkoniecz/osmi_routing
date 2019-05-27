#! /usr/bin/env bash

set -euo pipefail

BASEDIR=$(pwd)
rm -rf maven_repository/*
cd graphhopper
mvn --projects api,core,reader-osm -am -DskipTests=true compile package
mvn deploy:deploy-file -Durl=file://$(pwd)/../maven_repository/ -Dfile=core/target/graphhopper-core-0.12-SNAPSHOT.jar -DgroupId=com.graphhopper -DartifactId=graphhopper-core -Dpackaging=jar -Dversion=0.12.0-SNAPSHOT
mvn deploy:deploy-file -Durl=file://$(pwd)/../maven_repository/ -Dfile=reader-osm/target/graphhopper-reader-osm-0.12-SNAPSHOT.jar -DgroupId=com.graphhopper -DartifactId=graphhopper-reader-osm -Dpackaging=jar -Dversion=0.12.0-SNAPSHOT
mvn deploy:deploy-file -Durl=file://$(pwd)/../maven_repository/ -Dfile=api/target/graphhopper-api-0.12-SNAPSHOT.jar -DgroupId=com.graphhopper -DartifactId=graphhopper-api -Dpackaging=jar -Dversion=0.12.0-SNAPSHOT
cd $BASEDIR
MAVEN_OPTS=-Xss20m mvn compile assembly:single -U
