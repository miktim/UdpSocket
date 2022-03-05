#!/bin/bash

echo $(javac -version)
echo $(java -version)
if [ -f ../../dist/udpsocket-1.0.2.jar ]; then
  javac -cp ../../dist/udpsocket-1.0.2.jar UdpSocketTest.java
  java  -cp ../../dist/udpsocket-1.0.2.jar:. UdpSocketTest
  rm -f *.class
else
  echo First make the udpsocket-1.0.2.jar file.
fi
echo
echo Completed. Press Enter to exit...
read
