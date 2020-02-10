#!/bin/sh
set -ex
wget http://archive.apache.org/dist/thrift/0.9.3/thrift-0.9.3.tar.gz
tar xzf thrift-0.9.3.tar.gz
cd thrift-0.9.3
sed -i -e 's#mvn.repo=http://repo1.maven.org/maven2#mvn.repo=https://repo1.maven.org/maven2#g' ./lib/java/build.properties ./lib/java/build.properties-e
./configure --without-qt4 --without-qt5 --without-csharp --without-erlang --without-nodejs --without-lua --without-python --without-perl --without-php --without-php_extension --without-dart --without-ruby --without-haskell --without-go --without-haxe --without-d
make
sudo make install
