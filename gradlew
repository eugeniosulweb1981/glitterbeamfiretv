
#!/usr/bin/env sh
APP_HOME="$(cd "$(dirname "$0")"; pwd)"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
exec "${JAVA_HOME}/bin/java" -Xmx64m -Xms64m -cp "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
