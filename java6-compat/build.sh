#!/bin/sh
set -eu
cd "$(dirname "$0")"
rm -rf build/classes build/generated build/java6
mkdir -p build/classes build/generated
find build/src -name '*.java' > build/sources.txt
build_cp=/deps/streamsupport-1.7.4.jar:/deps/threetenbp-1.6.8.jar:/deps/commons-codec-1.10.jar:/deps/jctools-core-4.0.7.jar:/deps/jsr305-3.0.2.jar:/deps/error_prone_annotations-2.50.0.jar:/deps/auto-value-annotations-1.11.1.jar
javac -J-XX:TieredStopAtLevel=1 -source 8 -target 8 -cp "$build_cp" -processorpath /deps/auto-value-1.11.1.jar -s build/generated -d build/classes @build/sources.txt
javac -J-XX:TieredStopAtLevel=1 -source 8 -target 8 -proc:none -cp "build/classes:$build_cp" -d build/classes tests/*.java
compat_cp="build/classes:$build_cp"
java -XX:TieredStopAtLevel=1 -Dretrolambda.quiet=true -Dretrolambda.inputDir=build/classes -Dretrolambda.outputDir=build/java6 -Dretrolambda.classpath="$compat_cp" -Dretrolambda.bytecodeVersion=50 -Dretrolambda.defaultMethods=true -javaagent:/deps/retrolambda-2.5.7.jar -jar /deps/retrolambda-2.5.7.jar
