#!/bin/bash
#export MALLOC_ARENA_MAX=4 # Stop the JVM from being allowed to use up all of
# Docker's virtual memory. Use if it's a problem
# see https://siddhesh.in/posts/malloc-per-thread-arenas-in-glibc.html

CGROUPS_MEM=$(cat /sys/fs/cgroup/memory/memory.limit_in_bytes)
MEMINFO_MEM=$(($(awk '/MemTotal/ {print $2}' /proc/meminfo)*1024))
MEM=$(($MEMINFO_MEM>$CGROUPS_MEM?$CGROUPS_MEM:$MEMINFO_MEM))
JVM_DASH_HEAP_RATIO=${JVM_DASH_HEAP_RATIO:-0.8}
XMX=$(awk '{printf("%d",$1*$2/1024^2)}' <<< " ${MEM} ${JVM_DASH_HEAP_RATIO} ")
# Use the container memory limit to set max heap size so that the GC
# knows to collect before it's hard-stopped by the container environment,
# causing OOM exception.

: ${DASH_JAVA_OPTS:='-XX:+UseG1GC -server'}

/usr/bin/java $DASH_JAVA_OPTS \
              "-Xmx${XMX}m" \
              -jar /opt/onyx-dashboard.jar ${ZOOKEEPER_ADDR}
