# net.yetamine.osgi.jdbc #

This repository provides an OSGi that adapts JDBC code (both drivers and clients) on-the-fly to run within an OSGi container without any code modification.


## Usage ##

Install the bundle with its dependencies in your favourite OSGi container ([Karaf](http://karaf.apache.org/) recommended). If you deploy JDBC drivers in the OSGi container, the drivers must be OSGi-fied, but the code needs no changes. Deploying drivers on the boot classpath an option as well, but this way gives up all benefits of the modular environment.


## Prerequisites ##

For building this project is needed:

* JDK 8 or newer.
* Maven 3.3 or newer.
* Yetamine parent POM: see [net.yetamine](http://github.com/pdolezal/net.yetamine).

For using the built library is needed:

* JRE 8 or newer.


## Licensing ##

The project is licensed under the [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0). Contributions to the project are welcome and accepted if they can be incorporated without the need of changing the license or license conditions and terms.


[![Yetamine logo](https://github.com/pdolezal/net.yetamine/raw/master/about/Yetamine_small.png "Our logo")](https://github.com/pdolezal/net.yetamine/blob/master/about/Yetamine_large.png)
