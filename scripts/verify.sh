#!/bin/sh
set -eu

BUILD_DIR="${TMPDIR:-/tmp}/fintech-template-mail-classes"
mkdir -p "$BUILD_DIR"
find src/main/java src/test/java -name '*.java' -print | sort | xargs javac -d "$BUILD_DIR"
java -cp "$BUILD_DIR" dev.ledger.mail.PaymentNotificationPolicyTest
