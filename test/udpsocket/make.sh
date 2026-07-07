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
if [ $? -eq 0 ] ; then
  jar cvf ./${jname}.jar .${cpath}/*.class
fi
rm -f -r ./org
#more < ./compile.log
cat compile.log
echo
echo Completed. Press Enter to exit...
read