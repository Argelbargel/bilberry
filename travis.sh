#!/usr/bin/env bash

echo "Building branch $TRAVIS_BRANCH (pull-request: $TRAVIS_PULL_REQUEST)..."

./gradlew check -Prelease=${TRAVIS_TAG}

#if [ "$TRAVIS_TAG" != "" ]; then
    echo "Publishing release $TRAVIS_TAG..."
    
    ./gradlew publish -Prelease=${TRAVIS_TAG}

    REPO=`git config remote.origin.url`
    ORIGIN=${REPO/https:\/\/github.com\//git@github.com:}
    TARGET_BRANCH="mvn-repo"

    git clone ${REPO} build/deploy --no-checkout

    cd build/deploy
    git remote remove origin
    git remote add -t ${TARGET_BRANCH} origin ${ORIGIN}
    git config user.name "Travis CI"
    git config user.email "$GITHUB_USER_NAME@users.noreply.github.com"
    git config push.default simple

    git checkout ${TARGET_BRANCH} || (git checkout --orphan ${TARGET_BRANCH} && git rm -r -f .)

    echo "Adding release to mvn-repo..."
    cp -r ../repo/* .

    echo "Changes to release:"
    git status -s

    if [ -n "$(git status -s)" ]; then
        git add .
        git commit -m "release: $TRAVIS_TAG"

        ENCRYPTED_KEY_VAR="encrypted_${DEPLOY_KEY_ID}_key"
        ENCRYPTED_IV_VAR="encrypted_${DEPLOY_KEY_ID}_iv"
        ENCRYPTED_KEY=${!ENCRYPTED_KEY_VAR}
        ENCRYPTED_IV=${!ENCRYPTED_IV_VAR}
        openssl aes-256-cbc -K ${ENCRYPTED_KEY} -iv ${ENCRYPTED_IV} -in ../../deploy_key.enc -out deploy_key -d
exit 0
        chmod 600 deploy_key
        eval `ssh-agent -s`
        ssh-add deploy_key

        git push --set-upstream origin ${TARGET_BRANCH}
    fi
#fi

