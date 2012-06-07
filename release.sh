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
echo "name: apple
url: http://www.apple.com
charset: UTF-8
replaceNoDomainOnly: false
" > dist/input/apple.yml

echo "#!/bin/sh
cd \`dirname \$0\`
exec java -Xmx512M -XX:MaxPermSize=128M -cp gyotaku.jar gyotaku.CommandLine \"\$@\"
" > dist/gyotaku
chmod +x dist/gyotaku

echo "#!/bin/sh
cd \`dirname \$0\`
exec java -Xmx512M -XX:MaxPermSize=128M -cp gyotaku.jar gyotaku.CommandLine input output
" > dist/gyotaku_all
chmod +x dist/gyotaku_all

echo "#!/bin/sh
cd \`dirname \$0\`
exec java -Xmx512M -XX:MaxPermSize=128M -cp gyotaku.jar gyotaku.SwingApplication \"\$@\"
" > dist/gyotaku_ui
chmod +x dist/gyotaku_ui

echo "@echo off
set SCRIPT_DIR=%~dp0
java -Xmx512M -XX:MaxPermSize=128M -cp \"%SCRIPT_DIR%gyotaku.jar\" gyotaku.CommandLine  %*
" > dist/gyotaku.bat
chmod +x dist/gyotaku.bat

echo "@echo off
set SCRIPT_DIR=%~dp0
java -Xmx512M -XX:MaxPermSize=128M -cp \"%SCRIPT_DIR%gyotaku.jar\" gyotaku.CommandLine input output 
" > dist/gyotaku_all.bat
chmod +x dist/gyotaku_all.bat

echo "@echo off
set SCRIPT_DIR=%~dp0
java -Xmx512M -XX:MaxPermSize=128M -cp \"%SCRIPT_DIR%gyotaku.jar\" gyotaku.SwingApplication %*
" > dist/gyotaku_ui.bat
chmod +x dist/gyotaku_ui.bat


mv dist gyotaku-${VERSION}
zip -r gyotaku-${VERSION}.zip gyotaku-${VERSION}/
rm -rf gyotaku-${VERSION}

