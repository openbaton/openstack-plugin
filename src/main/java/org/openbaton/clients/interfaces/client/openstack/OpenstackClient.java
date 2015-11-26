/*
 * Copyright (c) 2015 Fraunhofer FOKUS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openbaton.clients.interfaces.client.openstack;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import org.jclouds.Constants;
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
import org.jclouds.openstack.glance.v1_0.options.UpdateImageOptions;
import org.jclouds.openstack.keystone.v2_0.config.CredentialTypes;
import org.jclouds.openstack.keystone.v2_0.config.KeystoneProperties;
import org.jclouds.openstack.keystone.v2_0.domain.Access;
import org.jclouds.openstack.keystone.v2_0.domain.Endpoint;
import org.jclouds.openstack.neutron.v2.NeutronApi;
import org.jclouds.openstack.neutron.v2.domain.*;
import org.jclouds.openstack.neutron.v2.domain.Network.CreateNetwork;
import org.jclouds.openstack.neutron.v2.domain.Network.UpdateNetwork;
import org.jclouds.openstack.neutron.v2.domain.Subnet.CreateSubnet;
import org.jclouds.openstack.neutron.v2.domain.Subnet.UpdateSubnet;
import org.jclouds.openstack.neutron.v2.extensions.RouterApi;
import org.jclouds.openstack.neutron.v2.features.NetworkApi;
import org.jclouds.openstack.neutron.v2.features.PortApi;
import org.jclouds.openstack.neutron.v2.features.SubnetApi;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.domain.Address;
import org.jclouds.openstack.nova.v2_0.domain.FloatingIP;
import org.jclouds.openstack.nova.v2_0.domain.RebootType;
import org.jclouds.openstack.nova.v2_0.features.FlavorApi;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.jclouds.scriptbuilder.ScriptBuilder;
import org.jclouds.scriptbuilder.domain.OsFamily;
import org.openbaton.catalogue.mano.common.DeploymentFlavour;
import org.openbaton.catalogue.nfvo.*;
import org.openbaton.catalogue.nfvo.Network;
import org.openbaton.catalogue.nfvo.Subnet;
import org.openbaton.plugin.PluginStarter;
import org.openbaton.vim.drivers.exceptions.VimDriverException;
import org.openbaton.vim.drivers.interfaces.VimDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import static org.jclouds.scriptbuilder.domain.Statements.exec;

/**
 * Created by mpa on 06.05.15.
 */
public class OpenstackClient extends VimDriver {

    private static final Pattern PATTERN = Pattern.compile(
            "^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");
    Iterable<Module> modules;
    //private KeystoneApi keystoneApi;
    Properties overrides;
    private Logger log = LoggerFactory.getLogger(this.getClass());

    public OpenstackClient() throws RemoteException {
        super();
        init();
    }

    public static boolean validate(final String ip) {
        return PATTERN.matcher(ip).matches();
    }

    public static void main(String[] args) throws NoSuchMethodException, IOException, InstantiationException, TimeoutException, IllegalAccessException, InvocationTargetException {
        if (args.length == 4)
            PluginStarter.registerPlugin(OpenstackClient.class, args[0], args[1], Integer.parseInt(args[2]), Integer.parseInt(args[3]));
        else
            PluginStarter.registerPlugin(OpenstackClient.class, "openstack", "localhost", 5672, 10);
    }

    public void init() {
        modules = ImmutableSet.<Module>of(new SLF4JLoggingModule());
        overrides = new Properties();
        overrides.setProperty(KeystoneProperties.CREDENTIAL_TYPE, CredentialTypes.PASSWORD_CREDENTIALS);
        overrides.setProperty(Constants.PROPERTY_RELAX_HOSTNAME, "true");
        overrides.setProperty(Constants.PROPERTY_TRUST_ALL_CERTS, "true");
        //NovaApi novaApi = ContextBuilder.newBuilder("openstack-nova").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NovaApi.class);
        //NeutronApi neutronApi = ContextBuilder.newBuilder("openstack-neutron").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NeutronApi.class);
        //GlanceApi glanceApi = ContextBuilder.newBuilder("openstack-glance").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(GlanceApi.class);
        //this.keystoneApi = ContextBuilder.newBuilder("openstack-keystone").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(KeystoneApi.class);
        //this.tenantId = keystoneApi.getTenantApi().get().getByName(vimInstance.getTenant()).getId();
        //String tenantId = getTenantId(vimInstance);
    }

    public String getZone(VimInstance vimInstance) {
        NovaApi novaApi = ContextBuilder.newBuilder("openstack-nova").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NovaApi.class);
        Set<String> zones = novaApi.getConfiguredRegions();
        String zone = zones.iterator().next();
        return zone;
    }

    @Override
    public Server launchInstance(VimInstance vimInstance, String name, String imageId, String flavorId, String keypair, Set<String> network, Set<String> secGroup, String userData) throws VimDriverException {
        try {
            NovaApi novaApi = ContextBuilder.newBuilder("openstack-nova").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NovaApi.class);
            ServerApi serverApi = novaApi.getServerApi(getZone(vimInstance));
            String script = new ScriptBuilder().addStatement(exec(userData)).render(OsFamily.UNIX);
            CreateServerOptions options = CreateServerOptions.Builder.keyPairName(keypair).networks(network).securityGroupNames(secGroup).userData(script.getBytes());
            String extId = serverApi.create(name, imageId, flavorId, options).getId();
            Server server = getServerById(vimInstance, extId);
            log.debug("Created Server: " + server);
            return server;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    public Server launchInstanceAndWait(VimInstance vimInstance, String name, String imageId, String flavorId, String keypair, Set<String> network, Set<String> secGroup, String userData) throws VimDriverException {
        return launchInstanceAndWait(vimInstance, name, imageId, flavorId, keypair, network, secGroup, userData, null);
    }

    @Override
    public Server launchInstanceAndWait(VimInstance vimInstance, String name, String imageId, String flavorId, String keypair, Set<String> network, Set<String> secGroup, String userData, Map<String, String> floatingIp) throws VimDriverException {
        boolean bootCompleted = false;
        log.info("Deploying VM on VimInstance: " + vimInstance.getName());
        Server server = launchInstance(vimInstance, name, imageId, flavorId, keypair, network, secGroup, userData);
        log.info("Deployed VM ( " + server.getName() + " ) with extId: " + server.getExtId() + " in status " + server.getStatus());
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
                throw new VimDriverException(server.getExtendedStatus());
            }
        }
        if (floatingIp != null && floatingIp.size() > 0) {
            log.debug("Assigning FloatingIPs to VM with hostname: " + name);
            log.debug("FloatingIPs are: " + floatingIp);
            if (listFreeFloatingIps(vimInstance).size() >= floatingIp.size()) {
                for (Map.Entry<String, String> fip : floatingIp.entrySet()) {
                    associateFloatingIpToNetwork(vimInstance, server, fip);
                    log.info("Assigned FloatingIPs to VM with hostname: " + name + " -> FloatingIPs: " + server.getFloatingIps());
                }
            } else {
                log.error("Cannot assign FloatingIPs to VM with hostname: " + name + ". No FloatingIPs left...");
            }
        }
        return server;
    }

    private String getNetworkIdByName(VimInstance vimInstance, String key) throws VimDriverException {
        for (Network n : this.listNetworks(vimInstance)) {
            if (n.getName().equals(key))
                return n.getExtId();
        }
        return null;
    }

    public void rebootServer(VimInstance vimInstance, String extId, RebootType type) throws VimDriverException {
        try {
            NovaApi novaApi = ContextBuilder.newBuilder("openstack-nova").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NovaApi.class);
            ServerApi serverApi = novaApi.getServerApi(getZone(vimInstance));
            serverApi.reboot(extId, type);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    public void deleteServerById(VimInstance vimInstance, String extId) throws VimDriverException {
        try {
            NovaApi novaApi = ContextBuilder.newBuilder("openstack-nova").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NovaApi.class);
            ServerApi serverApi = novaApi.getServerApi(getZone(vimInstance));
            serverApi.delete(extId);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    @Override
    public void deleteServerByIdAndWait(VimInstance vimInstance, String extId) throws VimDriverException {
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
            GlanceApi glanceApi = ContextBuilder.newBuilder("openstack-glance").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(GlanceApi.class);
            ImageApi imageApi = glanceApi.getImageApi(getZone(vimInstance));
            List<NFVImage> images = new ArrayList<NFVImage>();
            for (ImageDetails jcloudsImage : imageApi.listInDetail().concat()) {
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
            log.info("Listed images for VimInstance with name: " + vimInstance.getName() + " -> Images: " + images);
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
            NovaApi novaApi = ContextBuilder.newBuilder("openstack-nova").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NovaApi.class);
            ServerApi serverApi = novaApi.getServerApi(getZone(vimInstance));
            String tenantId = getTenantId(vimInstance);
            for (org.jclouds.openstack.nova.v2_0.domain.Server jcloudsServer : serverApi.listInDetail().concat()) {
                if (jcloudsServer.getTenantId().equals(tenantId)) {
                    log.debug("Found jclouds VM: " + jcloudsServer);
                    Server server = new Server();
                    server.setExtId(jcloudsServer.getId());
                    server.setName(jcloudsServer.getName());
                    server.setStatus(jcloudsServer.getStatus().value());
                    server.setExtendedStatus(jcloudsServer.getExtendedStatus().toString());
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
                    server.setImage(getImageById(vimInstance, jcloudsServer.getImage().getId()));
                    server.setFlavor(getFlavorById(vimInstance, jcloudsServer.getFlavor().getId()));
                    log.debug("Found VM: " + server);
                    servers.add(server);
                }
            }
            log.info("Listed all VMs on VimInstance with name: " + vimInstance.getName() + " -> VMs: " + servers);
            return servers;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    private Server getServerById(VimInstance vimInstance, String extId) throws VimDriverException {
        log.debug("Finding VM by ID: " + extId + " on VimInstance with name: " + vimInstance.getName());
        try {
            NovaApi novaApi = ContextBuilder.newBuilder("openstack-nova").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NovaApi.class);
            ServerApi serverApi = novaApi.getServerApi(getZone(vimInstance));
            org.jclouds.openstack.nova.v2_0.domain.Server jcloudsServer = serverApi.get(extId);
            log.debug("Found jclouds VM by ID: " + extId + " on VimInstance with name: " + vimInstance.getName() + " -> VM: " + jcloudsServer);
            Server server = new Server();
            server.setExtId(jcloudsServer.getId());
            server.setName(jcloudsServer.getName());
            server.setStatus(jcloudsServer.getStatus().value());
            server.setExtendedStatus(jcloudsServer.getExtendedStatus().toString());
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
            server.setImage(getImageById(vimInstance, jcloudsServer.getImage().getId()));
            server.setFlavor(getFlavorById(vimInstance, jcloudsServer.getFlavor().getId()));
            log.info("Found VM by ID: " + extId + " on VimInstance with name: " + vimInstance.getName() + " -> VM: " + server);
            return server;
        } catch (NullPointerException e) {
            log.debug("Not found jclouds VM by ID: " + extId + " on VimInstance with name: " + vimInstance.getName());
            throw new NullPointerException("Not found Server with ExtId: " + extId + " on VimInstance with name: " + vimInstance.getName());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    @Override
    public NFVImage addImage(VimInstance vimInstance, NFVImage image, byte[] imageFile) throws VimDriverException {
        NFVImage addedImage = addImage(vimInstance, image.getName(), new ByteArrayInputStream(imageFile), image.getDiskFormat(), image.getContainerFormat(), image.getMinDiskSpace(), image.getMinRam(), image.isPublic());
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

    private NFVImage addImage(VimInstance vimInstance, String name, InputStream payload, String diskFormat, String containerFormat, long minDisk, long minRam, boolean isPublic) throws VimDriverException {
        log.debug("Adding Image (with image file) with name: " + name + " to VimInstance with name: " + vimInstance.getName());
        try {
            GlanceApi glanceApi = ContextBuilder.newBuilder("openstack-glance").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(GlanceApi.class);
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
                e.printStackTrace();
            }
            ImageDetails imageDetails = imageApi.create(name, jcloudsPayload, new CreateImageOptions[]{createImageOptions});
            log.debug("Added jclouds Image: " + imageDetails + " to VimInstance with name: " + vimInstance.getName());
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
            log.info("Added Image with name: " + name + " to VimInstance with name: " + vimInstance.getName() + " -> Image: " + image);
            return image;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    @Override
    public NFVImage addImage(VimInstance vimInstance, NFVImage image, String image_url) throws VimDriverException {
        NFVImage addedImage = addImage(vimInstance, image.getName(), image_url, image.getDiskFormat(), image.getContainerFormat(), image.getMinDiskSpace(), image.getMinRam(), image.isPublic());
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

    private NFVImage addImage(VimInstance vimInstance, String name, String image_url, String diskFormat, String containerFromat, long minDisk, long minRam, boolean isPublic) throws VimDriverException {
        log.debug("Adding Image (with image url) with name: " + name + " to VimInstance with name: " + vimInstance.getName());
        try {
            GlanceApi glanceApi = ContextBuilder.newBuilder("openstack-glance").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(GlanceApi.class);
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
            ImageDetails imageDetails = imageApi.reserve(name, new CreateImageOptions[]{createImageOptions});
            log.debug("Added jclouds Image: " + imageDetails + " to VimInstance with name: " + vimInstance.getName());
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
            log.info("Added Image with name: " + name + " to VimInstance with name: " + vimInstance.getName() + " -> Image: " + image);
            return image;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    @Override
    public boolean deleteImage(VimInstance vimInstance, NFVImage image) throws VimDriverException {
        log.debug("Deleting Image with name: " + image.getName() + " (ExtId: " + image.getExtId() + ") from VimInstance with name: " + vimInstance.getName());
        try {
            GlanceApi glanceApi = ContextBuilder.newBuilder("openstack-glance").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(GlanceApi.class);
            ImageApi imageApi = glanceApi.getImageApi(getZone(vimInstance));
            boolean isDeleted = imageApi.delete(image.getExtId());
            log.info("Deleted Image with name: " + image.getName() + " (ExtId: " + image.getExtId() + ") from VimInstance with name: " + vimInstance.getName());
            return isDeleted;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    @Override
    public NFVImage updateImage(VimInstance vimInstance, NFVImage image) throws VimDriverException {
        NFVImage updatedImage = updateImage(vimInstance, image.getExtId(), image.getName(), image.getDiskFormat(), image.getContainerFormat(), image.getMinDiskSpace(), image.getMinRam(), image.isPublic());
        image.setName(updatedImage.getName());
        image.setExtId(updatedImage.getExtId());
        image.setCreated(updatedImage.getCreated());
        image.setUpdated(updatedImage.getUpdated());
        image.setMinDiskSpace(updatedImage.getMinDiskSpace());
        image.setMinRam(updatedImage.getMinRam());
        image.setIsPublic(updatedImage.isPublic());
        image.setDiskFormat(updatedImage.getDiskFormat());
        image.setContainerFormat(updatedImage.getContainerFormat());
        log.info("Updated Image with name: " + image.getName() + " (ExtId: " + image.getExtId() + ") on VimInstance with name: " + vimInstance.getName());
        return image;
    }

    private NFVImage updateImage(VimInstance vimInstance, String extId, String name, String diskFormat, String containerFormat, long minDisk, long minRam, boolean isPublic) throws VimDriverException {
        log.debug("Updating Image with name: " + name + " (ExtId: " + extId + ") on VimInstance with name: " + vimInstance.getName());
        try {
            GlanceApi glanceApi = ContextBuilder.newBuilder("openstack-glance").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(GlanceApi.class);
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
            log.info("Updated Image with name: " + image.getName() + " (ExtId: " + image.getExtId() + ") on VimInstance with name: " + vimInstance.getName() + " -> updated Image: " + image);
            return image;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    @Override
    public NFVImage copyImage(VimInstance vimInstance, NFVImage image, byte[] imageFile) throws VimDriverException {
        NFVImage copiedImage = copyImage(vimInstance, image.getName(), new ByteArrayInputStream(imageFile), image.getDiskFormat(), image.getContainerFormat(), image.getMinDiskSpace(), image.getMinRam(), image.isPublic());
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

    private NFVImage copyImage(VimInstance vimInstance, String name, InputStream inputStream, String diskFormat, String containerFormat, long minDisk, long minRam, boolean isPublic) throws VimDriverException {
        try {
            GlanceApi glanceApi = ContextBuilder.newBuilder("openstack-glance").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(GlanceApi.class);
            ImageApi imageApi = glanceApi.getImageApi(getZone(vimInstance));
            NFVImage image = addImage(vimInstance, name, inputStream, diskFormat, containerFormat, minDisk, minRam, isPublic);
            return image;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    private NFVImage getImageById(VimInstance vimInstance, String extId) throws VimDriverException {
        log.debug("Finding Image by ExtId: " + extId);
        try {
            NovaApi novaApi = ContextBuilder.newBuilder("openstack-nova").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NovaApi.class);
            org.jclouds.openstack.nova.v2_0.features.ImageApi imageApi = novaApi.getImageApi(getZone(vimInstance));
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
            log.warn(e.getMessage(), new NullPointerException("Image with extId: " + extId + " not found."));
            NFVImage image = new NFVImage();
            image.setExtId(extId);
            return image;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    @Override
    public DeploymentFlavour addFlavor(VimInstance vimInstance, DeploymentFlavour flavor) throws VimDriverException {
        DeploymentFlavour addedFlavor = addFlavor(vimInstance, flavor.getFlavour_key(), flavor.getVcpus(), flavor.getRam(), flavor.getDisk());
        flavor.setExtId(addedFlavor.getExtId());
        flavor.setFlavour_key(addedFlavor.getFlavour_key());
        flavor.setVcpus(addedFlavor.getVcpus());
        flavor.setRam(addedFlavor.getRam());
        flavor.setDisk(addedFlavor.getVcpus());
        return flavor;
    }

    private DeploymentFlavour addFlavor(VimInstance vimInstance, String name, int vcpus, int ram, int disk) throws VimDriverException {
        log.debug("Adding Flavor with name: " + name + " to VimInstance with name: " + vimInstance.getName());
        try {
            NovaApi novaApi = ContextBuilder.newBuilder("openstack-nova").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NovaApi.class);
            FlavorApi flavorApi = novaApi.getFlavorApi(getZone(vimInstance));
            UUID id = java.util.UUID.randomUUID();
            org.jclouds.openstack.nova.v2_0.domain.Flavor newFlavor = org.jclouds.openstack.nova.v2_0.domain.Flavor.builder().id(id.toString()).name(name).disk(disk).ram(ram).vcpus(vcpus).build();
            org.jclouds.openstack.nova.v2_0.domain.Flavor jcloudsFlavor = flavorApi.create(newFlavor);
            DeploymentFlavour flavor = new DeploymentFlavour();
            flavor.setExtId(jcloudsFlavor.getId());
            flavor.setFlavour_key(jcloudsFlavor.getName());
            flavor.setVcpus(jcloudsFlavor.getVcpus());
            flavor.setRam(jcloudsFlavor.getRam());
            flavor.setDisk(jcloudsFlavor.getVcpus());
            log.info("Added Flavor with name: " + name + " to VimInstance with name: " + vimInstance.getName() + " -> Flavor: " + flavor);
            return flavor;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    @Override
    public DeploymentFlavour updateFlavor(VimInstance vimInstance, DeploymentFlavour flavor) throws VimDriverException {
        DeploymentFlavour updatedFlavor = updateFlavor(vimInstance, flavor.getExtId(), flavor.getFlavour_key(), flavor.getVcpus(), flavor.getRam(), flavor.getDisk());
        flavor.setFlavour_key(updatedFlavor.getFlavour_key());
        flavor.setExtId(updatedFlavor.getExtId());
        flavor.setRam(updatedFlavor.getRam());
        flavor.setDisk(updatedFlavor.getDisk());
        flavor.setVcpus(updatedFlavor.getVcpus());
        return flavor;
    }

    private DeploymentFlavour updateFlavor(VimInstance vimInstance, String extId, String name, int vcpus, int ram, int disk) throws VimDriverException {
        log.debug("Updating Flavor with name: " + name + " on VimInstance with name: " + vimInstance.getName());
        try {
            NovaApi novaApi = ContextBuilder.newBuilder("openstack-nova").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NovaApi.class);
            FlavorApi flavorApi = novaApi.getFlavorApi(getZone(vimInstance));
            boolean isDeleted = deleteFlavor(vimInstance, extId);
            if (isDeleted) {
                org.jclouds.openstack.nova.v2_0.domain.Flavor newFlavor = org.jclouds.openstack.nova.v2_0.domain.Flavor.builder().id(extId).name(name).disk(disk).ram(ram).vcpus(vcpus).build();
                org.jclouds.openstack.nova.v2_0.domain.Flavor jcloudsFlavor = flavorApi.create(newFlavor);
                DeploymentFlavour updatedFlavor = new DeploymentFlavour();
                updatedFlavor.setExtId(jcloudsFlavor.getId());
                updatedFlavor.setFlavour_key(jcloudsFlavor.getName());
                updatedFlavor.setVcpus(jcloudsFlavor.getVcpus());
                updatedFlavor.setRam(jcloudsFlavor.getRam());
                updatedFlavor.setDisk(jcloudsFlavor.getVcpus());
                log.info("Updated Flavor with name: " + name + " on VimInstance with name: " + vimInstance.getName() + " -> Flavor: " + updatedFlavor);
                return updatedFlavor;
            } else {
                throw new VimDriverException("Image with extId: " + extId + " not updated successfully. Not able to delete it and create a new one.");
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    @Override
    public boolean deleteFlavor(VimInstance vimInstance, String extId) throws VimDriverException {
        log.debug("Deleting Flavor with ExtId: " + extId + " from VimInstance with name: " + vimInstance.getName());
        try {
            NovaApi novaApi = ContextBuilder.newBuilder("openstack-nova").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NovaApi.class);
            FlavorApi flavorApi = novaApi.getFlavorApi(getZone(vimInstance));
            flavorApi.delete(extId);
            boolean isDeleted;
            try {
                getFlavorById(vimInstance, extId);
                isDeleted = false;
                log.warn("Not deleted Flavor with ExtId: " + extId + " from VimInstance with name: " + vimInstance.getName());
            } catch (NullPointerException e) {
                isDeleted = true;
                log.info("Deleted Flavor with ExtId: " + extId + " from VimInstance with name: " + vimInstance.getName());
            }
            return isDeleted;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    private DeploymentFlavour getFlavorById(VimInstance vimInstance, String extId) throws VimDriverException {
        log.debug("Finding Flavor with ExtId: " + extId + " on VimInstance with name: " + vimInstance.getName());
        try {
            NovaApi novaApi = ContextBuilder.newBuilder("openstack-nova").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NovaApi.class);
            FlavorApi flavorApi = novaApi.getFlavorApi(getZone(vimInstance));
            org.jclouds.openstack.nova.v2_0.domain.Flavor jcloudsFlavor = flavorApi.get(extId);
            DeploymentFlavour flavor = new DeploymentFlavour();
            flavor.setFlavour_key(jcloudsFlavor.getName());
            flavor.setExtId(jcloudsFlavor.getId());
            flavor.setRam(jcloudsFlavor.getRam());
            flavor.setDisk(jcloudsFlavor.getDisk());
            flavor.setVcpus(jcloudsFlavor.getVcpus());
            log.info("Found Flavor with ExtId: " + extId + " on VimInstance with name: " + vimInstance.getName());
            return flavor;
        } catch (NullPointerException e) {
            throw new NullPointerException("Flavor with extId: " + extId + " not found on VimInstance with name: " + vimInstance.getName());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    @Override
    public List<DeploymentFlavour> listFlavors(VimInstance vimInstance) throws VimDriverException {
        log.debug("Listing Flavours on VimInstance with name: " + vimInstance.getName());
        try {
            NovaApi novaApi = ContextBuilder.newBuilder("openstack-nova").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NovaApi.class);
            FlavorApi flavorApi = novaApi.getFlavorApi(getZone(vimInstance));
            List<DeploymentFlavour> flavors = new ArrayList<DeploymentFlavour>();
            for (org.jclouds.openstack.nova.v2_0.domain.Flavor jcloudsFlavor : flavorApi.listInDetail().concat()) {
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
            log.info("Listed Flavours on VimInstance with name: " + vimInstance.getName() + " -> Flavours: " + flavors);
            return flavors;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    @Override
    public Network createNetwork(VimInstance vimInstance, Network network) throws VimDriverException {
        Network createdNetwork = createNetwork(vimInstance, network.getName(), network.isExternal(), network.isShared());
        network.setName(createdNetwork.getName());
        network.setExtId(createdNetwork.getExtId());
        network.setExternal(createdNetwork.isExternal());
        network.setShared(createdNetwork.isShared());
        return network;
    }

    private Network createNetwork(VimInstance vimInstance, String name, boolean external, boolean shared) throws VimDriverException {
        log.debug("Creating Network with name: " + name + " on VimInstance with name: " + vimInstance.getName());
        try {
            NeutronApi neutronApi = ContextBuilder.newBuilder("openstack-neutron").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NeutronApi.class);
            NetworkApi networkApi = neutronApi.getNetworkApi(getZone(vimInstance));
            //CreateNetwork createNetwork = CreateNetwork.createBuilder(name).networkType(NetworkType.fromValue(networkType)).external(external).shared(shared).segmentationId(segmentationId).physicalNetworkName(physicalNetworkName).build();
            CreateNetwork createNetwork = CreateNetwork.createBuilder(name).external(external).shared(shared).build();
            log.debug("Initialized jclouds Network: " + createNetwork);
            org.jclouds.openstack.neutron.v2.domain.Network jcloudsNetwork = networkApi.create(createNetwork);
            log.debug("Created jclouds Network: " + jcloudsNetwork);
            Network network = new Network();
            network.setName(jcloudsNetwork.getName());
            network.setExtId(jcloudsNetwork.getId());
            network.setExternal(jcloudsNetwork.getExternal());
            network.setShared(jcloudsNetwork.getShared());
            log.info("Created Network with name: " + name + " on VimInstance with name: " + vimInstance.getName() + " -> Network: " + network);
            return network;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    @Override
    public Network updateNetwork(VimInstance vimInstance, Network network) throws VimDriverException {
        Network updatedNetwork = updateNetwork(vimInstance, network.getExtId(), network.getName(), network.isExternal(), network.isShared());
        network.setName(updatedNetwork.getName());
        network.setExtId(updatedNetwork.getExtId());
        network.setExternal(updatedNetwork.isExternal());
        network.setShared(updatedNetwork.isShared());
        return network;
    }

    private Network updateNetwork(VimInstance vimInstance, String extId, String name, boolean external, boolean shared) throws VimDriverException {
        log.debug("Updating Network with name: " + name + " on VimInstance with name: " + vimInstance.getName());
        try {
            NeutronApi neutronApi = ContextBuilder.newBuilder("openstack-neutron").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NeutronApi.class);
            NetworkApi networkApi = neutronApi.getNetworkApi(getZone(vimInstance));
            //Plugin does not support updating provider attributes. -> NetworkType, SegmentationId, physicalNetworkName
            UpdateNetwork updateNetwork = UpdateNetwork.updateBuilder().name(name).build();
            org.jclouds.openstack.neutron.v2.domain.Network jcloudsNetwork = networkApi.update(extId, updateNetwork);
            Network network = new Network();
            network.setName(jcloudsNetwork.getName());
            network.setExtId(jcloudsNetwork.getId());
            network.setExternal(jcloudsNetwork.getExternal());
            network.setShared(jcloudsNetwork.getShared());
            log.debug("Updated Network with name: " + name + " on VimInstance with name: " + vimInstance.getName() + " -> Network: " + network);
            return network;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    @Override
    public boolean deleteNetwork(VimInstance vimInstance, String extId) throws VimDriverException {
        log.debug("Deleting Network with ExtId: " + extId + " from VimInstance with name: " + vimInstance.getName());
        try {
            NeutronApi neutronApi = ContextBuilder.newBuilder("openstack-neutron").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NeutronApi.class);
            NetworkApi networkApi = neutronApi.getNetworkApi(getZone(vimInstance));
            boolean isDeleted = networkApi.delete(extId);
            if (isDeleted == true) {
                log.debug("Deleted Network with ExtId: " + extId + " from VimInstance with name: " + vimInstance.getName());
            } else {
                log.debug("Not deleted Network with ExtId: " + extId + " from VimInstance with name: " + vimInstance.getName());
            }
            return isDeleted;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    @Override
    public Network getNetworkById(VimInstance vimInstance, String extId) throws VimDriverException {
        log.debug("Finding Network with ExtId: " + extId + " on VimInstance with name: " + vimInstance.getName());
        try {
            NeutronApi neutronApi = ContextBuilder.newBuilder("openstack-neutron").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NeutronApi.class);
            NetworkApi networkApi = neutronApi.getNetworkApi(getZone(vimInstance));
            org.jclouds.openstack.neutron.v2.domain.Network jcloudsNetwork = networkApi.get(extId);
            Network network = new Network();
            network.setName(jcloudsNetwork.getName());
            network.setExtId(jcloudsNetwork.getId());
            network.setExternal(jcloudsNetwork.getExternal());
            network.setShared(jcloudsNetwork.getShared());
            log.info("Found Network with ExtId: " + extId + " on VimInstance with name: " + vimInstance.getName());
            return network;
        } catch (NullPointerException e) {
            throw new NullPointerException("Not found Network with ExtId: " + extId + " on VimInstance with name: " + vimInstance.getName());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    @Override
    public List<String> getSubnetsExtIds(VimInstance vimInstance, String extId) throws VimDriverException {
        log.debug("Listing all external SubnetIDs for Network with ExtId: " + extId + " from VimInstance with name: " + vimInstance.getName());
        try {
            NeutronApi neutronApi = ContextBuilder.newBuilder("openstack-neutron").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NeutronApi.class);
            NetworkApi networkApi = neutronApi.getNetworkApi(getZone(vimInstance));
            List<String> subnets = new ArrayList<String>();
            org.jclouds.openstack.neutron.v2.domain.Network jcloudsNetwork = networkApi.get(extId);
            subnets = jcloudsNetwork.getSubnets().asList();
            log.info("Listed all external SubnetIDs for Network with ExtId: " + extId + " from VimInstance with name: " + vimInstance.getName() + " -> external Subnet IDs: " + subnets);
            return subnets;
        } catch (NullPointerException e) {
            throw new NullPointerException("Not found Network with ExtId: " + extId + " from VimInstance with name: " + vimInstance.getName());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    @Override
    public List<Network> listNetworks(VimInstance vimInstance) throws VimDriverException {
        log.debug("Listing all Networks of VimInstance with name: " + vimInstance.getName());
        try {
            NeutronApi neutronApi = ContextBuilder.newBuilder("openstack-neutron").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NeutronApi.class);
            List<Network> networks = new ArrayList<Network>();
            String tenantId = getTenantId(vimInstance);
            for (org.jclouds.openstack.neutron.v2.domain.Network jcloudsNetwork : neutronApi.getNetworkApi(getZone(vimInstance)).list().concat()) {
                if (jcloudsNetwork.getTenantId().equals(tenantId) || jcloudsNetwork.getShared()) {
                    log.debug("Found jclouds Network: " + jcloudsNetwork);
                    Network network = new Network();
                    network.setName(jcloudsNetwork.getName());
                    network.setExtId(jcloudsNetwork.getId());
                    network.setExternal(jcloudsNetwork.getExternal());
                    network.setShared(jcloudsNetwork.getShared());
                    log.debug("Found Network: " + network);
                    networks.add(network);
                }
            }
            log.info("Listed all Networks of VimInstance with name: " + vimInstance.getName() + " -> Networks: " + networks);
            return networks;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    @Override
    public Subnet createSubnet(VimInstance vimInstance, Network network, Subnet subnet) throws VimDriverException {
        Subnet createdSubnet = createSubnet(vimInstance, network, subnet.getName(), subnet.getCidr());
        subnet.setExtId(createdSubnet.getExtId());
        subnet.setName(createdSubnet.getName());
        subnet.setCidr(createdSubnet.getCidr());
        subnet.setGatewayIp(createdSubnet.getGatewayIp());
        return subnet;
    }

    private Subnet createSubnet(VimInstance vimInstance, Network network, String name, String cidr) throws VimDriverException {
        log.debug("Creating Subnet with name: " + name + " on Network with name: + " + network.getName() + " on VimInstance with name: " + vimInstance.getName());
        try {
            NeutronApi neutronApi = ContextBuilder.newBuilder("openstack-neutron").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NeutronApi.class);
            SubnetApi subnetApi = neutronApi.getSubnetApi(getZone(vimInstance));
            CreateSubnet createSubnet = CreateSubnet.createBuilder(network.getExtId(), cidr).name(name).dnsNameServers(ImmutableSet.<String>of(properties.getProperty("dns-nameserver"))).ipVersion(4).build();
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
            //attachInterface(vimInstance, routerId, subnet.getExtId());
            if (routerId != null) {
                String portId = createPort(vimInstance, network, subnet);
                attachPort(vimInstance, routerId, portId);
            }
            log.info("Created Subnet with name: " + name + " on Network with name: + " + network.getName() + " on VimInstance with name: " + vimInstance.getName() + " -> Subnet: " + subnet);
            return subnet;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }

    }

    @Override
    public Subnet updateSubnet(VimInstance vimInstance, Network network, Subnet subnet) throws VimDriverException {
        Subnet updatedSubnet = updateSubnet(vimInstance, network, subnet.getExtId(), subnet.getName());
        subnet.setExtId(updatedSubnet.getExtId());
        subnet.setName(updatedSubnet.getName());
        subnet.setCidr(updatedSubnet.getCidr());
        subnet.setGatewayIp(updatedSubnet.getGatewayIp());
        return subnet;
    }

    private Subnet updateSubnet(VimInstance vimInstance, Network network, String subnetExtId, String name) throws VimDriverException {
        log.debug("Updating Subnet with ExtId: " + subnetExtId + " on Network with name: + " + network.getName() + " on VimInstance with name: " + vimInstance.getName());
        try {
            NeutronApi neutronApi = ContextBuilder.newBuilder("openstack-neutron").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NeutronApi.class);
            SubnetApi subnetApi = neutronApi.getSubnetApi(getZone(vimInstance));
            //Cannot update read-only attribute cidr
            //Cannot update read-only attribute network_id
            //Cannot update read-only attribute ip_version
            UpdateSubnet updateSubnet = UpdateSubnet.updateBuilder().name(name).build();
            org.jclouds.openstack.neutron.v2.domain.Subnet jcloudsSubnet = subnetApi.update(subnetExtId, updateSubnet);
            Subnet subnet = new Subnet();
            subnet.setExtId(jcloudsSubnet.getId());
            subnet.setName(jcloudsSubnet.getName());
            subnet.setCidr(jcloudsSubnet.getCidr());
            subnet.setGatewayIp(jcloudsSubnet.getGatewayIp());
            log.debug("Updated Subnet with ExtId: " + subnetExtId + " on Network with name: + " + network.getName() + " on VimInstance with name: " + vimInstance.getName() + " -> Subnet: " + subnet);
            return subnet;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    private String getRouter(VimInstance vimInstance) throws VimDriverException {
        log.debug("Finding a Router that is connected with external Network on VimInstance with name: " + vimInstance.getName());
        try {
            NeutronApi neutronApi = ContextBuilder.newBuilder("openstack-neutron").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NeutronApi.class);
            RouterApi routerApi = neutronApi.getRouterApi(getZone(vimInstance)).get();
            PagedIterable routerList = routerApi.list();
            String tenantId = getTenantId(vimInstance);
            if (routerList.iterator().hasNext()) {
                for (Router router : (FluentIterable<Router>) routerList.concat())
                    if (router.getTenantId().equals(tenantId)) {
                        ExternalGatewayInfo externalGatewayInfo = router.getExternalGatewayInfo();
                        if (externalGatewayInfo != null) {
                            String networkId = externalGatewayInfo.getNetworkId();
                            if (getNetworkById(vimInstance, networkId).isExternal()) {
                                log.info("Found a Router that is connected with external Network on VimInstance with name: " + vimInstance.getName());
                                return router.getId();
                            }
                        }
                    }
            }
            log.warn("Not found any Router that is connected with external Network on VimInstance with name: " + vimInstance.getName());
            return null;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    private String createRouter(VimInstance vimInstance) throws VimDriverException {
        log.debug("Creating a Router that is connected with external Network on VimInstance with name: " + vimInstance.getName());
        try {
            NeutronApi neutronApi = ContextBuilder.newBuilder("openstack-neutron").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NeutronApi.class);
            RouterApi routerApi = neutronApi.getRouterApi(getZone(vimInstance)).get();
            //Find external network
            String externalNetId = null;
            log.debug("Finding an external Network where we can connect a new Router to on VimInstance with name: " + vimInstance.getName());
            for (Network network : listNetworks(vimInstance)) {
                if (network.isExternal()) {
                    log.debug("Found an external Network where we can connect a new Router to on VimInstance with name: " + vimInstance.getName() + " -> Network: " + network);
                    externalNetId = network.getExtId();
                }
            }
            if (externalNetId == null) {
                log.warn("Not found any external Network where we can connect a new Router to on VimInstance with name: " + vimInstance.getName());
                return null;
            }
            ExternalGatewayInfo externalGatewayInfo = ExternalGatewayInfo.builder().networkId(externalNetId).build();
            Router.CreateRouter options = Router.CreateRouter.createBuilder().name(vimInstance.getTenant() + "_" + (int) (Math.random() * 1000) + "_router").adminStateUp(true).externalGatewayInfo(externalGatewayInfo).build();
            Router router = routerApi.create(options);
            log.info("Created a Router that is connected with external Network on VimInstance with name: " + vimInstance.getName());
            return router.getId();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    private String attachInterface(VimInstance vimInstance, String routerId, String subnetId) throws VimDriverException {
        log.debug("Attaching Subnet with ExtId: " + subnetId + " to Router with ExtId: " + routerId + " on VimInstnace with name: " + vimInstance);
        try {
            NeutronApi neutronApi = ContextBuilder.newBuilder("openstack-neutron").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NeutronApi.class);
            RouterApi routerApi = neutronApi.getRouterApi(getZone(vimInstance)).get();
            RouterInterface routerInterface = routerApi.addInterfaceForSubnet(routerId, subnetId);
            log.info("Attached Subnet with ExtId: " + subnetId + " to Router with ExtId: " + routerId + " on VimInstnace with name: " + vimInstance);
            return routerInterface.getSubnetId();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    private String attachPort(VimInstance vimInstance, String routerId, String portId) throws VimDriverException {
        log.debug("Attaching Port with ExtId: " + portId + " to Router with ExtId: " + routerId + " on VimInstnace with name: " + vimInstance);
        try {
            NeutronApi neutronApi = ContextBuilder.newBuilder("openstack-neutron").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NeutronApi.class);
            RouterApi routerApi = neutronApi.getRouterApi(getZone(vimInstance)).get();
            RouterInterface routerInterface = routerApi.addInterfaceForPort(routerId, portId);
            log.info("Attached Port with ExtId: " + portId + " to Router with ExtId: " + routerId + " on VimInstnace with name: " + vimInstance);
            return routerInterface.getSubnetId();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    private String createPort(VimInstance vimInstance, Network network, Subnet subnet) throws VimDriverException {
        log.debug("Creating a Port for network with name: " + network.getName() + " and Subnet with name: " + subnet.getName() + " on VimInstance with name: " + vimInstance.getName());
        try {
            NeutronApi neutronApi = ContextBuilder.newBuilder("openstack-neutron").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NeutronApi.class);
            PortApi portApi = neutronApi.getPortApi(getZone(vimInstance));
            Port.CreatePort createPort = Port.createBuilder(network.getExtId()).name("Port_" + network.getName() + "_" + (int) (Math.random() * 1000)).fixedIps(ImmutableSet.of(IP.builder().ipAddress(subnet.getGatewayIp()).build())).build();
            Port port = portApi.create(createPort);
            log.info("Created a Port for network with name: " + network.getName() + " and Subnet with name: " + subnet.getName() + " on VimInstance with name: " + vimInstance.getName() + " -> Port: " + port);
            return port.getId();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    @Override
    public boolean deleteSubnet(VimInstance vimInstance, String extId) throws VimDriverException {
        log.debug("Deleting Subnet with ExtId: " + extId + " from VimInstance with name: " + vimInstance.getName());
        try {
            NeutronApi neutronApi = ContextBuilder.newBuilder("openstack-neutron").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NeutronApi.class);
            SubnetApi subnetApi = neutronApi.getSubnetApi(getZone(vimInstance));
            boolean isDeleted = subnetApi.delete(extId);
            if (isDeleted == true) {
                log.info("Deleted Subnet with ExtId: " + extId + " from VimInstance with name: " + vimInstance.getName());
            } else {
                log.warn("Not deleted Subnet with ExtId: " + extId + " from VimInstance with name: " + vimInstance.getName());
            }
            return isDeleted;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    private List<String> listFreeFloatingIps(VimInstance vimInstance) throws VimDriverException {
        log.debug("Listing all free FloatingIPs of VimInstance with name: " + vimInstance.getName());
        try {
            NovaApi novaApi = ContextBuilder.newBuilder("openstack-nova").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NovaApi.class);
            org.jclouds.openstack.nova.v2_0.extensions.FloatingIPApi floatingIPApi = novaApi.getFloatingIPApi(getZone(vimInstance)).get();
            List<String> floatingIPs = new LinkedList<String>();
            Iterator<FloatingIP> floatingIpIterator = floatingIPApi.list().iterator();
            while (floatingIpIterator.hasNext()) {
                FloatingIP floatingIP = floatingIpIterator.next();
                if (floatingIP.getInstanceId() == null) {
                    floatingIPs.add(floatingIP.getIp());
                }
            }
            log.info("Listed all free FloatingIPs of VimInstance with name: " + vimInstance.getName() + " -> free FloatingIPs: " + floatingIPs);
            return floatingIPs;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    private void associateFloatingIp(VimInstance vimInstance, Server server, String floatingIp) throws VimDriverException {
        log.debug("Associating FloatingIP: " + floatingIp + " to VM with hostname: " + server.getName() + " on VimInstance with name: " + vimInstance.getName());
        try {
            NovaApi novaApi = ContextBuilder.newBuilder("openstack-nova").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NovaApi.class);
            org.jclouds.openstack.nova.v2_0.extensions.FloatingIPApi floatingIPApi = novaApi.getFloatingIPApi(getZone(vimInstance)).get();
            floatingIPApi.addToServer(floatingIp, server.getExtId());
            server.setFloatingIps(new HashMap<String, String>());
            server.getFloatingIps().put("netname", floatingIp);
            log.info("Associated FloatingIP: " + floatingIp + " to VM with hostname: " + server.getName() + " on VimInstance with name: " + vimInstance.getName());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    public String getTenantId(VimInstance vimInstance) throws VimDriverException {
        log.debug("Finding TenantID for Tenant with name: " + vimInstance.getTenant() + " on VimInstance with name: " + vimInstance.getName());
        try {
            ContextBuilder contextBuilder = ContextBuilder.newBuilder("openstack-nova").credentials(vimInstance.getUsername(), vimInstance.getPassword()).endpoint(vimInstance.getAuthUrl());
            ComputeServiceContext context = contextBuilder.buildView(ComputeServiceContext.class);
            Function<Credentials, Access> auth = context.utils().injector().getInstance(Key.get(new TypeLiteral<Function<Credentials, Access>>() {
            }));
            //Get Access and all information
            Access access = auth.apply(new Credentials.Builder<Credentials>().identity(vimInstance.getTenant() + ":" + vimInstance.getUsername()).credential(vimInstance.getPassword()).build());
            //Get Tenant ID of user
            String tenant_id = access.getToken().getTenant().get().getId();
            log.info("Found TenantID for Tenant with name: " + vimInstance.getTenant() + " on VimInstance with name: " + vimInstance.getName() + " -> TenantID: " + tenant_id);
            return tenant_id;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        }
    }

    @Override
    public Quota getQuota(VimInstance vimInstance) throws VimDriverException {
        log.debug("Finding Quota for Tenant with name: " + vimInstance.getTenant() + " on VimInstance with name: " + vimInstance.getName());
        HttpURLConnection connection = null;
        try {
            Quota quota = new Quota();
            ContextBuilder contextBuilder = ContextBuilder.newBuilder("openstack-nova").credentials(vimInstance.getUsername(), vimInstance.getPassword()).endpoint(vimInstance.getAuthUrl());
            ComputeServiceContext context = contextBuilder.buildView(ComputeServiceContext.class);
            Function<Credentials, Access> auth = context.utils().injector().getInstance(Key.get(new TypeLiteral<Function<Credentials, Access>>() {
            }));
            //Get Access and all information
            Access access = auth.apply(new Credentials.Builder<Credentials>().identity(vimInstance.getTenant() + ":" + vimInstance.getUsername()).credential(vimInstance.getPassword()).build());
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
            log.info("Found Quota for tenant with name: " + vimInstance.getTenant() + " on VimInstance with name: " + vimInstance.getName() + " -> Quota: " + quota);
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
     * DEBUG: keystoneclient.session REQ: curl -g -i -X GET http://192.168.41.45:5000/v2.0 -H "Accept: application/json" -H "User-Agent: python-keystoneclient"
     * <p/>
     * DEBUG: keystoneclient.session RESP: [200] content-length: 423 vary: X-Auth-Token keep-alive: timeout=5, max=100 server: Apache/2.4.7 (Ubuntu) connection: Keep-Alive date: Thu, 15 Oct 2015 16:02:51 GMT content-type: application/json
     * <p/>
     * RESP BODY: {"version": {"status": "stable", "updated": "2014-04-17T00:00:00Z", "media-types": [{"base": "application/json", "type": "application/vnd.openstack.identity-v2.0+json"}, {"base": "application/xml", "type": "application/vnd.openstack.identity-v2.0+xml"}], "id": "v2.0", "links": [{"href": "http://192.168.41.45:5000/v2.0/", "rel": "self"}, {"href": "http://docs.openstack.org/", "type": "text/html", "rel": "describedby"}]}}
     * <p/>
     * <p/>
     * <p/>
     * DEBUG: neutronclient.neutron.v2_0.floatingip.AssociateFloatingIP run(Namespace(fixed_ip_address=None, floatingip_id=u'863726a7-1cb2-4625-9a7a-89acfc5a4efb', port_id=u'eaa9b1a3-019a-4ad6-bf32-03d6d52dbf15', request_format='json'))
     * <p/>
     * DEBUG: keystoneclient.auth.identity.v2 Making authentication request to http://192.168.41.45:5000/v2.0/tokens
     * <p/>
     * DEBUG: keystoneclient.session REQ: curl -g -i -X PUT http://192.168.41.45:9696/v2.0/floatingips/863726a7-1cb2-4625-9a7a-89acfc5a4efb.json -H "User-Agent: python-neutronclient" -H "Content-Type: application/json" -H "Accept: application/json" -H "X-Auth-Token: {SHA1}8e68f14d5225cf075ab87965c6d0d65f45527994" -d '{"floatingip": {"port_id": "eaa9b1a3-019a-4ad6-bf32-03d6d52dbf15"}}'
     * <p/>
     * DEBUG: keystoneclient.session RESP: [200] date: Thu, 15 Oct 2015 16:02:51 GMT connection: keep-alive content-type: application/json; charset=UTF-8 content-length: 371 x-openstack-request-id: req-3706c1e2-af82-458a-a304-320c3d9c5306
     * <p/>
     * RESP BODY: {"floatingip": {"floating_network_id": "84581ab8-fd45-468e-8cc1-dd1f0a24b18f", "router_id": "46562b53-29e0-4708-b35b-1fd25f8edb03", "fixed_ip_address": "10.0.0.113", "floating_ip_address": "192.168.41.189", "tenant_id": "7941f2d9f2f24da4be590a3d0c6d55cb", "status": "DOWN", "port_id": "eaa9b1a3-019a-4ad6-bf32-03d6d52dbf15", "id": "863726a7-1cb2-4625-9a7a-89acfc5a4efb"}}
     * <p/>
     * <p/>
     * <p/>
     * Associated floating IP 863726a7-1cb2-4625-9a7a-89acfc5a4efb
     *
     * @param vimInstance
     * @param server
     * @param fip
     * @return
     */
    public synchronized void associateFloatingIpToNetwork(VimInstance vimInstance, Server server, Map.Entry<String, String> fip) throws VimDriverException {
        log.debug("Associating FloatingIP to VM with hostname: " + server.getName() + " on VimInstance with name: " + vimInstance.getName());
        HttpURLConnection connection = null;
        try {
            String floatingIp = null;
            String privateIp = null;
            if (fip.getValue() != null) {
                if (fip.getValue().equals("random")) {
                    log.debug("Associating FloatingIP: defined random IP -> try to find one");
                    privateIp = server.getIps().get(fip.getKey()).get(0);
                    if (privateIp == null)
                        log.error("Associating FloatingIP: Cannot assign FloatingIPs to server " + server.getId() + " . wrong network" + fip.getKey());
                    else {
                        floatingIp = listFreeFloatingIps(vimInstance).get(0);
                    }
                } else if (validate(fip.getValue())) {
                    log.debug("Associating FloatingIP: " + fip.getValue());
                    privateIp = server.getIps().get(fip.getKey()).get(0);
                    if (privateIp == null)
                        log.error("Associating FloatingIP: Cannot assign FloatingIPs to server " + server.getId() + " . wrong network" + fip.getKey());
                    else {
                        floatingIp = fip.getValue();
                    }
                }
            } else
                log.error("Associating FloatingIP: Cannot assign FloatingIPs to server " + server.getId() + " . wrong floatingip: " + fip.getValue());

            log.debug(log.getClass().toString());
            ContextBuilder contextBuilder = ContextBuilder.newBuilder("openstack-nova").credentials(vimInstance.getUsername(), vimInstance.getPassword()).endpoint(vimInstance.getAuthUrl());
            ComputeServiceContext context = contextBuilder.buildView(ComputeServiceContext.class);
            Function<Credentials, Access> auth = context.utils().injector().getInstance(Key.get(new TypeLiteral<Function<Credentials, Access>>() {
            }));

            //Get Access and all information
            Access access = auth.apply(new Credentials.Builder<Credentials>().identity(vimInstance.getTenant() + ":" + vimInstance.getUsername()).credential(vimInstance.getPassword()).build());
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
            NovaApi novaApi = ContextBuilder.newBuilder("openstack-nova").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NovaApi.class);
            org.jclouds.openstack.nova.v2_0.extensions.FloatingIPApi floatingIPApi = novaApi.getFloatingIPApi(getZone(vimInstance)).get();
            Iterator<FloatingIP> floatingIpIterator = floatingIPApi.list().iterator();
            log.debug("Associating FloatingIP: finding ID of FloatingIP: " + floatingIp);
            while (floatingIpIterator.hasNext()) {
                FloatingIP floatingIP = floatingIpIterator.next();
                log.debug("Associating FloatingIP: check -> " + floatingIP.toString());
                if (floatingIP.getIp().equals(floatingIp)) {
                    floatingIpId = floatingIP.getId();
                    break;
                }
            }
            log.debug("Associating FloatingIP: looking for Port where the private IP is connected to");
            NeutronApi neutronApi = ContextBuilder.newBuilder("openstack-neutron").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NeutronApi.class);
            for (Port port : neutronApi.getPortApi(getZone(vimInstance)).list().concat()) {
                log.debug("Associating FloatingIP: Port: " + port.toString());

                String ipAddress = port.getFixedIps().iterator().next().getIpAddress();
                log.debug("IP PORT: " + ipAddress + " === " + privateIp);
                if (ipAddress.equals(privateIp)) {
                    port_id = port.getId();
                }
            }

            URL url = new URL(endpoint + "/v2.0/floatingips/" + floatingIpId + ".json");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "python-neutronclient");
            connection.setRequestProperty("X-Auth-Token", access.getToken().getId());
            OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream());
            out.write("{\"floatingip\": {\"port_id\": \"" + port_id + "\"}}");
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
            server.getFloatingIps().put(fip.getKey(), floatingIp);
            log.info("Associated FloatingIP to VM with hostname: " + server.getName() + " on VimInstance with name: " + vimInstance.getName() + " -> FloatingIP: " + floatingIp);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new VimDriverException(e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    public String getType(VimInstance vimInstance) {
        return "openstack";
    }

}

