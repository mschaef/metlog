#!/bin/sh

rlwrap java -Xmx3g -Xms3g -cp ./sqltool/hsqldb-2.3.0.jar:./sqltool/sqltool-2.3.0.jar org.hsqldb.cmdline.SqlTool --rcFile "sqltool.rc" metlog "$@" 
