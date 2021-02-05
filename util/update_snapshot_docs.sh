#!/bin/bash

set -eu

echo -e "Publishing docs...\n"

GH_PAGES_DIR="$HOME/gh-pages"

git clone --quiet --branch=gh-pages https://x-access-token:${GITHUB_TOKEN}@github.com/google/jimfs.git $GH_PAGES_DIR > /dev/null

cd $GH_PAGES_DIR

git config --global user.name "$GITHUB_ACTOR"
git config --global user.email "$GITHUB_ACTOR@users.noreply.github.com"

./updaterelease.sh snapshot

git push -fq origin gh-pages > /dev/null

echo -e "Published docs to gh-pages.\n"
