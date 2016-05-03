OpenBaton Openstack Plugin - Liberty branch
----------------

OpenBaton is an open source project providing a reference implementation of the NFVO and VNFM based on the [ETSI][NFV MANO] specification, is implemented in java using the [spring.io] framework. It consists of two main components: a NFVO and a generic VNFM. This project **openstack-plugin** contains an implementation of a plugin for OpenBaton system. This plugin is implemented with the help of plugin-sdk developed for the NFVO which allows NFVO to send requests to the plugin via AMQP server and allocate resources on Openstack. The default way of communication between the plugin and openstack is rabbitmq server. The plugin uses Apache Jclouds API and REST to communicate with Openstack. It is most widely used and tested with the Openstack-Kilo version to date. This is recommended and default Openstack distribution. However, if you want to use it with Openstack-Liberty, you can find the Liberty version of the plugin at [get.openbaton.org][get-openbaton-org-liberty] or in a specific branch of this project. Be aware, however, that liberty version uses the snapshot(not yet released) version of the jclouds API. 

##### Installation

If you are using source code (git) to install Openbaton, it is recommended that you go to [get.openbaton.org][get-openbaton-org] and download the stable version of the plugin from there. After this you will need to put the jar in the folder that is specified in file /etc/openbaton/openbaton.properties via the property plugin-installation-dir. You can specify where the log of the plugin will be with this parameter nfvo.plugin.log.path.
If you are using debian package then you will be presented with a choice of downloading plugins during the installation process.


You can also clone this project and build it with gradle yourself. After that the placement of the built jar file is the same as for jar that would have been downloaded from the repository.
#### Usage
If you have placed the plugin as it was mentioned earlier, then NFVO will automatically start it with the right parameters. The plugin however is by itself an application which can be started remotely by using CLI. For this, you will need to type this into console. 

```bash
$ java -jar path-to-plugin.jar openstack [rabbitmq-ip] [rabbitmq-port] [n-of-consumers] [user] [password]
```
```properties
rabbitmq-ip is the ip of the host where the rabbitmq server is installed and running
rabbitmq-port is the port on which the rabbitmq accepts the messages(it is usually 5672 by default) 
number-of-consumers specifies the number of actors that will accept the requests
```
### Development

Want to contribute? Great! Get in contact with us. You can find us on twitter @[openbaton]
You can look at [Plugin SDK][plugin-sdk-link] to see how the SDK is implemented or you can use it to implement your own plugin!

### News and Website
Information about OpenBaton can be found on our [website][website]. Follow us on Twitter @[openbaton].

### License

Copyright (c) 2015 Fraunhofer FOKUS. All rights reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[plugin-sdk-link]: https://github.com/openbaton/plugin-sdk
[nfvo-link]: https://github.com/openbaton/NFVO
[generic-link]:https://github.com/openbaton/generic-vnfm
[get-openbaton-org]:http://get.openbaton.org/plugins/stable/
[client-link]: https://github.com/openbaton/openbaton-client
[spring.io]:https://spring.io/
[NFV MANO]:http://docbox.etsi.org/ISG/NFV/Open/Published/gs_NFV-MAN001v010101p%20-%20Management%20and%20Orchestration.pdf
[openbaton]:http://twitter.com/openbaton
[website]:http://openbaton.github.io/
[get-openbaton-org-liberty]:http://get.openbaton.org/plugins/1.0.2-liberty-nighly/
