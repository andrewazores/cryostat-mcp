#!/usr/bin/env bash

quarkus build \
    --native \
    --no-tests \
    -Dquarkus.native.container-build=true
