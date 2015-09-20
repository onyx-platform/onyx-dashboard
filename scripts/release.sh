#!/bin/bash

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

cd "$(dirname "$0")/.."

new_version=$1
release_branch=$2
current_version=`lein pprint :version`

# Update to release version.
git checkout master
lein set-version $new_version
lein update-dependency org.onyxplatform/onyx $new_version
sed -i '' "s/$current_version/$new_version/g" README.md

git commit -am "Release version $new_version."
git tag $new_version
git push origin $new_version
git push origin master

# Merge artifacts into release branch.
git checkout $release_branch
git merge master -X theirs
git push origin $release_branch

# Prepare next release cycle.
git checkout master
lein set-version
git commit -m "Prepare for next release cycle." project.clj README.md
git push origin master
