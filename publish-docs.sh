#!/usr/bin/env bash

set -ex

pip install --user awscli; export PATH=$PATH:$HOME/.local/bin

PROJECT_VERSION=$(./gradlew --quiet projectVersion)

publish_site() {
    aws s3 sync build/asciidoc/html5/ "s3://cfn-stacks.com/docs/artifacts3-plugin/${1}"
    aws s3 cp build/asciidoc/pdf/index.pdf "s3://cfn-stacks.com/docs/artifacts3-plugin/${1}/artifacts3-plugin.pdf"
}

# Publish the site for the current version (always)
publish_site $PROJECT_VERSION

# If this is a release (not a snapshot) then publish as latest as well
if [[ $PROJECT_VERSION != *"SNAPSHOT"* ]]; then
    publish_site "latest"
fi