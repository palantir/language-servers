#!/bin/bash

set -euo pipefail

# Run only checkstyle + findbugs
./gradlew --stacktrace --parallel build -x test -x jacocoTestReport
