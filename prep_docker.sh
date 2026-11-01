#!/bin/bash

rm -rf build/docker
mkdir build/docker
mkdir build/docker/lib
rsync -a -c build/install/JRomManager-nogui-noarch/lib/ build/docker/lib/
rsync -a -c build/install/JRomManager-nogui-noarch/*.jar build/docker/
# AOT training fixture: the Dockerfile trains the server AOT cache in a builder
# stage from this DAT (see docker/Dockerfile). roms/ must exist for the scan's
# dst dir; .keep keeps the empty dir through docker COPY.
mkdir -p build/docker/aot-fixture/roms
rsync -a -c dist/aot-fixture/ build/docker/aot-fixture/
touch build/docker/aot-fixture/roms/.keep
cp docker/Dockerfile build/docker/Dockerfile
