#!/bin/sh
set -eu

if [ "$#" -ne 1 ]; then
  echo "usage: $0 customer@example.com" >&2
  exit 2
fi

BUILD_DIR="${TMPDIR:-/tmp}/fintech-template-mail-classes"
mkdir -p "$BUILD_DIR"
find src/main/java -name '*.java' -print | sort | xargs javac -d "$BUILD_DIR"
java -cp "$BUILD_DIR" dev.ledger.mail.PaymentMailRunner "$1"
