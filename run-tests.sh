#!/bin/sh

for alias in "" ":olical"
do
  for config in source dev dummy
  do
    clojure -M:poly$alias test :all with:$config
    if test $? -ne 0
    then
      echo "Tests failed with :$config (alias: ${alias:-none})"
      exit 1
    fi
  done
done
