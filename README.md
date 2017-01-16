# net.yetamine.osgi.jdbc #

This repository provides an OSGi extender that adapts JDBC code using `java.sql.DriverManager` (both drivers and clients) on-the-fly to run within an OSGi container without any source code modification. Besides that, it publishes classic JDBC drivers as OSGi services and makes drivers published as OSGi services available for any code using the `DriverManager`.


## User's guide ##

Let's have a look how to use the extender when being a regular administrator of an OSGi application/server and/or a developer that writes some JDBC code.


### Requirements ###

* JRE 8 or newer.
* OSGi framework compatible with OSGi R5.


### Installation ###

The installation consists just of three steps:

1. Install the extender bundle.
2. Adjust the start level of the extender bundle.
3. Start or restart the container.

It is important that the extender bundle starts before any JDBC code, so that all the code could adapted in a consistent way. Installing the bundle when the container is not running should be preferred if the container allows that. Otherwise all bundles with JDBC code should be stopped (e.g., by setting a suitable framework start level) and the container should be restarted after the installation in order to ensure that the extender could adapt all JDBC code correctly.

**Installing the extender bundle** implies installing all its dependencies as well; fortunately, there are just two (their root packages are listed with the acceptable version range):

* *org.objectweb.asm @ [5.0,6)*
* *org.slf4j @ [1.7,2)*

More advanced containers like [Karaf](http://karaf.apache.org/) provide these dependencies out of the box and it is not necessary to install them, which reduces the installation procedure a lot.

**Adjusting the start level of the extender bundle** is required, as explained above, so that the extender bundle could start before any JDBC code and therefore be able to adapt it. Of course, it assumes that bundles in your container have some reasonable start levels assigned and there is a suitable start level, dedicated for system services, when the extender bundle could be started.

When the container is freshly started and the extender starts, everything should be alright and work without any further steps.


### Usage ###

As noted above, the extender acts as a two-way bridge that publishes JDBC drivers deployed in OSGi bundles as OSGi services implementing `java.sql.Driver` interface, so that any OSGi-based code may use the drivers directly, but it makes all such services available as drivers through `java.sql.DriverManager`. From the point of view of a developer, it is possible to write the JDBC code as usual, as if the code was not running in an OSGi container, e.g.:

```{java}
try (Connection connection = DriverManager.getConnection(url, properties)) {
    // Do something here
}
```

Because the extender allows using `DriverManager` in your code in the usual way, it makes possible to have a single code for both OSGi and non-OSGi environment. While OSGi services are powerful, using `DriverManager` satisfies most use cases and is far more easier and straight-forward.


### Remarks and limitations ###

* The extender does not publish, as OSGi services, the drivers that are not deployed as regular bundles in the container.  Drivers provided on the JVM classpath are available via `DriverManagerAdapter`, or via `DriverManager` (as they would have been without the extender anyway) together with all other available drivers, so they could be used anyway and usually with less troubles than without the extender.

* The extender does not search any drivers in fragment bundles. Firstly, it would lead to technically complex solutions (and might not work very well); secondly, using fragment bundles for drivers does not look like a good idea anyway, but still could be used as an easier escape route in some situations.

* A natural expectation by an OSGi developer could be that stopping a bundle causes all its drivers to be stopped as well. The extender tries to satisfy this expectation, although the driver design actually does not support restarting. So, the extender must fake stopping and (re)starting drivers and merely hides them to appear like stopped. Although nobody would spot this trick probably, let's honestly admit it for the case that someone did spot such a behavior and wondered whether it is a bug or what. No, it's not a bug, it's a (limited) feature.


### Troubleshooting ###

Nothing is perfect and it might happen that the default behaviour causes a trouble. For such cases, the extender provides a couple of hooks to tune its behaviour, namely to adjust the driver loading condition (by default, drivers are loaded when a bundle starts) and to prevent code adaptation selectively (so that the extender may ignore whatever is told).

Anyway, using this tweaking controls requires some programming and understanding the technical details (at least some of them). On the other hand, writing a small OSGi service can be still a small price for keeping your other code untouched, while enjoying the benefits of the extender. See `net.yetamine.osgi.jdbc.tweak` package for details.


## Technical details ##

Well, understanding some technical details (besides or before diving in the source) can quench one's curiosity, help to solve a trouble or inspire one to make its own solution or suggest an improvement.

How it works? The major source of inspiration and the example of the technique was gained from [Apache Aries SPI Fly](http://aries.apache.org/modules/spi-fly.html) that provides similar, but more complex and versatile bridge for Java `ServiceLoader`. Actually, `DriverManager` is a kind of specific and specialized service loader. But specific indeed, so SPI Fly does not save the day in this case…


### The problem ###

Using JDBC in OSGi is troublesome, because `DriverManager` contains a check that prevents a caller to see the driver whose class can't be loaded by the caller's classloader. In other words: the caller must be able to load the driver itself, else the caller can't use the driver. Obviously, this is completely against the service-oriented decoupling and can't work in modular environments where most classes are intentionally hidden behind module boundaries.


### The solution ###

It is not possible to change `DriverManager` and neither is possible to force everybody to rewrite code to use something else… and yet *the same* something. (Alright, Oracle could do both, but we are not Oracle.) But it is possible to patch existing code to use *something else*. Actually, OSGi provides an excellent tool for that: weaving hooks. This hook type allows inspecting and modifying bytecode of any class that the framework wants to load.

The extender uses a weaving hook to patch invocations of a subset of `DriverManager`'s methods and redirects these calls to its own mechanism. This allows intercepting all driver registrations and deregistrations (for drivers loaded from a bundle) as well as driver enumerations and connection requests (i.e., calls of a `getConnection` method). Hence, when a driver gets loaded, it registers self and thanks to the bytecode patch not at `DriverManager`, but at the extender which then publishes the driver as an OSGi service. Getting a connection searches such OSGi services (and therefore can accept native services that were registered by anybody) and *then* consults the original `DriverManager` to get even the drivers that were not loaded from any bundle, but from `DriverManager` itself from the JVM classpath. (So, OSGi-bundled drivers are preferred.)

Intercepting driver enumerations at `DriverManager` provides the other way of the bridging: it provides all drivers, from both `DriverManager` and from the OSGi service registry.

As noted, the extender must take care of bundle lifecycle: firstly, it needs to know when a bundle gets available to scan it for drivers to load; secondly, it has to unregister drivers, which it registered before, if a bundle state requires to prevent the drivers from further use. Technically, it requires to track both bundles and services, trigger driver loading and intercept class loading for patching the bytecode before loading a class. The tuning support requires additional service tracking and adjusting the lifecycle decisions according to the tuning hook responses.

The major problem with the lifecycle management is that a class (specified in the `META-INF/services`) registers one or more drivers when the class has been loaded and initialized. While it is possible to deregister a driver (`DriverManager` offers such a rare option), there is no reliable way to reload the class again to trigger registration of the driver again. This driver design imperfection forces the extender to load drivers once and then keep them alive as long as their bundles change the state to `UNINSTALLED` (then a bundle, including all its classes, can be indeed reloaded). It is possible to delay the loading – but after loading, a driver can be just hidden (i.e., it is unregistered from the service registry), never really stopped and started again.


## Prerequisites ##

For building this project is needed:

* JDK 8 or newer.
* Maven 3.3 or newer.

The requirements for *using* the library are summed up above.


## Licensing ##

The project is licensed under the [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0). Contributions to the project are welcome and accepted if they can be incorporated without the need of changing the license or license conditions and terms.


[![Yetamine logo](https://github.com/pdolezal/net.yetamine/raw/master/about/Yetamine_small.png "Our logo")](https://github.com/pdolezal/net.yetamine/blob/master/about/Yetamine_large.png)
