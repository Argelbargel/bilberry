#!/usr/bin/env bash

./gradlew assemble -Prelease=${TRAVIS_TAG}
./gradlew check -Prelease=${TRAVIS_TAG}

if [ "$TRAVIS_TAG" != "" ]; then
    ./gradlew publish -Prelease=${TRAVIS_TAG}
fi

