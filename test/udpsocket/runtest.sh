#!/bin/bash

echo $(javac -version)
echo $(java -version)
if [ -f ./UdpSocket.jar ]; then
#  javac -cp ./UdpSocket.jar -d . BasicTest.java
  java  -cp ./UdpSocket.jar:. BasicTest.java
rm -f *.class
else
  echo First make the UdpSocket.jar file.
fi
echo
echo Completed. Press Enter to exit...
read
