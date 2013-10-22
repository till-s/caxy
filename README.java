INTRODUCTION
------------

caxy is now also available as an implementation in java.
This means that you don't have to compile anything but
you can use the pre-compiled application on any platform
supporting java (>= 1.6).

For general information about caxy and how to use it
consult the general README.

Authorship
----------
caxy was created by Till Straumann <strauman@slac.stanford.edu>

NOTE about ProxifiedSocketChannel.java
--------------------------------------

 The 'ProxifiedSocketChannel.java' file implements a class to
 enhance the CAJ native java CA-client implementation.
 It adds support for SOCKS4/SOCKS5.
  
 Note that this file is NOT directly RELATED or USED by caxy.
 It is meant to be used to patch CAJ.

RUNNING java caxy
-----------------

caxTo run java caxy you need jvm >= 1.6 and you start it

java -jar <path>/caxy.jar

The commandline options are mostly the same as for the C version
(see caxy.html or run caxy with '-h') with the following exceptions:

 -S    : 'server' mode runs always in the foreground.
 -f    : ignored.

 -n    : reverse DNS lookup of IP addresses (debugging mode) is 
         not supported. All addresses are dumped numerically. The
         -n option is not recognized.

 -v    : the java version only supports CATUN protocol version 3.
         There is no option to select the protocol version.

BUILDING java caxy
------------------

If you decide to hack around and you want to rebuild the
java version then read this section. If you just want to use
caxy then the pre-built 'caxy.jar' should be enough on any
platform that supports at least java-1.6 (you can rebuild
'caxy.jar' under 1.5. This will work, too, but you won't
have auto_addr_list support.)

For building the java version you need jdk and ant.
Simply chdir to the top directory (holding the file 'build.xml')
and issue 

    ant

To clean up select the 'clean' target

    ant clean

ACKNOWLEDGEMENT/LICENSE
-----------------------
java caxy uses the GNU 'Getopt' class which is released
under the LGPL and distributed in source (and compiled)
form with caxy.
When using the pre-built binaries then the respective terms
of the LGPL apply. Consult the license for details.
