#!/bin/bash

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

cd "$(dirname "$0")/.."

new_version=$1
release_branch=$2
current_version=$(lein pprint :version | sed s/\"//g)

grep "$current_version" README.MD || (echo "Version string $1 was not found in README" && exit 1)

# Update to release version.
git checkout master
lein set-version $new_version
sed -i.bak 's/$current_version/"$new_version"/g' README.md
git add README.md project.clj

git commit -m "Release version $new_version."
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

next_release_version=$(lein pprint :version | sed s/\"//g)
sed -i '' "s/$new_version/$next_release_version/g" README.md

git commit -m "Prepare for next release cycle." project.clj README.md
git push origin master
