#!/bin/bash

echo $(javac -version)
echo $(java -version)
if [ -f ../../dist/udpsocket-3.0.0.jar ]; then
  javac -cp ../../dist/udpsocket-3.0.0.jar UdpSocketTest.java
  java  -cp ../../dist/udpsocket-3.0.0.jar:. UdpSocketTest
  rm -f *.class
else
  echo First make the udpsocket-3.0.0.jar file.
fi
echo
echo Completed. Press Enter to exit...
read
