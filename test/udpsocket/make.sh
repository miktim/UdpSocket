#!/bin/bash

echo $(javac -version)
jname=UdpSocket
cpath=/org/miktim/udpsocket/
if [ ! -d ${cpath} ]
  then mkdir -p .${cpath}
  else rm -f ${cpath}/*.*
fi
javac -Xstdout ./compile.log -Xlint:unchecked -cp .${cpath} -d ./ \
  ../../src${cpath}*.java
#  WsHandler.java Headers.java WsConnection.java WebSocket.java WsListener.java
if [ $? -eq 0 ] ; then
  jar cvf ./${jname}.jar .${cpath}/*.class
#  javadoc -d ./${jname}Doc -nodeprecated -use package-info.java \
#   ../../src${cpath}*.java
fi
rm -f -r ./org
#more < ./compile.log
cat compile.log
echo
echo Completed. Press Enter to exit...
read