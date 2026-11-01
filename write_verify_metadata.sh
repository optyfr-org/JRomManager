#!/bin/bash

./gradlew --write-verification-metadata pgp,sha256 --export-keys help
