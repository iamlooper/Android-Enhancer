#!/sbin/sh

# Execute update-binary from here for KSU installation.
unzip -o "$ZIPFILE" META-INF/* -d "$TMPDIR" >&2
sh "$TMPDIR/META-INF/com/google/android/update-binary"