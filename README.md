OpenBaton Plugin SDK
----------------

OpenBaton is an open source project providing a reference implementation of the NFVO and VNFM based on the [ETSI][NFV MANO] specification, is implemented in java using the [spring.io] framework. It consists of two main components: a NFVO and a generic VNFM. This project **plugin-sdk** contains modules that are needed to implement a plugin for OpenBaton system.

#### How does this works? 

An OpenBaton Plugin is a RMI Server that connects to the NFVO or any other rmiregistry with access to the OpenBaton catalogue as _codebase_. It offers an implementation of an interface that is used by NFVO. by default NFVO starts a rmiregistry at localhost:1099.

In order to create a VIM plugin for OpenBaton system you need to add to your gradle build file:

```gradle
buildscript {
    dependencies {
        classpath("org.springframework.boot:spring-boot-gradle-plugin:1.2.6.RELEASE")
    }
}

apply plugin: 'spring-boot'

project.ext{
	mainClass = 'path.to.the.StarterClass'
}

apply plugin: 'java'
apply plugin: 'maven'
repositories {
    mavenCentral()
    maven {
        url "http://193.175.132.176:8081/nexus/content/groups/public"
    }
}

dependencies {
    compile 'org.openbaton:plugin-sdk:0.6'
}
```

Than create a class that implement ClientInterfaces and the inherited methods.
Create another starter class and set the path to it in a variable mainClass.

the starter class can be like the following:

```java
import org.openbaton.plugin.PluginStarter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StarterClass {

    private static Logger log = LoggerFactory.getLogger(PluginStarter.class);

    public static void main(String[] args) {
        log.info("params are: pluginName registryIp registryPort\ndefault is openstack-plugin localhost 1099");

        if (args.length > 1)
            PluginStarter.run(OpenstackClient.class, args[0], args[1], Integer.parseInt(args[2]));
        else
            PluginStarter.run(OpenstackClient.class, "openstack-plugin", "localhost");
    }
}
```

Then, compile & run it

#### Version
0.6

##### Installation

No installation required.

### Development

Want to contribute? Great! Get in contact with us. You can find us on twitter @[openbaton]

### News and Website
Information about OpenBaton can be found on our website. Follow us on Twitter @[openbaton].

### License

[nfvo-link]: https://github.com/openbaton/NFVO
[generic-link]:https://github.com/openbaton/generic-vnfm
[client-link]: https://github.com/openbaton/openbaton-client
[spring.io]:https://spring.io/
[NFV MANO]:http://docbox.etsi.org/ISG/NFV/Open/Published/gs_NFV-MAN001v010101p%20-%20Management%20and%20Orchestration.pdf
[openbaton]:http://twitter.com/openbaton
