# net.yetamine.osgi.jdbc #

This repository provides an OSGi extender that adapts JDBC code using `java.sql.DriverManager` (both drivers and clients) on-the-fly to run within an OSGi container without any code modification. Besides that, it enables OSGi services to bind those drivers as regular OSGi services.


## Usage ##

Install the extender bundle with its dependencies in your favourite OSGi container and assign preferably a low start level to the bundle, which reduces the risk that the extender won't be able to intercept some code depending on `DriverManager`.

Management of the drivers that are deployed on the boot classpath and loaded by `DriverManager` has some limitations. Therefore drivers should be rather deployed as OSGi bundles in the container, which allows the extender to manage them with no need to interact with `DriverManager` and without implied limitations. Of course, if the driver implementation provides no OSGi manifest, it needs adapting to a bundle.


## Limitations

The used technique employs OSGi weaving hooks to patch loaded classes in runtime on-the-fly. This approach works for most cases well enough, although it can't intercept dynamic invocations like calling `DriverManager` via reflection (for which hardly any reason exists though). Using the weaving hook assumes that the hook can intercept loading all classes that do deal with `DriverManager` in order to patch them – this is the reason for the extender to be started early, which can be ensured by assigning a low start level for the extender bundle.

The extender can't manage any drivers that are managed by `DriverManager` already and can't even register them as individual OSGi services. Instead, a service implementing `DriverProvider` interface provides similar way to pick suitable drivers from a whole set of drivers; the extender registers an implementation of the `DriverProvider` which offers drivers registered as OSGi services (including those registered by other sources) and drivers available via `DriverManager`. It is actually a convenient way to choose a driver without fiddling too much with individual driver services.

Another limitation stems from the driver registration procedure: a driver becomes registered during loading a class that is declared as a service for `ServiceLoader`. There is no better lifecycle definition and the actual driver class can be a different class. Moreover, loading a class is a single-shot action. So, the extender does load the declared service class, which enables the service class to register one or more drivers, but does rather not unregister any drivers as long as the registering bundle remains resolved. Instead, it just registers and unregisters these drivers as OSGi services dynamically in order to reflect the state of their registering bundle.

For the sake of simplicity, drivers are not loaded from fragment bundles. Fragments may have wirings to multiple hosts which would make the resolution process more complex, while its value is questionable. Anyway, this provides an escape route for the cases where the driver class is loaded directly via `Class::forName` instead of via `DriverManager`, so attaching a fragment to such legacy bundles may solve some problems sufficiently even without using some of the tweaks like `WeavingFilter` and/or `BundleControl` that provide hooks for influencing the JDBC support behavior.


## Prerequisites ##

For building this project is needed:

* JDK 8 or newer.
* Maven 3.3 or newer.
* Yetamine parent POM: see [net.yetamine](http://github.com/pdolezal/net.yetamine).

For using the built library is needed:

* JRE 8 or newer.
* OSGi framework compatible with OSGi R5. (Recommending [Karaf](http://karaf.apache.org/) which provides all necessary out of the box.)


## Licensing ##

The project is licensed under the [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0). Contributions to the project are welcome and accepted if they can be incorporated without the need of changing the license or license conditions and terms.


[![Yetamine logo](https://github.com/pdolezal/net.yetamine/raw/master/about/Yetamine_small.png "Our logo")](https://github.com/pdolezal/net.yetamine/blob/master/about/Yetamine_large.png)
