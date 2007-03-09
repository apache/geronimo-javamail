
Building
========

To build you will need:

 * J2SE SDK 1.4.2+ (http://java.sun.com/j2se/1.4.2)
 * Maven 2.0.4+ (http://maven.apache.org)

NOTE: If you use JDK 1.5 you may run into unexpected errors, so stick to 1.4.

To build all changes incrementally:

    mvn install

To perform clean builds, which are sometimes needed after some changes to the 
source tree:

    mvn clean install

