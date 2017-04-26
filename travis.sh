#!/usr/bin/env bash

echo "Building branch $TRAVIS_BRANCH (pull-request: $TRAVIS_PULL_REQUEST)..."

./gradlew assemble -Prelease=${TRAVIS_TAG}
./gradlew check -Prelease=${TRAVIS_TAG}

if [ "$TRAVIS_TAG" != "" ]; then
    echo "Publishing release $TRAVIS_TAG..."
    
    ./gradlew publish -Prelease=${TRAVIS_TAG}

    REPO=`git config remote.origin.url`
    TARGET_REPO=${REPO/https:\/\/github.com/https://${GITHUB_USER_NAME}:${GITHUB_API_TOKEN}@github.com}
    TARGET_BRANCH="mvn-repo"

    git clone ${REPO} build/deploy --no-checkout

    cd build/deploy
    git checkout ${TARGET_BRANCH} || git checkout --orphan ${TARGET_BRANCH}
    git config user.name "Travis CI"
    git config user.email "$COMMIT_AUTHOR_EMAIL"

    echo "Adding release to mvn-repo..."
    cp -rv ../repo/* .

    echo "Changes for release..."
    git status -s

    if [ -z `git status -s` ]; then
        echo "No changes to the output on this push; exiting."
        exit 0
    fi

    git add .
    git commit -m "release : $TRAVIS_TAG"
    git push ${TARGET_REPO} ${TARGET_BRANCH}
fi

