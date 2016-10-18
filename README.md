# language-servers

[![CircleCI](https://circleci.com/gh/palantir/language-servers.svg?style=svg)](https://circleci.com/gh/palantir/language-servers) [ ![Download](https://api.bintray.com/packages/palantir/releases/language-servers/images/download.svg) ](https://bintray.com/palantir/releases/language-servers/_latestVersion)

A collection of implementations for the [Microsoft Language Server Protocol](https://github.com/Microsoft/language-server-protocol/blob/master/protocol.md)

## Included projects

### groovy-language-server

A groovy implementation of the protocol. Uses the Java API definition in [typefox/ls-api](https://github.com/TypeFox/ls-api)

### language-server-commons

A framework that makes writing additional light-weight language-servers easier.

## Dev Setup

### Getting started

- `git clone <repo link>`
- `cd language-servers`
- `./gradlew eclipse` This generates eclipse projects
- Import projects into eclipse

### Building and Testing
- `./gradlew build` Compiles, runs tests, checkstyle and findbugs
- `./gradlew test` Runs all unit tests
- `./gradlew publishToMavenLocal` Creates jars in your Maven local repository

### Debug
- `./gradlew clean cleanEclipse` Deletes gradle generated files
