#!/usr/bin/env bash

echo "Building branch $TRAVIS_BRANCH (pull-request: $TRAVIS_PULL_REQUEST)..."
echo $(pwd)

./gradlew assemble -Prelease=${TRAVIS_TAG}
./gradlew check -Prelease=${TRAVIS_TAG}

if [ "$TRAVIS_TAG" != "" ]; then
    echo "Publishing release $TRAVIS_TAG..."
    
    ./gradlew publish -Prelease=${TRAVIS_TAG}

    REPO=`git config remote.origin.url`
    TARGET_REPO=${REPO/https:\/\/github.com/https://${GITHUB_USER_NAME}:${GITHUB_API_TOKEN}@github.com}
    TARGET_BRANCH="mvn-repo"

    git clone ${REPO} /tmp/deploy --no-checkout

    cd /tmp/deploy
    git checkout ${TARGET_BRANCH} || git checkout --orphan ${TARGET_BRANCH}
    git config user.name "Travis CI"
    git config user.email "$GITHUB_USER_NAME@users.noreply.github.com"
    git config push.default simple

    echo "Adding release to mvn-repo..."
    cp -rv $HOME/repo/* .

    echo "Changes to release..."
    git status -s

    if [ -n "$(git status -s)" ]; then
        git add .
        git commit -m "release: $TRAVIS_TAG"
        git push ${TARGET_REPO}
    fi
fi

