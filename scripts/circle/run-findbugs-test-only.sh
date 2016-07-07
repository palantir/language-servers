#!/bin/bash

set -euo pipefail

time ./gradlew --stacktrace --parallel findbugsTest
