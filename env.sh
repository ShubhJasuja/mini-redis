#!/usr/bin/env bash
# No JDK was on your PATH, so this finds one. Use it with:  source env.sh
for candidate in \
  "$HOME/.jdks/temurin-17.0.17" \
  "/c/Program Files/JetBrains/IntelliJ IDEA 2025.1.2/jbr" \
  "/c/Program Files/JetBrains/IntelliJ IDEA Community Edition 2025.1.4.1/jbr"
do
  if [ -x "$candidate/bin/javac.exe" ] || [ -x "$candidate/bin/javac" ]; then
    export JAVA_HOME="$candidate"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "JAVA_HOME=$JAVA_HOME"
    java -version
    return 0 2>/dev/null || exit 0
  fi
done
echo "No JDK found. Install one (e.g. Temurin 17/21) and set JAVA_HOME." >&2
