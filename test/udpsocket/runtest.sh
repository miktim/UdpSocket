#!/bin/bash

echo $(javac -version)
echo $(java -version)
if [ -f ../../dist/UdpSocket.jar ]; then
  javac -cp ../../dist/UdpSocket.jar UdpSocketTest.java
  java  -cp ../../dist/UdpSocket.jar:. UdpSocketTest
  rm -f *.class
else
  echo First make the UdpSocket.jar file.
fi
echo
echo Completed. Press Enter to exit...
read
