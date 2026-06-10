#!/bin/sh

rlwrap java -Xmx3g -Xms3g -cp ./sqltool/hsqldb-2.7.4.jar:./sqltool/sqltool-2.4.0.jar org.hsqldb.cmdline.SqlTool --rcFile "sqltool.rc" metlog "$@" 
