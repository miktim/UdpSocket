#!/bin/bash

echo $(javac -version)
echo $(java -version)
if [ -f ./UdpSocket.jar ]; then
#  javac -cp ./UdpSocket.jar -d . BasicTest.java
  java  -cp ./UdpSocket.jar:. BasicTest.java
  java  -cp ./UdpSocket.jar:. ReceiverTest.java
rm -f *.class
else
  echo First make the UdpSocket.jar file or copy it from /dist.
fi
echo
echo Completed. Press Enter to exit...
read
