#!/bin/sh

if [ $# -ne 1 ]; then
  echo "usage: ./release.sh [version]"
  exit 0
fi
VERSION=$1

sbt clean
sbt proguard

mkdir dist

SCALA_VERSION=2.9.1
cp -p target/scala-${SCALA_VERSION}/gyotaku_${SCALA_VERSION}-*.min.jar dist/gyotaku.jar

mkdir dist/input
echo "{
  \"name\": \"www.apple.com\",
  \"url\": \"http://www.apple.com/\",
  \"charset\": \"UTF-8\",
  \"replaceNoDomainOnly\": false
}
" > dist/input/apple.json

echo "#!/bin/sh
cd \`dirname \$0\`
exec java -jar -Xmx512M -XX:MaxPermSize=128M gyotaku.jar \"\$@\"
" > dist/gyotaku
chmod +x dist/gyotaku

echo "@echo off
set SCRIPT_DIR=%~dp0
java -jar -Xmx512M -XX:MaxPermSize=128M \"%SCRIPT_DIR%gyotaku.jar\" %*
" > dist/gyotaku.bat
chmod +x dist/gyotaku.bat

mv dist gyotaku-${VERSION}
zip -r gyotaku-${VERSION}.zip gyotaku-${VERSION}/
rm -rf gyotaku-${VERSION}

