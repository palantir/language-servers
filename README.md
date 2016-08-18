# groovy-language-server

[![CircleCI](https://circleci.com/gh/palantir/groovy-language-server.svg?style=svg)](https://circleci.com/gh/palantir/groovy-language-server)

A groovy implementation of the [Microsoft Language Server Protocol](https://github.com/Microsoft/language-server-protocol/blob/master/protocol.md)

Uses the Java API definition in [typefox/ls-api](https://github.com/TypeFox/ls-api)

## Dev setup
- `git clone <repo link>`
- `cd groovy-language-server`
- `./gradlew eclipse` This generates an eclipse project
- Import projects into eclipse

## Building and Testing
- `./gradlew build` Compiles, runs tests, checkstyle and findbugs
- `./gradlew test` Runs all unit tests
- `./gradlew publishToMavenLocal` Creates jars in your Maven local repository

## Debug
- `./gradlew clean cleanEclipse` Deletes gradle generated files


