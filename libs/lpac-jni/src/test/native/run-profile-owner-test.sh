#!/usr/bin/env sh
set -eu
test_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
native_dir=$(CDPATH= cd -- "$test_dir/../../main/jni" && pwd)
test_binary=$(mktemp "${TMPDIR:-/tmp}/lpac-owner-test.XXXXXX")
trap 'unlink "$test_binary"' EXIT HUP INT TERM
${CC:-cc} -std=gnu11 -g -O1 -ffunction-sections -fdata-sections \
    -fsanitize=address,undefined -fno-omit-frame-pointer \
    -I "$native_dir/lpac" \
    "$test_dir/profile_owner_test.c" \
    "$native_dir/lpac/euicc/es10c.c" \
    "$native_dir/lpac/euicc/derutil.c" \
    "$native_dir/lpac/euicc/hexutil.c" \
    "$native_dir/lpac/euicc/base64.c" \
    -Wl,--gc-sections -o "$test_binary"
ASAN_OPTIONS=detect_leaks=1 "$test_binary"
