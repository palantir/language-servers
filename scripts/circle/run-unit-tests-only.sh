#!/bin/bash

set -euo pipefail

time ./gradlew --stacktrace --parallel test
time ./gradlew --stacktrace jacocoFullReport
