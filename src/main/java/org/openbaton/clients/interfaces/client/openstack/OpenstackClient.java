/*
 *
 *  *
 *  *  * Copyright (c) 2016 Open Baton (http://www.openbaton.org)
 *  *  *
 *  *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  *  * you may not use this file except in compliance with the License.
 *  *  * You may obtain a copy of the License at
 *  *  *
 *  *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *  *
 *  *  * Unless required by applicable law or agreed to in writing, software
 *  *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  * See the License for the specific language governing permissions and
 *  *  * limitations under the License.
 *  *
 *
 */

package org.openbaton.clients.interfaces.client.openstack;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.util.SubnetUtils;
import org.jclouds.ContextBuilder;
import org.jclouds.collect.PagedIterable;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.domain.Credentials;
import org.jclouds.io.Payload;
import org.jclouds.io.payloads.ByteArrayPayload;
import org.jclouds.io.payloads.InputStreamPayload;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.openstack.glance.v1_0.GlanceApi;
import org.jclouds.openstack.glance.v1_0.domain.ContainerFormat;
import org.jclouds.openstack.glance.v1_0.domain.DiskFormat;
import org.jclouds.openstack.glance.v1_0.domain.ImageDetails;
import org.jclouds.openstack.glance.v1_0.features.ImageApi;
import org.jclouds.openstack.glance.v1_0.options.CreateImageOptions;
import org.jclouds.openstack.glance.v1_0.options.ListImageOptions;
import org.jclouds.openstack.glance.v1_0.options.UpdateImageOptions;
import org.jclouds.openstack.keystone.v2_0.config.CredentialTypes;
import org.jclouds.openstack.keystone.v2_0.config.KeystoneProperties;
import org.jclouds.openstack.keystone.v2_0.domain.Access;
import org.jclouds.openstack.keystone.v2_0.domain.Endpoint;
import org.jclouds.openstack.neutron.v2.NeutronApi;
import org.jclouds.openstack.neutron.v2.domain.ExternalGatewayInfo;
import org.jclouds.openstack.neutron.v2.domain.FloatingIP;
import org.jclouds.openstack.neutron.v2.domain.IP;
import org.jclouds.openstack.neutron.v2.domain.Network.CreateNetwork;
import org.jclouds.openstack.neutron.v2.domain.Network.UpdateNetwork;
import org.jclouds.openstack.neutron.v2.domain.Port;
import org.jclouds.openstack.neutron.v2.domain.Router;
import org.jclouds.openstack.neutron.v2.domain.RouterInterface;
import org.jclouds.openstack.neutron.v2.domain.Subnet.CreateSubnet;
import org.jclouds.openstack.neutron.v2.domain.Subnet.UpdateSubnet;
import org.jclouds.openstack.neutron.v2.extensions.FloatingIPApi;
import org.jclouds.openstack.neutron.v2.extensions.RouterApi;
import org.jclouds.openstack.neutron.v2.features.NetworkApi;
import org.jclouds.openstack.neutron.v2.features.PortApi;
import org.jclouds.openstack.neutron.v2.features.SubnetApi;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.domain.Address;
import org.jclouds.openstack.nova.v2_0.domain.RebootType;
import org.jclouds.openstack.nova.v2_0.features.FlavorApi;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.jclouds.openstack.v2_0.domain.Resource;
import org.jclouds.openstack.v2_0.options.PaginationOptions;
import org.jclouds.scriptbuilder.ScriptBuilder;
import org.jclouds.scriptbuilder.domain.OsFamily;
import org.openbaton.catalogue.mano.common.DeploymentFlavour;
import org.openbaton.catalogue.mano.descriptor.VNFDConnectionPoint;
import org.openbaton.catalogue.nfvo.NFVImage;
import org.openbaton.catalogue.nfvo.Network;
import org.openbaton.catalogue.nfvo.Quota;
import org.openbaton.catalogue.nfvo.Server;
import org.openbaton.catalogue.nfvo.Subnet;
import org.openbaton.catalogue.nfvo.VimInstance;
import org.openbaton.exceptions.VimDriverException;
import org.openbaton.plugin.PluginStarter;
import org.openbaton.vim.drivers.interfaces.VimDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import static org.jclouds.scriptbuilder.domain.Statements.exec;

/**
 * Created by mpa on 06.05.15.
 */
public class OpenstackClient extends VimDriver {

  private static final Pattern PATTERN =
      Pattern.compile("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");
  Iterable<Module> modules;
  Properties overrides;
  private static Logger log = LoggerFactory.getLogger(OpenstackClient.class);
  private static Lock lock;
  private Gson gson = new GsonBuilder().create();

  public OpenstackClient() throws RemoteException {
    super();
    init();
  }

  public static boolean validate(final String ip) {
    return PATTERN.matcher(ip).matches();
  }

  public static void main(String[] args)
      throws NoSuchMethodException, IOException, InstantiationException, TimeoutException,
          IllegalAccessException, InvocationTargetException {
    OpenstackClient.lock = new ReentrantLock();
    if (args.length == 6) {
      PluginStarter.registerPlugin(
          OpenstackClient.class,
          args[0],
          args[1],
          Integer.parseInt(args[2]),
          Integer.parseInt(args[3]),
          args[4],
          args[5]);
    } else if (args.length == 4) {
      PluginStarter.registerPlugin(
          OpenstackClient.class,
          args[0],
          args[1],
          Integer.parseInt(args[2]),
          Integer.parseInt(args[3]));
    } else {
      PluginStarter.registerPlugin(OpenstackClient.class, "openstack", "localhost", 5672, 10);
    }
    /*OpenstackClient client = new OpenstackClient();
    client.init();
    VimInstance vimInstance = new VimInstance();
    vimInstance.setUsername("openbaton");
    vimInstance.setPassword("openbaton");
    vimInstance.setTenant("slice-low-latency");
    vimInstance.setAuthUrl("http://172.27.101.16:5000/v2.0");
    vimInstance.setName("orange-box");

    try {
      System.out.println(client.listFreeFloatingIps(vimInstance));
    } catch (VimDriverException e) {
      e.printStackTrace();
    }*/
  }

  public void init() {
    modules = ImmutableSet.<Module>of(new SLF4JLoggingModule());
    overrides = new Properties();
    overrides.setProperty(KeystoneProperties.CREDENTIAL_TYPE, CredentialTypes.PASSWORD_CREDENTIALS);
    String sslChecksDisabled = properties.getProperty("disable-ssl-certificate-checks", "false");
    log.debug("Disable SSL certificate checks: {}", sslChecksDisabled);
    if (sslChecksDisabled.trim().equals("true")) {
      DisableSSLValidation.disableChecks();
    }
  }

  private String getZone(VimInstance vimInstance) {
    NovaApi novaApi =
        ContextBuilder.newBuilder("openstack-nova")
            .endpoint(vimInstance.getAuthUrl())
            .credentials(
                vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                vimInstance.getPassword())
            .modules(modules)
            .overrides(overrides)
            .buildApi(NovaApi.class);
    Set<String> zones = novaApi.getConfiguredRegions();
    log.debug("Available openstack environment zones: " + zones);
    String zone = null;
    if (vimInstance.getLocation() != null) {
      if (vimInstance.getLocation().getName() != null) {
        log.debug(
            "Finding Location of openstack environment: " + vimInstance.getLocation().getName());
        for (String region : zones) {
          if (region.equals(vimInstance.getLocation().getName())) {
            zone = region;
            log.debug("Found Location of openstack environment: " + zone);
            break;
          }
        }
        if (zone == null) {
          log.warn(
              "Not found Location '"
                  + vimInstance.getLocation().getName()
                  + "'of openstack environment. Selecting a random one...");
        }
      } else {
        log.warn(
            "Location of openstack environment is not defined properly -> Missing Name of the Location. Selecting a "
                + "random one...");
      }
    } else {
      log.debug("Location of openstack environment is not defined. Selecting a random one...");
    }
    if (zone == null) {
      for (String zn : zones) {
        if (zn.contains("nova")) {
          return zn;
        }
      }
      log.debug("Selecting a random Location of openstack environment from: " + zones);
      zone = zones.iterator().next();
      log.debug("Selected Location of openstack environment: '" + zone + "'");
    }
    return zone;
  }

  @Override
  public Server launchInstance(
      VimInstance vimInstance,
      String name,
      String imageId,
      String flavorId,
      String keypair,
      Set<VNFDConnectionPoint> network,
      Set<String> secGroup,
      String userData)
      throws VimDriverException {
    try {
      NovaApi novaApi =
          ContextBuilder.newBuilder("openstack-nova")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NovaApi.class);

      List<String> networkIds = getNetowrkIdsFromNames(vimInstance, network);

      ServerApi serverApi = novaApi.getServerApi(getZone(vimInstance));
      String script = new ScriptBuilder().addStatement(exec(userData)).render(OsFamily.UNIX);
      CreateServerOptions options;
      if (keypair.equals("")) {
        options =
            CreateServerOptions.Builder.networks(networkIds)
                .securityGroupNames(secGroup)
                .userData(script.getBytes());

      } else {

        options =
            CreateServerOptions.Builder.keyPairName(keypair)
                .networks(networkIds)
                .securityGroupNames(secGroup)
                .userData(script.getBytes());
      }

      log.debug(
          "Keypair: "
              + keypair
              + ", SecGroup, "
              + secGroup
              + ", imageId: "
              + imageId
              + ", flavorId: "
              + flavorId
              + ", networks: "
              + networkIds);
      String extId = serverApi.create(name, imageId, flavorId, options).getId();
      Server server = getServerById(vimInstance, extId);
      log.debug("Created Server: " + server);
      return server;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  private List<String> getNetowrkIdsFromNames(
      VimInstance vimInstance, Set<VNFDConnectionPoint> networks) throws VimDriverException {
    List<String> res = new ArrayList<>();
    Set<Network> networkList = vimInstance.getNetworks();

    Gson gson = new Gson();
    String oldVNFDCP = gson.toJson(networks);
    Set<VNFDConnectionPoint> newNetworks =
        gson.fromJson(oldVNFDCP, new TypeToken<Set<VNFDConnectionPoint>>() {}.getType());

    VNFDConnectionPoint[] vnfdConnectionPoints = newNetworks.toArray(new VNFDConnectionPoint[0]);
    Arrays.sort(
        vnfdConnectionPoints,
        new Comparator<VNFDConnectionPoint>() {
          @Override
          public int compare(VNFDConnectionPoint o1, VNFDConnectionPoint o2) {
            return o1.getInterfaceId() - o2.getInterfaceId();
          }
        });

    for (VNFDConnectionPoint vnfdConnectionPoint : vnfdConnectionPoints) {
      for (Network network : networkList) {
        if ((vnfdConnectionPoint.getVirtual_link_reference().equals(network.getName())
            || vnfdConnectionPoint.getVirtual_link_reference().equals(network.getExtId()))) {
          if (!res.contains(network.getExtId())) res.add(network.getExtId());
        }
      }
    }
    log.debug("result " + res);
    return res;
  }

  public Server launchInstanceAndWait(
      VimInstance vimInstance,
      String name,
      String imageId,
      String flavorId,
      String keypair,
      Set<VNFDConnectionPoint> network,
      Set<String> secGroup,
      String userData)
      throws VimDriverException {
    return launchInstanceAndWait(
        vimInstance, name, imageId, flavorId, keypair, network, secGroup, userData, null, null);
  }

  @Override
  public Server launchInstanceAndWait(
      VimInstance vimInstance,
      String name,
      String imageId,
      String flavorId,
      String keypair,
      Set<VNFDConnectionPoint> network,
      Set<String> secGroup,
      String userData,
      Map<String, String> floatingIp,
      Set<org.openbaton.catalogue.security.Key> keys)
      throws VimDriverException {
    boolean bootCompleted = false;
    if (keys != null && !keys.isEmpty()) {
      userData = addKeysToUserData(userData, keys);
    }
    log.info("Deploying VM on VimInstance: " + vimInstance.getName());
    log.debug("UserData is:\n " + userData + " \n");
    Server server =
        launchInstance(vimInstance, name, imageId, flavorId, keypair, network, secGroup, userData);
    log.info(
        "Deployed VM ( "
            + server.getName()
            + " ) with extId: "
            + server.getExtId()
            + " in status "
            + server.getStatus());
    while (bootCompleted == false) {
      log.debug("Waiting for VM with hostname: " + name + " to finish the launch");
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      server = getServerById(vimInstance, server.getExtId());
      if (server.getStatus().equals("ACTIVE")) {
        log.debug("Finished deployment of VM with hostname: " + name);
        bootCompleted = true;
      }
      if (server.getStatus().equals("ERROR")) {
        log.error("Failed to launch VM with hostname: " + name + " -> Went into ERROR");
        VimDriverException vimDriverException = new VimDriverException(server.getExtendedStatus());
        vimDriverException.setServer(server);
        throw vimDriverException;
      }
    }
    if (floatingIp != null && floatingIp.size() > 0) {
      lock.lock();
      log.debug("Assigning FloatingIPs to VM with hostname: " + name);
      log.debug("FloatingIPs are: " + floatingIp);
      int freeIps = listFreeFloatingIps(vimInstance).size();
      int ipsNeeded = floatingIp.size();
      if (freeIps < ipsNeeded) {
        log.info(
            "Insufficient number of ips allocated to tenant, will try to allocate more ips from pool");
        log.debug("Getting the pool name of a floating ip pool");
        String pool_name = getIpPoolName(vimInstance);
        get_allocated(vimInstance, pool_name, ipsNeeded - freeIps);
      }
      if (listFreeFloatingIps(vimInstance).size() >= floatingIp.size()) {
        for (Map.Entry<String, String> fip : floatingIp.entrySet()) {
          associateFloatingIpToNetwork(vimInstance, server, fip);
          log.info(
              "Assigned FloatingIPs to VM with hostname: "
                  + name
                  + " -> FloatingIPs: "
                  + server.getFloatingIps());
        }
      } else {
        log.error(
            "Cannot assign FloatingIPs to VM with hostname: " + name + ". No FloatingIPs left...");
      }
      lock.unlock();
    }
    return server;
  }

  private String addKeysToUserData(
      String userData, Set<org.openbaton.catalogue.security.Key> keys) {
    log.debug("Going to add all keys: " + keys.size());
    userData += "\n";
    userData += "for x in `find /home/ -name authorized_keys`; do\n";
    String oldKeys = gson.toJson(keys);

    Set<org.openbaton.catalogue.security.Key> keysSet =
        new Gson()
            .fromJson(
                oldKeys, new TypeToken<Set<org.openbaton.catalogue.security.Key>>() {}.getType());

    for (org.openbaton.catalogue.security.Key key : keysSet) {
      log.debug("Adding key: " + key.getName());
      userData += "\techo \"" + key.getPublicKey() + "\" >> $x\n";
    }
    userData += "done\n";
    return userData;
  }

  private String getNetworkIdByName(VimInstance vimInstance, String key) throws VimDriverException {
    for (Network n : this.listNetworks(vimInstance)) {
      if (n.getName().equals(key)) {
        return n.getExtId();
      }
    }
    return null;
  }

  public void rebootServer(VimInstance vimInstance, String extId, RebootType type)
      throws VimDriverException {
    try {
      NovaApi novaApi =
          ContextBuilder.newBuilder("openstack-nova")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NovaApi.class);
      ServerApi serverApi = novaApi.getServerApi(getZone(vimInstance));
      serverApi.reboot(extId, type);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  public void deleteServerById(VimInstance vimInstance, String extId) throws VimDriverException {
    try {
      NovaApi novaApi =
          ContextBuilder.newBuilder("openstack-nova")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NovaApi.class);
      ServerApi serverApi = novaApi.getServerApi(getZone(vimInstance));
      serverApi.delete(extId);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  @Override
  public void deleteServerByIdAndWait(VimInstance vimInstance, String extId)
      throws VimDriverException {
    boolean deleteCompleted = false;
    log.debug("Deleting VM with ExtId: " + extId);
    deleteServerById(vimInstance, extId);
    while (deleteCompleted == false) {
      log.debug("Waiting until VM with ExtId: " + extId + " is deleted...");
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      try {
        getServerById(vimInstance, extId);
      } catch (NullPointerException e) {
        deleteCompleted = true;
        log.info("Deleted VM with ExtId: " + extId);
      }
    }
  }

  @Override
  public List<NFVImage> listImages(VimInstance vimInstance) throws VimDriverException {
    log.debug("Listing images for VimInstance with name: " + vimInstance.getName());
    try {
      GlanceApi glanceApi =
          ContextBuilder.newBuilder("openstack-glance")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(GlanceApi.class);
      ImageApi imageApi = glanceApi.getImageApi(getZone(vimInstance));
      ListImageOptions listImageOptions = new ListImageOptions();
      listImageOptions.limit(1000);
      List<NFVImage> images = new ArrayList<NFVImage>();
      for (ImageDetails jcloudsImage : imageApi.listInDetail(listImageOptions).toList()) {
        NFVImage image = new NFVImage();
        log.debug("Found image: " + jcloudsImage.getName());
        image.setName(jcloudsImage.getName());
        image.setExtId(jcloudsImage.getId());
        image.setMinRam(jcloudsImage.getMinRam());
        image.setMinDiskSpace(jcloudsImage.getMinDisk());
        image.setCreated(jcloudsImage.getCreatedAt());
        image.setUpdated(jcloudsImage.getUpdatedAt());
        image.setIsPublic(jcloudsImage.isPublic());
        image.setDiskFormat(jcloudsImage.getDiskFormat().toString().toUpperCase());
        image.setContainerFormat(jcloudsImage.getContainerFormat().toString().toUpperCase());
        images.add(image);
      }
      log.info(
          "Listed images for VimInstance with name: "
              + vimInstance.getName()
              + " -> Images: "
              + images);
      return images;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  @Override
  public List<Server> listServer(VimInstance vimInstance) throws VimDriverException {
    log.debug("Listing all VMs on VimInstance with name: " + vimInstance.getName());
    try {
      List<Server> servers = new ArrayList<Server>();
      NovaApi novaApi =
          ContextBuilder.newBuilder("openstack-nova")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NovaApi.class);
      ServerApi serverApi = novaApi.getServerApi(getZone(vimInstance));
      String tenantId = getTenantId(vimInstance);
      for (org.jclouds.openstack.nova.v2_0.domain.Server jcloudsServer :
          serverApi.listInDetail().concat()) {
        if (jcloudsServer.getTenantId().equals(tenantId)) {
          log.debug("Found jclouds VM: " + jcloudsServer);
          Server server = new Server();
          server.setExtId(jcloudsServer.getId());
          server.setName(jcloudsServer.getName());
          server.setStatus(jcloudsServer.getStatus().value());
          server.setExtendedStatus(jcloudsServer.getExtendedStatus().toString());
          if (jcloudsServer.getExtendedAttributes().isPresent()) {
            server.setHostName(jcloudsServer.getExtendedAttributes().get().getHostName());
            server.setHypervisorHostName(
                jcloudsServer.getExtendedAttributes().get().getHypervisorHostName());
          }
          HashMap<String, List<String>> ipMap = new HashMap<String, List<String>>();
          for (String key : jcloudsServer.getAddresses().keys()) {
            List<String> ips = new ArrayList<String>();
            for (Address address : jcloudsServer.getAddresses().get(key)) {
              ips.add(address.getAddr());
            }
            ipMap.put(key, ips);
          }
          server.setIps(ipMap);
          server.setFloatingIps(new HashMap<String, String>());
          server.setCreated(jcloudsServer.getCreated());
          server.setUpdated(jcloudsServer.getUpdated());
          Resource image = jcloudsServer.getImage();
          if (image != null) {
            server.setImage(getImageById(vimInstance, image.getId()));
          } else {
            log.warn("The image this server is using was deleted");
          }
          Resource flavor = jcloudsServer.getFlavor();
          if (flavor != null) {
            server.setFlavor(getFlavorById(vimInstance, flavor.getId()));
          } else {
            log.warn("The flavor this server is using was deleted");
          }
          log.debug("Found VM: " + server);
          servers.add(server);
        }
      }
      log.info(
          "Listed all VMs on VimInstance with name: "
              + vimInstance.getName()
              + " -> VMs: "
              + servers);
      return servers;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  private Server getServerById(VimInstance vimInstance, String extId) throws VimDriverException {
    log.debug("Finding VM by ID: " + extId + " on VimInstance with name: " + vimInstance.getName());
    try {
      NovaApi novaApi =
          ContextBuilder.newBuilder("openstack-nova")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NovaApi.class);
      ServerApi serverApi = novaApi.getServerApi(getZone(vimInstance));
      org.jclouds.openstack.nova.v2_0.domain.Server jcloudsServer = serverApi.get(extId);
      log.debug(
          "Found jclouds VM by ID: "
              + extId
              + " on VimInstance with name: "
              + vimInstance.getName()
              + " -> VM: "
              + jcloudsServer);
      Server server = new Server();
      server.setExtId(jcloudsServer.getId());
      server.setName(jcloudsServer.getName());
      server.setStatus(jcloudsServer.getStatus().value());
      server.setExtendedStatus(jcloudsServer.getExtendedStatus().toString());
      HashMap<String, List<String>> ipMap = new HashMap<>();

      for (String key : jcloudsServer.getAddresses().keys()) {
        List<String> ips = new ArrayList<>();
        for (Address address : jcloudsServer.getAddresses().get(key)) {
          ips.add(address.getAddr());
        }
        ipMap.put(key, ips);
      }

      server.setIps(ipMap);
      server.setFloatingIps(new HashMap<String, String>());
      server.setCreated(jcloudsServer.getCreated());
      server.setUpdated(jcloudsServer.getUpdated());
      Resource image = jcloudsServer.getImage();
      if (image != null) {
        server.setImage(getImageById(vimInstance, image.getId()));
      } else {
        log.warn("The image this server is using was deleted");
      }
      Resource flavor = jcloudsServer.getFlavor();
      if (flavor != null) {
        server.setFlavor(getFlavorById(vimInstance, flavor.getId()));
      } else {
        log.warn("The flavor this server is using was deleted");
      }
      log.info(
          "Found VM by ID: "
              + extId
              + " on VimInstance with name: "
              + vimInstance.getName()
              + " -> VM: "
              + server);
      return server;
    } catch (NullPointerException e) {
      log.debug(
          "Not found jclouds VM by ID: "
              + extId
              + " on VimInstance with name: "
              + vimInstance.getName());
      throw new NullPointerException(
          "Not found Server with ExtId: "
              + extId
              + " on VimInstance with name: "
              + vimInstance.getName());
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  @Override
  public NFVImage addImage(VimInstance vimInstance, NFVImage image, byte[] imageFile)
      throws VimDriverException {
    NFVImage addedImage =
        addImage(
            vimInstance,
            image.getName(),
            new ByteArrayInputStream(imageFile),
            image.getDiskFormat(),
            image.getContainerFormat(),
            image.getMinDiskSpace(),
            image.getMinRam(),
            image.isPublic());
    image.setName(addedImage.getName());
    image.setExtId(addedImage.getExtId());
    image.setCreated(addedImage.getCreated());
    image.setUpdated(addedImage.getUpdated());
    image.setMinDiskSpace(addedImage.getMinDiskSpace());
    image.setMinRam(addedImage.getMinRam());
    image.setIsPublic(addedImage.isPublic());
    image.setDiskFormat(addedImage.getDiskFormat());
    image.setContainerFormat(addedImage.getContainerFormat());
    return image;
  }

  private NFVImage addImage(
      VimInstance vimInstance,
      String name,
      InputStream payload,
      String diskFormat,
      String containerFormat,
      long minDisk,
      long minRam,
      boolean isPublic)
      throws VimDriverException {
    log.debug(
        "Adding Image (with image file) with name: "
            + name
            + " to VimInstance with name: "
            + vimInstance.getName());
    try {
      GlanceApi glanceApi =
          ContextBuilder.newBuilder("openstack-glance")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(GlanceApi.class);
      ImageApi imageApi = glanceApi.getImageApi(getZone(vimInstance));
      CreateImageOptions createImageOptions = new CreateImageOptions();
      createImageOptions.minDisk(minDisk);
      createImageOptions.minRam(minRam);
      createImageOptions.isPublic(isPublic);
      createImageOptions.diskFormat(DiskFormat.valueOf(diskFormat));
      createImageOptions.containerFormat(ContainerFormat.valueOf(containerFormat));
      log.debug("Initialized jclouds Image: " + createImageOptions);
      Payload jcloudsPayload = new InputStreamPayload(payload);
      try {
        ByteArrayOutputStream bufferedPayload = new ByteArrayOutputStream();
        int read = 0;
        byte[] bytes = new byte[1024];
        while ((read = payload.read(bytes)) != -1) {
          bufferedPayload.write(bytes, 0, read);
        }
        bufferedPayload.flush();
        jcloudsPayload = new ByteArrayPayload(bufferedPayload.toByteArray());
      } catch (IOException e) {
        log.error(e.getMessage(), e);
        throw new VimDriverException(e.getMessage());
      }
      ImageDetails imageDetails =
          imageApi.create(name, jcloudsPayload, new CreateImageOptions[] {createImageOptions});
      log.debug(
          "Added jclouds Image: "
              + imageDetails
              + " to VimInstance with name: "
              + vimInstance.getName());
      NFVImage image = new NFVImage();
      image.setName(imageDetails.getName());
      image.setExtId(imageDetails.getId());
      image.setCreated(imageDetails.getCreatedAt());
      image.setUpdated(imageDetails.getUpdatedAt());
      image.setMinDiskSpace(imageDetails.getMinDisk());
      image.setMinRam(imageDetails.getMinRam());
      image.setIsPublic(imageDetails.isPublic());
      image.setDiskFormat(imageDetails.getDiskFormat().toString().toUpperCase());
      image.setContainerFormat(imageDetails.getContainerFormat().toString().toUpperCase());
      log.info(
          "Added Image with name: "
              + name
              + " to VimInstance with name: "
              + vimInstance.getName()
              + " -> Image: "
              + image);
      return image;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  @Override
  public NFVImage addImage(VimInstance vimInstance, NFVImage image, String image_url)
      throws VimDriverException {
    NFVImage addedImage =
        addImage(
            vimInstance,
            image.getName(),
            image_url,
            image.getDiskFormat(),
            image.getContainerFormat(),
            image.getMinDiskSpace(),
            image.getMinRam(),
            image.isPublic());
    image.setName(addedImage.getName());
    image.setExtId(addedImage.getExtId());
    image.setCreated(addedImage.getCreated());
    image.setUpdated(addedImage.getUpdated());
    image.setMinDiskSpace(addedImage.getMinDiskSpace());
    image.setMinRam(addedImage.getMinRam());
    image.setIsPublic(addedImage.isPublic());
    image.setDiskFormat(addedImage.getDiskFormat());
    image.setContainerFormat(addedImage.getContainerFormat());
    return image;
  }

  private NFVImage addImage(
      VimInstance vimInstance,
      String name,
      String image_url,
      String diskFormat,
      String containerFromat,
      long minDisk,
      long minRam,
      boolean isPublic)
      throws VimDriverException {
    log.debug(
        "Adding Image (with image url) with name: "
            + name
            + " to VimInstance with name: "
            + vimInstance.getName());
    try {
      GlanceApi glanceApi =
          ContextBuilder.newBuilder("openstack-glance")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(GlanceApi.class);
      ImageApi imageApi = glanceApi.getImageApi(getZone(vimInstance));
      CreateImageOptions createImageOptions = new CreateImageOptions();
      createImageOptions.minDisk(minDisk);
      createImageOptions.minRam(minRam);
      createImageOptions.isPublic(isPublic);
      createImageOptions.diskFormat(DiskFormat.valueOf(diskFormat));
      createImageOptions.containerFormat(ContainerFormat.valueOf(containerFromat));
      createImageOptions.copyFrom(image_url);
      log.debug("Initialized jclouds Image: " + createImageOptions);
      //Create the Image
      ImageDetails imageDetails =
          imageApi.reserve(name, new CreateImageOptions[] {createImageOptions});
      log.debug(
          "Added jclouds Image: "
              + imageDetails
              + " to VimInstance with name: "
              + vimInstance.getName());
      NFVImage image = new NFVImage();
      image.setName(imageDetails.getName());
      image.setExtId(imageDetails.getId());
      image.setCreated(imageDetails.getCreatedAt());
      image.setUpdated(imageDetails.getUpdatedAt());
      image.setMinDiskSpace(imageDetails.getMinDisk());
      image.setMinRam(imageDetails.getMinRam());
      image.setIsPublic(imageDetails.isPublic());
      image.setDiskFormat(imageDetails.getDiskFormat().toString().toUpperCase());
      image.setContainerFormat(imageDetails.getContainerFormat().toString().toUpperCase());
      log.info(
          "Added Image with name: "
              + name
              + " to VimInstance with name: "
              + vimInstance.getName()
              + " -> Image: "
              + image);
      return image;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  @Override
  public boolean deleteImage(VimInstance vimInstance, NFVImage image) throws VimDriverException {
    log.debug(
        "Deleting Image with name: "
            + image.getName()
            + " (ExtId: "
            + image.getExtId()
            + ") from VimInstance with name: "
            + vimInstance.getName());
    try {
      GlanceApi glanceApi =
          ContextBuilder.newBuilder("openstack-glance")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(GlanceApi.class);
      ImageApi imageApi = glanceApi.getImageApi(getZone(vimInstance));
      boolean isDeleted = imageApi.delete(image.getExtId());
      log.info(
          "Deleted Image with name: "
              + image.getName()
              + " (ExtId: "
              + image.getExtId()
              + ") from VimInstance with name: "
              + vimInstance.getName());
      return isDeleted;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  @Override
  public NFVImage updateImage(VimInstance vimInstance, NFVImage image) throws VimDriverException {
    NFVImage updatedImage =
        updateImage(
            vimInstance,
            image.getExtId(),
            image.getName(),
            image.getDiskFormat(),
            image.getContainerFormat(),
            image.getMinDiskSpace(),
            image.getMinRam(),
            image.isPublic());
    image.setName(updatedImage.getName());
    image.setExtId(updatedImage.getExtId());
    image.setCreated(updatedImage.getCreated());
    image.setUpdated(updatedImage.getUpdated());
    image.setMinDiskSpace(updatedImage.getMinDiskSpace());
    image.setMinRam(updatedImage.getMinRam());
    image.setIsPublic(updatedImage.isPublic());
    image.setDiskFormat(updatedImage.getDiskFormat());
    image.setContainerFormat(updatedImage.getContainerFormat());
    return image;
  }

  private NFVImage updateImage(
      VimInstance vimInstance,
      String extId,
      String name,
      String diskFormat,
      String containerFormat,
      long minDisk,
      long minRam,
      boolean isPublic)
      throws VimDriverException {
    log.debug(
        "Updating Image with name: "
            + name
            + " (ExtId: "
            + extId
            + ") on VimInstance with name: "
            + vimInstance.getName());
    try {
      GlanceApi glanceApi =
          ContextBuilder.newBuilder("openstack-glance")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(GlanceApi.class);
      ImageApi imageApi = glanceApi.getImageApi(getZone(vimInstance));
      UpdateImageOptions updateImageOptions = new UpdateImageOptions();
      updateImageOptions.name(name);
      updateImageOptions.minRam(minRam);
      updateImageOptions.minDisk(minDisk);
      updateImageOptions.isPublic(isPublic);
      updateImageOptions.diskFormat(DiskFormat.valueOf(diskFormat));
      updateImageOptions.containerFormat(ContainerFormat.valueOf(containerFormat));
      ImageDetails imageDetails = imageApi.update(extId, updateImageOptions);
      NFVImage image = new NFVImage();
      image.setName(imageDetails.getName());
      image.setExtId(imageDetails.getId());
      image.setCreated(imageDetails.getCreatedAt());
      image.setUpdated(imageDetails.getUpdatedAt());
      image.setMinDiskSpace(imageDetails.getMinDisk());
      image.setMinRam(imageDetails.getMinRam());
      image.setIsPublic(imageDetails.isPublic());
      image.setDiskFormat(imageDetails.getDiskFormat().toString().toUpperCase());
      image.setContainerFormat(imageDetails.getContainerFormat().toString().toUpperCase());
      log.info(
          "Updated Image with name: "
              + image.getName()
              + " (ExtId: "
              + image.getExtId()
              + ") on VimInstance with name: "
              + vimInstance.getName()
              + " -> updated Image: "
              + image);
      return image;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  @Override
  public NFVImage copyImage(VimInstance vimInstance, NFVImage image, byte[] imageFile)
      throws VimDriverException {
    NFVImage copiedImage =
        copyImage(
            vimInstance,
            image.getName(),
            new ByteArrayInputStream(imageFile),
            image.getDiskFormat(),
            image.getContainerFormat(),
            image.getMinDiskSpace(),
            image.getMinRam(),
            image.isPublic());
    image.setName(copiedImage.getName());
    image.setExtId(copiedImage.getExtId());
    image.setCreated(copiedImage.getCreated());
    image.setUpdated(copiedImage.getUpdated());
    image.setMinDiskSpace(copiedImage.getMinDiskSpace());
    image.setMinRam(copiedImage.getMinRam());
    image.setIsPublic(copiedImage.isPublic());
    image.setDiskFormat(copiedImage.getDiskFormat());
    image.setContainerFormat(copiedImage.getContainerFormat());
    return image;
  }

  private NFVImage copyImage(
      VimInstance vimInstance,
      String name,
      InputStream inputStream,
      String diskFormat,
      String containerFormat,
      long minDisk,
      long minRam,
      boolean isPublic)
      throws VimDriverException {
    try {
      GlanceApi glanceApi =
          ContextBuilder.newBuilder("openstack-glance")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(GlanceApi.class);
      ImageApi imageApi = glanceApi.getImageApi(getZone(vimInstance));
      NFVImage image =
          addImage(
              vimInstance,
              name,
              inputStream,
              diskFormat,
              containerFormat,
              minDisk,
              minRam,
              isPublic);
      return image;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  private NFVImage getImageById(VimInstance vimInstance, String extId) throws VimDriverException {
    log.debug("Finding Image by ExtId: " + extId);
    try {
      NovaApi novaApi =
          ContextBuilder.newBuilder("openstack-nova")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NovaApi.class);
      org.jclouds.openstack.nova.v2_0.features.ImageApi imageApi =
          novaApi.getImageApi(getZone(vimInstance));
      org.jclouds.openstack.nova.v2_0.domain.Image jcloudsImage = imageApi.get(extId);
      NFVImage image = new NFVImage();
      image.setExtId(jcloudsImage.getId());
      image.setName(jcloudsImage.getName());
      image.setCreated(jcloudsImage.getCreated());
      image.setUpdated(jcloudsImage.getUpdated());
      image.setMinDiskSpace(jcloudsImage.getMinDisk());
      image.setMinRam(jcloudsImage.getMinRam());
      image.setIsPublic(false);
      image.setContainerFormat("not provided");
      image.setDiskFormat("not provided");
      log.info("Found Image by ExtId: " + extId + " -> Image: " + image);
      return image;
    } catch (NullPointerException e) {
      log.warn(
          e.getMessage(), new NullPointerException("Image with extId: " + extId + " not found."));
      NFVImage image = new NFVImage();
      image.setExtId(extId);
      return image;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  @Override
  public DeploymentFlavour addFlavor(VimInstance vimInstance, DeploymentFlavour flavor)
      throws VimDriverException {
    DeploymentFlavour addedFlavor =
        addFlavor(
            vimInstance,
            flavor.getFlavour_key(),
            flavor.getVcpus(),
            flavor.getRam(),
            flavor.getDisk());
    flavor.setExtId(addedFlavor.getExtId());
    flavor.setFlavour_key(addedFlavor.getFlavour_key());
    flavor.setVcpus(addedFlavor.getVcpus());
    flavor.setRam(addedFlavor.getRam());
    flavor.setDisk(addedFlavor.getVcpus());
    return flavor;
  }

  private DeploymentFlavour addFlavor(
      VimInstance vimInstance, String name, int vcpus, int ram, int disk)
      throws VimDriverException {
    log.debug(
        "Adding Flavor with name: " + name + " to VimInstance with name: " + vimInstance.getName());
    try {
      NovaApi novaApi =
          ContextBuilder.newBuilder("openstack-nova")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NovaApi.class);
      FlavorApi flavorApi = novaApi.getFlavorApi(getZone(vimInstance));
      UUID id = java.util.UUID.randomUUID();
      org.jclouds.openstack.nova.v2_0.domain.Flavor newFlavor =
          org.jclouds.openstack.nova.v2_0.domain.Flavor.builder()
              .id(id.toString())
              .name(name)
              .disk(disk)
              .ram(ram)
              .vcpus(vcpus)
              .build();
      org.jclouds.openstack.nova.v2_0.domain.Flavor jcloudsFlavor = flavorApi.create(newFlavor);
      DeploymentFlavour flavor = new DeploymentFlavour();
      flavor.setExtId(jcloudsFlavor.getId());
      flavor.setFlavour_key(jcloudsFlavor.getName());
      flavor.setVcpus(jcloudsFlavor.getVcpus());
      flavor.setRam(jcloudsFlavor.getRam());
      flavor.setDisk(jcloudsFlavor.getVcpus());
      log.info(
          "Added Flavor with name: "
              + name
              + " to VimInstance with name: "
              + vimInstance.getName()
              + " -> Flavor: "
              + flavor);
      return flavor;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  @Override
  public DeploymentFlavour updateFlavor(VimInstance vimInstance, DeploymentFlavour flavor)
      throws VimDriverException {
    DeploymentFlavour updatedFlavor =
        updateFlavor(
            vimInstance,
            flavor.getExtId(),
            flavor.getFlavour_key(),
            flavor.getVcpus(),
            flavor.getRam(),
            flavor.getDisk());
    flavor.setFlavour_key(updatedFlavor.getFlavour_key());
    flavor.setExtId(updatedFlavor.getExtId());
    flavor.setRam(updatedFlavor.getRam());
    flavor.setDisk(updatedFlavor.getDisk());
    flavor.setVcpus(updatedFlavor.getVcpus());
    return flavor;
  }

  private DeploymentFlavour updateFlavor(
      VimInstance vimInstance, String extId, String name, int vcpus, int ram, int disk)
      throws VimDriverException {
    log.debug(
        "Updating Flavor with name: "
            + name
            + " on VimInstance with name: "
            + vimInstance.getName());
    try {
      NovaApi novaApi =
          ContextBuilder.newBuilder("openstack-nova")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NovaApi.class);
      FlavorApi flavorApi = novaApi.getFlavorApi(getZone(vimInstance));
      boolean isDeleted = deleteFlavor(vimInstance, extId);
      if (isDeleted) {
        org.jclouds.openstack.nova.v2_0.domain.Flavor newFlavor =
            org.jclouds.openstack.nova.v2_0.domain.Flavor.builder()
                .id(extId)
                .name(name)
                .disk(disk)
                .ram(ram)
                .vcpus(vcpus)
                .build();
        org.jclouds.openstack.nova.v2_0.domain.Flavor jcloudsFlavor = flavorApi.create(newFlavor);
        DeploymentFlavour updatedFlavor = new DeploymentFlavour();
        updatedFlavor.setExtId(jcloudsFlavor.getId());
        updatedFlavor.setFlavour_key(jcloudsFlavor.getName());
        updatedFlavor.setVcpus(jcloudsFlavor.getVcpus());
        updatedFlavor.setRam(jcloudsFlavor.getRam());
        updatedFlavor.setDisk(jcloudsFlavor.getVcpus());
        log.info(
            "Updated Flavor with name: "
                + name
                + " on VimInstance with name: "
                + vimInstance.getName()
                + " -> Flavor: "
                + updatedFlavor);
        return updatedFlavor;
      } else {
        throw new VimDriverException(
            "Image with extId: "
                + extId
                + " not updated successfully. Not able to delete it and create a new one.");
      }
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  @Override
  public boolean deleteFlavor(VimInstance vimInstance, String extId) throws VimDriverException {
    log.debug(
        "Deleting Flavor with ExtId: "
            + extId
            + " from VimInstance with name: "
            + vimInstance.getName());
    try {
      NovaApi novaApi =
          ContextBuilder.newBuilder("openstack-nova")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NovaApi.class);
      FlavorApi flavorApi = novaApi.getFlavorApi(getZone(vimInstance));
      flavorApi.delete(extId);
      boolean isDeleted;
      try {
        DeploymentFlavour flavour = getFlavorById(vimInstance, extId);
        if (flavour.getFlavour_key() == null) {
          throw new NullPointerException();
        }
        isDeleted = false;
        log.warn(
            "Not deleted Flavor with ExtId: "
                + extId
                + " from VimInstance with name: "
                + vimInstance.getName());
      } catch (NullPointerException e) {
        isDeleted = true;
        log.info(
            "Deleted Flavor with ExtId: "
                + extId
                + " from VimInstance with name: "
                + vimInstance.getName());
      }
      return isDeleted;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  private DeploymentFlavour getFlavorById(VimInstance vimInstance, String extId)
      throws VimDriverException {
    log.debug(
        "Finding Flavor with ExtId: "
            + extId
            + " on VimInstance with name: "
            + vimInstance.getName());
    try {
      NovaApi novaApi =
          ContextBuilder.newBuilder("openstack-nova")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NovaApi.class);
      FlavorApi flavorApi = novaApi.getFlavorApi(getZone(vimInstance));
      org.jclouds.openstack.nova.v2_0.domain.Flavor jcloudsFlavor = flavorApi.get(extId);
      DeploymentFlavour flavor = new DeploymentFlavour();
      flavor.setFlavour_key(jcloudsFlavor.getName());
      flavor.setExtId(jcloudsFlavor.getId());
      flavor.setRam(jcloudsFlavor.getRam());
      flavor.setDisk(jcloudsFlavor.getDisk());
      flavor.setVcpus(jcloudsFlavor.getVcpus());
      log.info(
          "Found Flavor with ExtId: "
              + extId
              + " on VimInstance with name: "
              + vimInstance.getName());
      return flavor;
    } catch (NullPointerException e) {
      log.warn(
          e.getMessage(),
          new NullPointerException(
              "Flavor with extId: "
                  + extId
                  + " not found on VimInstance with name: "
                  + vimInstance.getName()));
      DeploymentFlavour flavor = new DeploymentFlavour();
      flavor.setExtId(extId);
      return flavor;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  @Override
  public List<DeploymentFlavour> listFlavors(VimInstance vimInstance) throws VimDriverException {
    log.debug("Listing Flavours on VimInstance with name: " + vimInstance.getName());
    try {
      NovaApi novaApi =
          ContextBuilder.newBuilder("openstack-nova")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NovaApi.class);
      FlavorApi flavorApi = novaApi.getFlavorApi(getZone(vimInstance));
      List<DeploymentFlavour> flavors = new ArrayList<DeploymentFlavour>();
      for (org.jclouds.openstack.nova.v2_0.domain.Flavor jcloudsFlavor :
          flavorApi.listInDetail().concat()) {
        DeploymentFlavour flavor = new DeploymentFlavour();
        log.debug("Found jclouds Flavour: " + jcloudsFlavor);
        flavor.setExtId(jcloudsFlavor.getId());
        flavor.setFlavour_key(jcloudsFlavor.getName());
        flavor.setRam(jcloudsFlavor.getRam());
        flavor.setDisk(jcloudsFlavor.getDisk());
        flavor.setVcpus(jcloudsFlavor.getVcpus());
        log.debug("Found Flavour: " + flavor);
        flavors.add(flavor);
      }
      log.info(
          "Listed Flavours on VimInstance with name: "
              + vimInstance.getName()
              + " -> Flavours: "
              + flavors);
      return flavors;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  @Override
  public Network createNetwork(VimInstance vimInstance, Network network) throws VimDriverException {
    Network createdNetwork =
        createNetwork(vimInstance, network.getName(), network.getExternal(), network.getShared());
    network.setName(createdNetwork.getName());
    network.setExtId(createdNetwork.getExtId());
    network.setExternal(createdNetwork.getExternal());
    network.setShared(createdNetwork.getShared());
    return network;
  }

  private Network createNetwork(
      VimInstance vimInstance, String name, boolean external, boolean shared)
      throws VimDriverException {
    log.debug(
        "Creating Network with name: "
            + name
            + " on VimInstance with name: "
            + vimInstance.getName());
    org.jclouds.openstack.neutron.v2.domain.Network jcloudsNetwork;
    try {
      NeutronApi neutronApi =
          ContextBuilder.newBuilder("openstack-neutron")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NeutronApi.class);
      NetworkApi networkApi = neutronApi.getNetworkApi(getZone(vimInstance));
      //CreateNetwork createNetwork = CreateNetwork.createBuilder(name).networkType(NetworkType.fromValue
      // (networkType)).external(external).shared(shared).segmentationId(segmentationId).physicalNetworkName
      // (physicalNetworkName).build();
      CreateNetwork createNetwork =
          CreateNetwork.createBuilder(name).external(external).shared(shared).build();
      log.debug("Initialized jclouds Network: " + createNetwork);

      jcloudsNetwork = networkApi.create(createNetwork);
    } catch (Exception e) {
      log.warn("Could not create a network");
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
    log.debug("Created jclouds Network: " + jcloudsNetwork);
    Network network = new Network();
    network.setName(jcloudsNetwork.getName());
    network.setExtId(jcloudsNetwork.getId());
    network.setExternal(jcloudsNetwork.getExternal());
    network.setShared(jcloudsNetwork.getShared());
    network.setSubnets(new HashSet<Subnet>());
    for (String subnetId : jcloudsNetwork.getSubnets()) {
      try {
        network.getSubnets().add(getSubnetById(vimInstance, subnetId));
      } catch (Exception e) {
        log.warn("Not able to find subnets. Not able to find subnet with id" + subnetId);
      }
    }
    log.info(
        "Created Network with name: "
            + name
            + " on VimInstance with name: "
            + vimInstance.getName()
            + " -> Network: "
            + network);
    return network;

    //return null;
  }

  @Override
  public Network updateNetwork(VimInstance vimInstance, Network network) throws VimDriverException {
    Network updatedNetwork =
        updateNetwork(
            vimInstance,
            network.getExtId(),
            network.getName(),
            network.getExternal(),
            network.getShared());
    network.setName(updatedNetwork.getName());
    network.setExtId(updatedNetwork.getExtId());
    network.setExternal(updatedNetwork.getExternal());
    network.setShared(updatedNetwork.getShared());
    return network;
  }

  private Network updateNetwork(
      VimInstance vimInstance, String extId, String name, boolean external, boolean shared)
      throws VimDriverException {
    log.debug(
        "Updating Network with name: "
            + name
            + " on VimInstance with name: "
            + vimInstance.getName());
    try {
      NeutronApi neutronApi =
          ContextBuilder.newBuilder("openstack-neutron")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NeutronApi.class);
      NetworkApi networkApi = neutronApi.getNetworkApi(getZone(vimInstance));
      //Plugin does not support updating provider attributes. -> NetworkType, SegmentationId, physicalNetworkName
      UpdateNetwork updateNetwork = UpdateNetwork.updateBuilder().name(name).build();
      org.jclouds.openstack.neutron.v2.domain.Network jcloudsNetwork =
          networkApi.update(extId, updateNetwork);
      Network network = new Network();
      network.setName(jcloudsNetwork.getName());
      network.setExtId(jcloudsNetwork.getId());
      network.setExternal(jcloudsNetwork.getExternal());
      network.setShared(jcloudsNetwork.getShared());
      network.setSubnets(new HashSet<Subnet>());
      for (String subnetId : jcloudsNetwork.getSubnets()) {
        network.getSubnets().add(getSubnetById(vimInstance, subnetId));
      }
      log.debug(
          "Updated Network with name: "
              + name
              + " on VimInstance with name: "
              + vimInstance.getName()
              + " -> Network: "
              + network);
      return network;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  @Override
  public boolean deleteNetwork(VimInstance vimInstance, String extId) throws VimDriverException {
    log.debug(
        "Deleting Network with ExtId: "
            + extId
            + " from VimInstance with name: "
            + vimInstance.getName());
    try {
      NeutronApi neutronApi =
          ContextBuilder.newBuilder("openstack-neutron")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NeutronApi.class);
      NetworkApi networkApi = neutronApi.getNetworkApi(getZone(vimInstance));
      boolean isDeleted = networkApi.delete(extId);
      if (isDeleted == true) {
        log.debug(
            "Deleted Network with ExtId: "
                + extId
                + " from VimInstance with name: "
                + vimInstance.getName());
      } else {
        log.debug(
            "Not deleted Network with ExtId: "
                + extId
                + " from VimInstance with name: "
                + vimInstance.getName());
      }
      return isDeleted;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  @Override
  public Network getNetworkById(VimInstance vimInstance, String extId) throws VimDriverException {
    log.debug(
        "Finding Network with ExtId: "
            + extId
            + " on VimInstance with name: "
            + vimInstance.getName());
    try {
      NeutronApi neutronApi =
          ContextBuilder.newBuilder("openstack-neutron")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NeutronApi.class);
      NetworkApi networkApi = neutronApi.getNetworkApi(getZone(vimInstance));
      org.jclouds.openstack.neutron.v2.domain.Network jcloudsNetwork = networkApi.get(extId);
      Network network = new Network();
      network.setName(jcloudsNetwork.getName());
      network.setExtId(jcloudsNetwork.getId());
      network.setExternal(jcloudsNetwork.getExternal());
      network.setShared(jcloudsNetwork.getShared());
      network.setSubnets(new HashSet<Subnet>());
      for (String subnetId : jcloudsNetwork.getSubnets()) {
        network.getSubnets().add(getSubnetById(vimInstance, subnetId));
      }
      log.info(
          "Found Network with ExtId: "
              + extId
              + " on VimInstance with name: "
              + vimInstance.getName()
              + " -> "
              + network);
      return network;
    } catch (NullPointerException e) {
      log.error(
          "Not found Network with ExtId: "
              + extId
              + " on VimInstance with name: "
              + vimInstance.getName(),
          e);
      throw new NullPointerException(
          "Not found Network with ExtId: "
              + extId
              + " on VimInstance with name: "
              + vimInstance.getName());
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  @Override
  public List<String> getSubnetsExtIds(VimInstance vimInstance, String extId)
      throws VimDriverException {
    log.debug(
        "Listing all external SubnetIDs for Network with ExtId: "
            + extId
            + " from VimInstance with name: "
            + vimInstance.getName());
    try {
      NeutronApi neutronApi =
          ContextBuilder.newBuilder("openstack-neutron")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NeutronApi.class);
      NetworkApi networkApi = neutronApi.getNetworkApi(getZone(vimInstance));
      List<String> subnets = new ArrayList<String>();
      org.jclouds.openstack.neutron.v2.domain.Network jcloudsNetwork = networkApi.get(extId);
      subnets = jcloudsNetwork.getSubnets().asList();
      log.info(
          "Listed all external SubnetIDs for Network with ExtId: "
              + extId
              + " from VimInstance with name: "
              + vimInstance.getName()
              + " -> external Subnet IDs: "
              + subnets);
      return subnets;
    } catch (NullPointerException e) {
      log.error(
          "Not found Network with ExtId: "
              + extId
              + " on VimInstance with name: "
              + vimInstance.getName(),
          e);
      throw new NullPointerException(
          "Not found Network with ExtId: "
              + extId
              + " on VimInstance with name: "
              + vimInstance.getName());
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  @Override
  public List<Network> listNetworks(VimInstance vimInstance) throws VimDriverException {
    log.debug("Listing all Networks of VimInstance with name: " + vimInstance.getName());
    try {
      NeutronApi neutronApi =
          ContextBuilder.newBuilder("openstack-neutron")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NeutronApi.class);
      List<Network> networks = new ArrayList<Network>();
      String tenantId = getTenantId(vimInstance);
      for (org.jclouds.openstack.neutron.v2.domain.Network jcloudsNetwork :
          neutronApi.getNetworkApi(getZone(vimInstance)).list().concat()) {
        if (jcloudsNetwork.getTenantId().equals(tenantId) || jcloudsNetwork.getShared()) {
          log.debug("Found jclouds Network: " + jcloudsNetwork);
          Network network = new Network();
          network.setName(jcloudsNetwork.getName());
          network.setExtId(jcloudsNetwork.getId());
          network.setExternal(jcloudsNetwork.getExternal());
          network.setShared(jcloudsNetwork.getShared());
          network.setSubnets(new HashSet<Subnet>());
          for (String subnetId : jcloudsNetwork.getSubnets()) {
            network.getSubnets().add(getSubnetById(vimInstance, subnetId));
          }
          log.debug("Found Network: " + network);
          networks.add(network);
        }
      }
      log.info(
          "Listed all Networks of VimInstance with name: "
              + vimInstance.getName()
              + " -> Networks: "
              + networks);
      return networks;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  private Subnet getSubnetById(VimInstance vimInstance, String extId) throws VimDriverException {
    log.debug(
        "Getting Subnet with extId: "
            + extId
            + " from VimInstance with name: "
            + vimInstance.getName());
    try {
      NeutronApi neutronApi =
          ContextBuilder.newBuilder("openstack-neutron")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NeutronApi.class);
      SubnetApi subnetApi = neutronApi.getSubnetApi(getZone(vimInstance));
      org.jclouds.openstack.neutron.v2.domain.Subnet jcloudsSubnet = subnetApi.get(extId);
      log.debug("Got jclouds Subnet: " + jcloudsSubnet);
      Subnet subnet = new Subnet();
      subnet.setExtId(jcloudsSubnet.getId());
      subnet.setName(jcloudsSubnet.getName());
      subnet.setCidr(jcloudsSubnet.getCidr());
      subnet.setGatewayIp(jcloudsSubnet.getGatewayIp());
      subnet.setNetworkId(jcloudsSubnet.getNetworkId());
      log.info(
          "Found Subnet with extId: "
              + extId
              + " on VimInstance with name: "
              + vimInstance.getName()
              + " -> Subnet: "
              + subnet);
      return subnet;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  @Override
  public Subnet createSubnet(VimInstance vimInstance, Network network, Subnet subnet)
      throws VimDriverException {
    Subnet createdSubnet = createSubnet(vimInstance, network, subnet.getName(), subnet.getCidr());
    subnet.setExtId(createdSubnet.getExtId());
    subnet.setName(createdSubnet.getName());
    subnet.setCidr(createdSubnet.getCidr());
    subnet.setGatewayIp(createdSubnet.getGatewayIp());
    return subnet;
  }

  private Subnet createSubnet(VimInstance vimInstance, Network network, String name, String cidr)
      throws VimDriverException {
    log.debug(
        "Creating Subnet with name: "
            + name
            + " on Network with name: + "
            + network.getName()
            + " on VimInstance with name: "
            + vimInstance.getName());
    try {
      NeutronApi neutronApi =
          ContextBuilder.newBuilder("openstack-neutron")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NeutronApi.class);
      SubnetApi subnetApi = neutronApi.getSubnetApi(getZone(vimInstance));
      CreateSubnet createSubnet =
          CreateSubnet.createBuilder(network.getExtId(), cidr)
              .name(name)
              .dnsNameServers(ImmutableSet.<String>of(properties.getProperty("dns-nameserver")))
              .ipVersion(4)
              .build();
      log.debug("Initialized jclouds Network: " + createSubnet);
      org.jclouds.openstack.neutron.v2.domain.Subnet jcloudsSubnet = subnetApi.create(createSubnet);
      log.debug("Created jclouds Network: " + jcloudsSubnet);
      Subnet subnet = new Subnet();
      subnet.setExtId(jcloudsSubnet.getId());
      subnet.setName(jcloudsSubnet.getName());
      subnet.setCidr(jcloudsSubnet.getCidr());
      subnet.setGatewayIp(jcloudsSubnet.getGatewayIp());
      String routerId = getRouter(vimInstance);
      if (routerId == null) {
        log.debug("Not found Router");
        routerId = createRouter(vimInstance);
      }
      if (routerId != null) {
        String portId = createPort(vimInstance, network, subnet);
        attachPort(vimInstance, routerId, portId);
      }
      log.info(
          "Created Subnet with name: "
              + name
              + " on Network with name: + "
              + network.getName()
              + " on VimInstance with name: "
              + vimInstance.getName()
              + " -> Subnet: "
              + subnet);
      return subnet;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  @Override
  public Subnet updateSubnet(VimInstance vimInstance, Network network, Subnet subnet)
      throws VimDriverException {
    Subnet updatedSubnet = updateSubnet(vimInstance, network, subnet.getExtId(), subnet.getName());
    subnet.setExtId(updatedSubnet.getExtId());
    subnet.setName(updatedSubnet.getName());
    subnet.setCidr(updatedSubnet.getCidr());
    subnet.setGatewayIp(updatedSubnet.getGatewayIp());
    return subnet;
  }

  private Subnet updateSubnet(
      VimInstance vimInstance, Network network, String subnetExtId, String name)
      throws VimDriverException {
    log.debug(
        "Updating Subnet with ExtId: "
            + subnetExtId
            + " on Network with name: + "
            + network.getName()
            + " on VimInstance with name: "
            + vimInstance.getName());
    try {
      NeutronApi neutronApi =
          ContextBuilder.newBuilder("openstack-neutron")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NeutronApi.class);
      SubnetApi subnetApi = neutronApi.getSubnetApi(getZone(vimInstance));
      UpdateSubnet updateSubnet = UpdateSubnet.updateBuilder().name(name).build();
      org.jclouds.openstack.neutron.v2.domain.Subnet jcloudsSubnet =
          subnetApi.update(subnetExtId, updateSubnet);
      Subnet subnet = new Subnet();
      subnet.setExtId(jcloudsSubnet.getId());
      subnet.setName(jcloudsSubnet.getName());
      subnet.setCidr(jcloudsSubnet.getCidr());
      subnet.setGatewayIp(jcloudsSubnet.getGatewayIp());
      log.debug(
          "Updated Subnet with ExtId: "
              + subnetExtId
              + " on Network with name: + "
              + network.getName()
              + " on VimInstance with name: "
              + vimInstance.getName()
              + " -> Subnet: "
              + subnet);
      return subnet;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  private String getRouter(VimInstance vimInstance) throws VimDriverException {
    log.debug(
        "Finding a Router that is connected with external Network on VimInstance with name: "
            + vimInstance.getName());
    try {
      NeutronApi neutronApi =
          ContextBuilder.newBuilder("openstack-neutron")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NeutronApi.class);

      RouterApi routerApi = neutronApi.getRouterApi(getZone(vimInstance)).get();
      PagedIterable routerList = routerApi.list();
      String tenantId = getTenantId(vimInstance);
      if (routerList.iterator().hasNext()) {
        for (Router router : (FluentIterable<Router>) routerList.concat()) {
          if (router.getTenantId().equals(tenantId)) {
            ExternalGatewayInfo externalGatewayInfo = router.getExternalGatewayInfo();
            if (externalGatewayInfo != null) {
              String networkId = externalGatewayInfo.getNetworkId();
              if (getNetworkById(vimInstance, networkId).getExternal()) {
                log.info(
                    "Found a Router that is connected with external Network on VimInstance with name: "
                        + vimInstance.getName());
                return router.getId();
              }
            }
          }
        }
      }
      log.warn(
          "Not found any Router that is connected with external Network on VimInstance with name: "
              + vimInstance.getName());
      return null;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      //      throw new VimDriverException(e.getMessage());
      return null;
    }
  }

  private String createRouter(VimInstance vimInstance) throws VimDriverException {
    log.debug(
        "Creating a Router that is connected with external Network on VimInstance with name: "
            + vimInstance.getName());
    try {
      NeutronApi neutronApi =
          ContextBuilder.newBuilder("openstack-neutron")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NeutronApi.class);
      RouterApi routerApi = neutronApi.getRouterApi(getZone(vimInstance)).get();
      //Find external network
      String externalNetId = null;
      log.debug(
          "Finding an external Network where we can connect a new Router to on VimInstance with name: "
              + vimInstance.getName());
      for (Network network : listNetworks(vimInstance)) {
        if (network.getExternal()) {
          log.debug(
              "Found an external Network where we can connect a new Router to on VimInstance with name: "
                  + vimInstance.getName()
                  + " -> Network: "
                  + network);
          externalNetId = network.getExtId();
        }
      }
      if (externalNetId == null) {
        log.warn(
            "Not found any external Network where we can connect a new Router to on VimInstance with name: "
                + vimInstance.getName());
        return null;
      }
      ExternalGatewayInfo externalGatewayInfo =
          ExternalGatewayInfo.builder().networkId(externalNetId).build();
      Router.CreateRouter options =
          Router.CreateRouter.createBuilder()
              .name(vimInstance.getTenant() + "_" + (int) (Math.random() * 1000) + "_router")
              .adminStateUp(true)
              .externalGatewayInfo(externalGatewayInfo)
              .build();
      Router router = routerApi.create(options);
      log.info(
          "Created a Router that is connected with external Network on VimInstance with name: "
              + vimInstance.getName());
      return router.getId();
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      //      throw new VimDriverException(e.getMessage());
      return null;
    }
  }

  private String attachPort(VimInstance vimInstance, String routerId, String portId)
      throws VimDriverException {
    log.debug(
        "Attaching Port with ExtId: "
            + portId
            + " to Router with ExtId: "
            + routerId
            + " on VimInstnace with name: "
            + vimInstance);
    try {
      NeutronApi neutronApi =
          ContextBuilder.newBuilder("openstack-neutron")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NeutronApi.class);
      RouterApi routerApi = neutronApi.getRouterApi(getZone(vimInstance)).get();
      RouterInterface routerInterface = routerApi.addInterfaceForPort(routerId, portId);
      log.info(
          "Attached Port with ExtId: "
              + portId
              + " to Router with ExtId: "
              + routerId
              + " on VimInstnace with name: "
              + vimInstance);
      return routerInterface.getSubnetId();
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  private String createPort(VimInstance vimInstance, Network network, Subnet subnet)
      throws VimDriverException {
    log.debug(
        "Creating a Port for network with name: "
            + network.getName()
            + " and Subnet with name: "
            + subnet.getName()
            + " on VimInstance with name: "
            + vimInstance.getName());
    try {
      NeutronApi neutronApi =
          ContextBuilder.newBuilder("openstack-neutron")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NeutronApi.class);
      PortApi portApi = neutronApi.getPortApi(getZone(vimInstance));
      Port.CreatePort createPort =
          Port.createBuilder(network.getExtId())
              .name("Port_" + network.getName() + "_" + (int) (Math.random() * 1000))
              .fixedIps(ImmutableSet.of(IP.builder().ipAddress(subnet.getGatewayIp()).build()))
              .build();
      Port port = portApi.create(createPort);
      log.info(
          "Created a Port for network with name: "
              + network.getName()
              + " and Subnet with name: "
              + subnet.getName()
              + " on VimInstance with name: "
              + vimInstance.getName()
              + " -> Port: "
              + port);
      return port.getId();
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  @Override
  public boolean deleteSubnet(VimInstance vimInstance, String extId) throws VimDriverException {
    log.debug(
        "Deleting Subnet with ExtId: "
            + extId
            + " from VimInstance with name: "
            + vimInstance.getName());
    try {
      NeutronApi neutronApi =
          ContextBuilder.newBuilder("openstack-neutron")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NeutronApi.class);
      SubnetApi subnetApi = neutronApi.getSubnetApi(getZone(vimInstance));
      boolean isDeleted = subnetApi.delete(extId);
      if (isDeleted == true) {
        log.info(
            "Deleted Subnet with ExtId: "
                + extId
                + " from VimInstance with name: "
                + vimInstance.getName());
      } else {
        log.warn(
            "Not deleted Subnet with ExtId: "
                + extId
                + " from VimInstance with name: "
                + vimInstance.getName());
      }
      return isDeleted;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  private List<String> listFreeFloatingIps(VimInstance vimInstance) throws VimDriverException {
    log.debug("Listing all free FloatingIPs of VimInstance with name: " + vimInstance.getName());
    String tenantId = getTenantId(vimInstance);
    try {
      NovaApi novaApi =
          ContextBuilder.newBuilder("openstack-nova")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NovaApi.class);
      NeutronApi neutronApi =
          ContextBuilder.newBuilder("openstack-neutron")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NeutronApi.class);

      //      if (novaApi.getFloatingIPApi(getZone(vimInstance)).isPresent()){
      boolean floatingIpApiNotPresent = false;
      Optional<FloatingIPApi> neutronApiFloatingIPApi = null;
      try {
        neutronApiFloatingIPApi = neutronApi.getFloatingIPApi(getZone(vimInstance));
      } catch (Exception e) {
        floatingIpApiNotPresent = true;
      }

      if (!floatingIpApiNotPresent && neutronApiFloatingIPApi.isPresent()) {
        FloatingIPApi floatingIPApi = neutronApiFloatingIPApi.get();

        //        org.jclouds.openstack.nova.v2_0.extensions.FloatingIPApi floatingIPApi =
        //            novaApi.getFloatingIPApi(getZone(vimInstance)).get();
        List<String> floatingIPs = new LinkedList<String>();

        for (FloatingIP floatingIP : floatingIPApi.list(new PaginationOptions())) {
          if (floatingIP.getTenantId().equals(tenantId) && floatingIP.getFixedIpAddress() == null) {
            floatingIPs.add(floatingIP.getFloatingIpAddress() /*getFloatingIpAddress()*/);
          }
        }
        log.info(
            "Listed all free FloatingIPs of VimInstance with name: "
                + vimInstance.getName()
                + " -> free FloatingIPs: "
                + floatingIPs);
        return floatingIPs;
      } else {
        /*
        REQ: curl -i http://192.168.45.101:9696/v2.0/floatingips.json -X GET -H "X-Auth-Token: ..." -H
        "Content-Type: application/json" -H "Accept: application/json" -H "User-Agent: python-neutronclient"
         */

        URI endpoint = null;
        ContextBuilder contextBuilder =
            ContextBuilder.newBuilder("openstack-nova")
                .credentials(vimInstance.getUsername(), vimInstance.getPassword())
                .endpoint(vimInstance.getAuthUrl());
        ComputeServiceContext context = contextBuilder.buildView(ComputeServiceContext.class);
        Function<Credentials, Access> auth =
            context
                .utils()
                .injector()
                .getInstance(Key.get(new TypeLiteral<Function<Credentials, Access>>() {}));

        Access access =
            auth.apply(
                new Credentials.Builder<>()
                    .identity(vimInstance.getTenant() + ":" + vimInstance.getUsername())
                    .credential(vimInstance.getPassword())
                    .build());

        log.debug("listing FloatingIPs: finding endpoint");
        for (org.jclouds.openstack.keystone.v2_0.domain.Service service : access) {
          if (service.getName().equals("neutron")) {
            for (Endpoint end : service) {
              endpoint = end.getPublicURL();
              break;
            }
            break;
          }
        }

        HttpURLConnection connection = null;
        URL url = new URL(endpoint + "/v2.0/floatingips.json");
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("User-Agent", "python-neutronclient");
        connection.setRequestProperty("X-Auth-Token", access.getToken().getId());

        InputStream is = connection.getInputStream();
        BufferedReader rd = new BufferedReader(new InputStreamReader(is));
        StringBuilder response = new StringBuilder(); // or StringBuffer if not Java 5+
        String line;
        while ((line = rd.readLine()) != null) {
          response.append(line);
          response.append('\r');
        }
        rd.close();
        //Parse json to object
        log.debug("List of FloatingIPs: Response of final request is: " + response.toString());

        JsonObject res =
            new GsonBuilder()
                .setPrettyPrinting()
                .create()
                .fromJson(response.toString(), JsonObject.class);

        log.debug("JsonObject is: " + res);

        List<String> result = new ArrayList<>();
        if (res.has("floatingips")) {
          for (JsonElement element : res.get("floatingips").getAsJsonArray()) {
            log.debug("FloatingIp is: " + element.getAsJsonObject());
            JsonElement fixed_ip_address = element.getAsJsonObject().get("fixed_ip_address");
            JsonElement tenant_id = element.getAsJsonObject().get("tenant_id");
            if (!fixed_ip_address.isJsonNull() || !tenant_id.getAsString().equals(tenantId)) {
              //              log.debug("FixedIpAddress is: " + fixed_ip_address);
              continue;
            }

            String floating_ip_address =
                element.getAsJsonObject().get("floating_ip_address").getAsString();
            log.debug("found ip: " + floating_ip_address);
            result.add(floating_ip_address);
          }
        } else {
          log.warn("Was not possible through Openstack ReST api to retrieve all the FloatingIP");
        }
        log.info("Free Floating ips are: " + result);
        return result;
      }
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  private void associateFloatingIp(VimInstance vimInstance, Server server, String floatingIp)
      throws VimDriverException {
    log.debug(
        "Associating FloatingIP: "
            + floatingIp
            + " to VM with hostname: "
            + server.getName()
            + " on VimInstance with name: "
            + vimInstance.getName());
    try {
      NovaApi novaApi =
          ContextBuilder.newBuilder("openstack-nova")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NovaApi.class);
      org.jclouds.openstack.nova.v2_0.extensions.FloatingIPApi floatingIPApi =
          novaApi.getFloatingIPApi(getZone(vimInstance)).get();
      floatingIPApi.addToServer(floatingIp, server.getExtId());
      server.setFloatingIps(new HashMap<String, String>());
      server.getFloatingIps().put("netname", floatingIp);
      log.info(
          "Associated FloatingIP: "
              + floatingIp
              + " to VM with hostname: "
              + server.getName()
              + " on VimInstance with name: "
              + vimInstance.getName());
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  public String getTenantId(VimInstance vimInstance) throws VimDriverException {
    log.debug(
        "Finding TenantID for Tenant with name: "
            + vimInstance.getTenant()
            + " on VimInstance with name: "
            + vimInstance.getName());
    try {
      ContextBuilder contextBuilder =
          ContextBuilder.newBuilder("openstack-nova")
              .credentials(vimInstance.getUsername(), vimInstance.getPassword())
              .endpoint(vimInstance.getAuthUrl());
      ComputeServiceContext context = contextBuilder.buildView(ComputeServiceContext.class);
      Function<Credentials, Access> auth =
          context
              .utils()
              .injector()
              .getInstance(Key.get(new TypeLiteral<Function<Credentials, Access>>() {}));
      //Get Access and all information
      Access access =
          auth.apply(
              new Credentials.Builder<Credentials>()
                  .identity(vimInstance.getTenant() + ":" + vimInstance.getUsername())
                  .credential(vimInstance.getPassword())
                  .build());
      //Get Tenant ID of user
      String tenant_id = access.getToken().getTenant().get().getId();
      log.info(
          "Found TenantID for Tenant with name: "
              + vimInstance.getTenant()
              + " on VimInstance with name: "
              + vimInstance.getName()
              + " -> TenantID: "
              + tenant_id);
      return tenant_id;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    }
  }

  @Override
  public Quota getQuota(VimInstance vimInstance) throws VimDriverException {
    log.debug(
        "Finding Quota for Tenant with name: "
            + vimInstance.getTenant()
            + " on VimInstance with name: "
            + vimInstance.getName());
    HttpURLConnection connection = null;
    try {
      Quota quota = new Quota();
      ContextBuilder contextBuilder =
          ContextBuilder.newBuilder("openstack-nova")
              .credentials(vimInstance.getUsername(), vimInstance.getPassword())
              .endpoint(vimInstance.getAuthUrl());
      ComputeServiceContext context = contextBuilder.buildView(ComputeServiceContext.class);
      Function<Credentials, Access> auth =
          context
              .utils()
              .injector()
              .getInstance(Key.get(new TypeLiteral<Function<Credentials, Access>>() {}));
      //Get Access and all information
      Access access =
          auth.apply(
              new Credentials.Builder<Credentials>()
                  .identity(vimInstance.getTenant() + ":" + vimInstance.getUsername())
                  .credential(vimInstance.getPassword())
                  .build());
      //Get Tenant ID of user
      String tenant_id = access.getToken().getTenant().get().getId();
      //Get nova endpoint
      URI endpoint = null;
      for (org.jclouds.openstack.keystone.v2_0.domain.Service service : access) {
        if (service.getName().equals("nova")) {
          for (Endpoint end : service) {
            endpoint = end.getPublicURL();
            break;
          }
          break;
        }
      }

      //Prepare quota request
      URL url = new URL(endpoint + "/os-quota-sets/" + tenant_id);
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("X-Auth-Token", access.getToken().getId());
      //Get Response
      InputStream is = connection.getInputStream();
      BufferedReader rd = new BufferedReader(new InputStreamReader(is));
      StringBuilder response = new StringBuilder(); // or StringBuffer if not Java 5+
      String line;
      while ((line = rd.readLine()) != null) {
        response.append(line);
        response.append('\r');
      }
      rd.close();
      //Parse json to object
      JsonParser parser = new JsonParser();
      JsonObject json = (JsonObject) parser.parse(response.toString());
      JsonObject quota_set = json.getAsJsonObject("quota_set");
      //Fill out quota
      quota.setTenant(vimInstance.getTenant());
      quota.setCores(Integer.parseInt(quota_set.get("cores").toString()));
      quota.setRam(Integer.parseInt(quota_set.get("ram").toString()));
      quota.setInstances(Integer.parseInt(quota_set.get("instances").toString()));
      quota.setFloatingIps(Integer.parseInt(quota_set.get("floating_ips").toString()));
      quota.setKeyPairs(Integer.parseInt(quota_set.get("key_pairs").toString()));
      log.info(
          "Found Quota for tenant with name: "
              + vimInstance.getTenant()
              + " on VimInstance with name: "
              + vimInstance.getName()
              + " -> Quota: "
              + quota);
      return quota;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new VimDriverException(e.getMessage());
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  /**
   * @param vimInstance
   * @param server
   * @param fip
   * @return
   */
  public synchronized void associateFloatingIpToNetwork(
      VimInstance vimInstance, Server server, Map.Entry<String, String> fip) {
    log.debug(
        "Associating FloatingIP to VM with hostname: "
            + server.getName()
            + " on VimInstance with name: "
            + vimInstance.getName());
    HttpURLConnection connection = null;
    try {
      String floatingIp = null;
      String privateIp = null;
      if (fip.getValue() != null) {
        if (fip.getValue().equals("random")) {
          log.debug("Associating FloatingIP: defined random IP -> try to find one");
          privateIp = server.getIps().get(fip.getKey()).get(0);
          if (privateIp == null) {
            log.error(
                "Associating FloatingIP: Cannot assign FloatingIPs to server "
                    + server.getId()
                    + " . wrong network"
                    + fip.getKey());
          } else {
            floatingIp = listFreeFloatingIps(vimInstance).get(0);
            log.debug("Got Floating ip" + floatingIp.toString());
          }
        } else if (validate(fip.getValue())) {
          log.debug("Associating FloatingIP: " + fip.getValue());
          privateIp = server.getIps().get(fip.getKey()).get(0);
          if (privateIp == null) {
            log.error(
                "Associating FloatingIP: Cannot assign FloatingIPs to server "
                    + server.getId()
                    + " . wrong network"
                    + fip.getKey());
          } else {
            floatingIp = fip.getValue();
          }
        }
      } else {
        log.error(
            "Associating FloatingIP: Cannot assign FloatingIPs to server "
                + server.getId()
                + " . wrong floatingip: "
                + fip.getValue());
      }

      log.debug("Associating " + floatingIp + " to server: " + server.getName());
      ContextBuilder contextBuilder =
          ContextBuilder.newBuilder("openstack-nova")
              .credentials(vimInstance.getUsername(), vimInstance.getPassword())
              .endpoint(vimInstance.getAuthUrl());
      ComputeServiceContext context = contextBuilder.buildView(ComputeServiceContext.class);
      Function<Credentials, Access> auth =
          context
              .utils()
              .injector()
              .getInstance(Key.get(new TypeLiteral<Function<Credentials, Access>>() {}));

      //Get Access and all information
      Access access =
          auth.apply(
              new Credentials.Builder<Credentials>()
                  .identity(vimInstance.getTenant() + ":" + vimInstance.getUsername())
                  .credential(vimInstance.getPassword())
                  .build());
      //Get Tenant ID of user

      String tenant_id = access.getToken().getTenant().get().getId();
      //Get nova endpoint
      URI endpoint = null;

      log.debug("Associating FloatingIP: finding endpoint");
      for (org.jclouds.openstack.keystone.v2_0.domain.Service service : access) {
        if (service.getName().equals("neutron")) {
          for (Endpoint end : service) {
            endpoint = end.getPublicURL();
            break;
          }
          break;
        }
      }

      log.debug("Associating FloatingIP: Endpoint is: " + endpoint);

      // Get floating Ip
      String floatingIpId = null;
      String port_id = null;
      NovaApi novaApi =
          ContextBuilder.newBuilder("openstack-nova")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NovaApi.class);
      NeutronApi neutronApi =
          ContextBuilder.newBuilder("openstack-neutron")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NeutronApi.class);
      if (novaApi.getFloatingIPApi(getZone(vimInstance)).isPresent()) {
        org.jclouds.openstack.nova.v2_0.extensions.FloatingIPApi novaFloatingIPApi =
            novaApi.getFloatingIPApi(getZone(vimInstance)).get();

        novaFloatingIPApi.addToServer(floatingIp, server.getExtId());

        server.getFloatingIps().put(fip.getKey(), floatingIp);
        log.info(
            "Associated FloatingIP to VM with hostname: "
                + server.getName()
                + " on VimInstance with name: "
                + vimInstance.getName()
                + " -> FloatingIP: "
                + floatingIp);
      } else {
        log.warn("Could not access floating ip using the jclouds APIs, trying with restAPI");

        floatingIpId = findFloatingIpId(floatingIp, vimInstance);

        Map<String, String> ports = listPorts(access, endpoint, vimInstance);

        port_id = ports.get(privateIp);

        URL url = new URL(endpoint + "/v2.0/floatingips/" + floatingIpId + ".json");
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "python-neutronclient");
        connection.setRequestProperty("X-Auth-Token", access.getToken().getId());
        OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream());
        String body = "{\"floatingip\": {\"port_id\": \"" + port_id + "\"}}";
        log.debug("Body is: " + body);
        out.write(body);
        out.close();
        //Get Response
        InputStream is = connection.getInputStream();
        BufferedReader rd = new BufferedReader(new InputStreamReader(is));
        StringBuilder response = new StringBuilder(); // or StringBuffer if not Java 5+
        String line;
        while ((line = rd.readLine()) != null) {
          response.append(line);
          response.append('\r');
        }
        rd.close();
        //Parse json to object
        log.debug("Associating FloatingIP: Response of final request is: " + response.toString());

        log.debug("Translating ip...");
        floatingIp = translateToNAT(floatingIp);

        server.getFloatingIps().put(fip.getKey(), floatingIp);
        log.info(
            "Associated FloatingIP to VM with hostname: "
                + server.getName()
                + " on VimInstance with name: "
                + vimInstance.getName()
                + " -> FloatingIP: "
                + floatingIp);
      }
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      //throw new VimDriverException(e.getMessage());
      log.warn(
          "It seems that floatingApi is not present or there are not enough available floating ips, this means that "
              + "we will not be able to assign them");
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  private static String translateToNAT(String floatingIp) throws UnknownHostException {

    Properties natRules = new Properties();
    try {
      File file = new File("/etc/openbaton/plugin/openstack/nat-translation-rules.properties");
      if (file.exists()) {
        natRules.load(new FileInputStream(file));
      } else {
        natRules.load(
            OpenstackClient.class.getResourceAsStream("/nat-translation-rules.properties"));
      }
    } catch (IOException e) {
      log.warn("no translation rules!");
      return floatingIp;
    }

    for (Map.Entry<Object, Object> entry : natRules.entrySet()) {
      String fromCidr = (String) entry.getKey();
      String toCidr = (String) entry.getValue();
      log.debug("cidr is: " + fromCidr);
      SubnetUtils utilsFrom = new SubnetUtils(fromCidr);
      SubnetUtils utilsTo = new SubnetUtils(toCidr);

      SubnetUtils.SubnetInfo subnetInfoFrom = utilsFrom.getInfo();
      SubnetUtils.SubnetInfo subnetInfoTo = utilsTo.getInfo();
      InetAddress floatingIpNetAddr = InetAddress.getByName(floatingIp);
      if (subnetInfoFrom.isInRange(floatingIp)) { //translation!

        log.debug("From networkMask " + subnetInfoFrom.getNetmask());
        log.debug("To networkMask " + subnetInfoTo.getNetmask());
        if (!subnetInfoFrom.getNetmask().equals(subnetInfoTo.getNetmask())) {
          log.error("Not translation possible, netmasks are different");
          return floatingIp;
        }
        byte[] host = new byte[4];
        for (int i = 0; i < floatingIpNetAddr.getAddress().length; i++) {
          byte value =
              (byte)
                  (floatingIpNetAddr.getAddress()[i]
                      | InetAddress.getByName(subnetInfoFrom.getNetmask()).getAddress()[i]);
          if (value == -1) {
            host[i] = 0;
          } else {
            host[i] = value;
          }
        }

        byte[] netaddress = InetAddress.getByName(subnetInfoTo.getNetworkAddress()).getAddress();
        String[] result = new String[4];
        for (int i = 0; i < netaddress.length; i++) {
          int intValue = new Byte((byte) (netaddress[i] | Byte.valueOf(host[i]))).intValue();
          if (intValue < 0) {
            intValue = intValue & 0xFF;
          }
          result[i] = String.valueOf(intValue);
        }

        return StringUtils.join(result, ".");
      }
    }
    return floatingIp;
  }

  //retrieves the ip pool name from openstack via http request
  public String getIpPoolName(VimInstance vimInstance) {
    HttpURLConnection connection = null;
    log.info("Began retrieving the name of the ip pool");
    try {
      ContextBuilder contextBuilder =
          ContextBuilder.newBuilder("openstack-nova")
              .credentials(vimInstance.getUsername(), vimInstance.getPassword())
              .endpoint(vimInstance.getAuthUrl());
      ComputeServiceContext context = contextBuilder.buildView(ComputeServiceContext.class);
      Function<Credentials, Access> auth =
          context
              .utils()
              .injector()
              .getInstance(Key.get(new TypeLiteral<Function<Credentials, Access>>() {}));
      //Get Access and all information
      Access access =
          auth.apply(
              new Credentials.Builder<Credentials>()
                  .identity(vimInstance.getTenant() + ":" + vimInstance.getUsername())
                  .credential(vimInstance.getPassword())
                  .build());
      //Get Tenant ID of user
      String tenant_id = access.getToken().getTenant().get().getId();
      //Get nova endpoint
      URI endpoint = null;
      for (org.jclouds.openstack.keystone.v2_0.domain.Service service : access) {
        if (service.getName().equals("nova")) {
          for (Endpoint end : service) {
            endpoint = end.getPublicURL();
            break;
          }
          break;
        }
      }
      URL url = null;
      url = new URL(endpoint + "/os-floating-ip-pools");
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("X-Auth-Token", access.getToken().getId());
      InputStream is = null;
      is = connection.getInputStream();

      BufferedReader rd = new BufferedReader(new InputStreamReader(is));
      StringBuilder response = new StringBuilder(); // or StringBuffer if not Java 5+
      String line;
      while ((line = rd.readLine()) != null) {
        response.append(line);
        response.append('\r');
      }
      rd.close();

      JsonParser parser = new JsonParser();
      JsonObject json = (JsonObject) parser.parse(response.toString());

      JsonArray ip_pools = json.get("floating_ip_pools").getAsJsonArray();
      String ip_pool_name = ip_pools.get(0).getAsJsonObject().get("name").getAsString();
      log.info("Retrieved the name of ip pool: " + ip_pool_name);
      if (connection != null) {
        connection.disconnect();
      }
      return ip_pool_name;
    } catch (Exception e) {
      log.warn("An error during trying to find the name of the pool");
      return null;
    }
  }

  //allocated a number of ips from the pool
  public void get_allocated(VimInstance vimInstance, String pool_name, int ipsNeeded) {
    if (pool_name == null) {
      return;
    }

    try {
      NovaApi novaApi =
          ContextBuilder.newBuilder("openstack-nova")
              .endpoint(vimInstance.getAuthUrl())
              .credentials(
                  vimInstance.getTenant() + ":" + vimInstance.getUsername(),
                  vimInstance.getPassword())
              .modules(modules)
              .overrides(overrides)
              .buildApi(NovaApi.class);
      if (novaApi.getFloatingIPApi(getZone(vimInstance)).isPresent()) {
        org.jclouds.openstack.nova.v2_0.extensions.FloatingIPApi floatingIPApi =
            novaApi.getFloatingIPApi(getZone(vimInstance)).get();
        while (ipsNeeded > 0) {
          log.debug("Allocating ip from pool: " + pool_name);
          org.jclouds.openstack.nova.v2_0.domain.FloatingIP ip =
              floatingIPApi.allocateFromPool(pool_name);
          if (ip == null) {
            log.warn("There are not enough ips in the pool for instantiation");
          }
          log.info("Allocated new ip from pool " + pool_name + "Data about ip: " + ip.toString());
          ipsNeeded--;
        }
      } else {
        log.warn("Could not access floating ip API");
      }
    } catch (Exception e) {
      log.warn(
          "It was impossible to allocate more floating ips because the quota is riched or floatingapis are not "
              + "available");
      //throw new VimDriverException(e.getMessage());
    }
    return;
  }

  private String findFloatingIpId(String floatingIp, VimInstance vimInstance)
      throws VimDriverException, IOException {
    URI endpoint = null;
    ContextBuilder contextBuilder =
        ContextBuilder.newBuilder("openstack-nova")
            .credentials(vimInstance.getUsername(), vimInstance.getPassword())
            .endpoint(vimInstance.getAuthUrl());
    ComputeServiceContext context = contextBuilder.buildView(ComputeServiceContext.class);
    Function<Credentials, Access> auth =
        context
            .utils()
            .injector()
            .getInstance(Key.get(new TypeLiteral<Function<Credentials, Access>>() {}));

    Access access =
        auth.apply(
            new Credentials.Builder<Credentials>()
                .identity(vimInstance.getTenant() + ":" + vimInstance.getUsername())
                .credential(vimInstance.getPassword())
                .build());

    log.debug("listing FloatingIPs: finding endpoint");
    for (org.jclouds.openstack.keystone.v2_0.domain.Service service : access) {
      if (service.getName().equals("neutron")) {
        for (Endpoint end : service) {
          endpoint = end.getPublicURL();
          break;
        }
        break;
      }
    }

    HttpURLConnection connection = null;
    URL url = new URL(endpoint + "/v2.0/floatingips.json");
    connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");
    connection.setDoOutput(true);
    connection.setRequestProperty("Accept", "application/json");
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty("User-Agent", "python-neutronclient");
    connection.setRequestProperty("X-Auth-Token", access.getToken().getId());

    InputStream is = connection.getInputStream();
    BufferedReader rd = new BufferedReader(new InputStreamReader(is));
    StringBuilder response = new StringBuilder(); // or StringBuffer if not Java 5+
    String line;
    while ((line = rd.readLine()) != null) {
      response.append(line);
      response.append('\r');
    }
    rd.close();
    //Parse json to object
    log.debug("Associating FloatingIP: Response of final request is: " + response.toString());

    JsonObject res =
        new GsonBuilder()
            .setPrettyPrinting()
            .create()
            .fromJson(response.toString(), JsonObject.class);

    if (res.has("floatingips")) {
      for (JsonElement element : res.get("floatingips").getAsJsonArray()) {
        log.debug("FloatingIp is: " + element.getAsJsonObject());
        String floating_ip_address =
            element.getAsJsonObject().get("floating_ip_address").getAsString();
        log.debug("found ip: " + floating_ip_address);
        log.debug(floating_ip_address + " == " + floatingIp);
        if (floating_ip_address.equals(floatingIp)) {
          return element.getAsJsonObject().get("id").getAsString();
        }
      }
    } else {
      log.warn("Was not possible through Openstack ReST api to retreive all the FloatingIP");
    }
    throw new VimDriverException(
        "looking for a floating ip id of a not existing floating ip. Sorry for that, we can't really implement very "
            + "well:(");
  }

  private Map<String, String> listPorts(Access access, URI endpoint, VimInstance vimInstance)
      throws IOException {
    Map<String, String> result = new HashMap<>();
    HttpURLConnection connection = null;
    // curl -g -i -X GET http://192.168.145.70:9696/v2.0/ports.json -H "User-Agent: python-neutronclient" -H "Accept:
    // application/json" -H "X-Auth-Token: {SHA1}30473af2f293a9d6b758bce6a82c8061e5593781"
    URL url = new URL(endpoint + "/v2.0/ports.json");
    connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");
    connection.setDoOutput(true);
    connection.setRequestProperty("Accept", "application/json");
    connection.setRequestProperty("User-Agent", "python-neutronclient");
    connection.setRequestProperty("X-Auth-Token", access.getToken().getId());

    InputStream is = connection.getInputStream();
    BufferedReader rd = new BufferedReader(new InputStreamReader(is));
    StringBuilder response = new StringBuilder(); // or StringBuffer if not Java 5+
    String line;
    while ((line = rd.readLine()) != null) {
      response.append(line);
      response.append('\r');
    }
    rd.close();
    //Parse json to object
    log.debug("Listing Ports: Response of final request is: " + response.toString());

    JsonObject ports =
        new GsonBuilder()
            .setPrettyPrinting()
            .create()
            .fromJson(response.toString(), JsonObject.class);

    for (JsonElement port : ports.get("ports").getAsJsonArray()) {
      for (JsonElement ip : port.getAsJsonObject().get("fixed_ips").getAsJsonArray()) {
        result.put(
            ip.getAsJsonObject().get("ip_address").getAsString(),
            port.getAsJsonObject().get("id").getAsString());
      }
    }

    log.debug("Found all the ports: " + result);
    return result;
  }

  @Override
  public String getType(VimInstance vimInstance) {
    return "openstack";
  }
}
