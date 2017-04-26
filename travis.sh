#!/usr/bin/env bash

./gradlew assemble -Prelease=${TRAVIS_TAG}
./gradlew check -Prelease=${TRAVIS_TAG}

if [ "$TRAVIS_TAG" != "" ]; then
    ./gradlew publish -Prelease=${TRAVIS_TAG}

    if [ "$TRAVIS_PULL_REQUEST" == "false" && "$TRAVIS_BRANCH" == "master" ]; then
        REPO=`git config remote.origin.url`
        TARGET_REPO=${REPO/https:\/\/github.com/https://${GITHUB_USER_NAME}:${GITHUB_API_TOKEN}@github.com}
        TARGET_BRANCH="mvn-repo"

        git clone $REPO out --no-checkout
        cd out
        git checkout $TARGET_BRANCH || git checkout --orphan $TARGET_BRANCH
        git config user.name "Travis CI"
        git config user.email "$COMMIT_AUTHOR_EMAIL"

        cp -r ../build/repo/* .

        if [ -z `git diff --exit-code` ]; then
            echo "No changes to the output on this push; exiting."
            exit 0
        fi

        git add .
        git commit -m "Deploy release : $TRAVIS_TAG"
        git push $TARGET_REPO $TARGET_BRANCH
    fi
fi

