#!/bin/sh

for config in source dev dummy
do
  clojure -M:poly test :all with:$config
  if test $? -ne 0
  then
    echo "Tests failed with :$config"
    exit 1
  fi
done
