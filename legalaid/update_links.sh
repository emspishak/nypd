#!/bin/bash

set -e

resources="$1"
resources_json=$(mktemp)

trap 'rm "$resources_json"' EXIT

bazel run //legalaid:ExistingToJson -- "$resources" > "$resources_json"
bazel run //legalaid:LegalAid -- -resources "$resources_json"
