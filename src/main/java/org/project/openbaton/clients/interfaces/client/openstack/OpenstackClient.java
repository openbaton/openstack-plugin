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

package org.project.openbaton.clients.interfaces.client.openstack;

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
import org.jclouds.collect.IterableWithMarker;
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
import org.jclouds.openstack.keystone.v2_0.KeystoneApi;
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
import org.jclouds.openstack.nova.v2_0.domain.SecurityGroup;
import org.jclouds.openstack.nova.v2_0.extensions.KeyPairApi;
import org.jclouds.openstack.nova.v2_0.extensions.SecurityGroupApi;
import org.jclouds.openstack.nova.v2_0.features.FlavorApi;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.jclouds.openstack.v2_0.domain.Resource;
import org.jclouds.scriptbuilder.ScriptBuilder;
import org.jclouds.scriptbuilder.domain.OsFamily;
import org.project.openbaton.catalogue.mano.common.DeploymentFlavour;
import org.project.openbaton.catalogue.nfvo.*;
import org.project.openbaton.catalogue.nfvo.Network;
import org.project.openbaton.catalogue.nfvo.Subnet;
import org.project.openbaton.clients.exceptions.VimDriverException;
import org.project.openbaton.clients.interfaces.ClientInterfaces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.*;

import static org.jclouds.scriptbuilder.domain.Statements.exec;

/**
 * Created by mpa on 06.05.15.
 */
public class OpenstackClient implements ClientInterfaces {

    private Logger log = LoggerFactory.getLogger(this.getClass());

    private VimInstance vimInstance;

    private NovaApi novaApi;
    private NeutronApi neutronApi;
    private GlanceApi glanceApi;
    private KeystoneApi keystoneApi;

    private String tenantId;


    private Set<String> zones;
    private String defaultZone = null;

    public OpenstackClient() throws RemoteException {
    }

    public void setNovaApi(NovaApi novaApi) {
        this.novaApi = novaApi;
    }

    public void setVimInstance(VimInstance vimInstance) {
        this.vimInstance = vimInstance;
    }

    public void setNeutronApi(NeutronApi neutronApi) {
        this.neutronApi = neutronApi;
    }

    public void setGlanceApi(GlanceApi glanceApi) {
        this.glanceApi = glanceApi;
    }

    public void init(VimInstance vimInstance) {
        Iterable<Module> modules = ImmutableSet.<Module>of(new SLF4JLoggingModule());
        Properties overrides = new Properties();
        overrides.setProperty(KeystoneProperties.CREDENTIAL_TYPE, CredentialTypes.PASSWORD_CREDENTIALS);
        overrides.setProperty(Constants.PROPERTY_RELAX_HOSTNAME, "true");
        overrides.setProperty(Constants.PROPERTY_TRUST_ALL_CERTS, "true");
        this.vimInstance = vimInstance;
        this.novaApi = ContextBuilder.newBuilder("openstack-nova").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NovaApi.class);
        this.neutronApi = ContextBuilder.newBuilder("openstack-neutron").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(NeutronApi.class);
        this.glanceApi = ContextBuilder.newBuilder("openstack-glance").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(GlanceApi.class);
        this.keystoneApi = ContextBuilder.newBuilder("openstack-keystone").endpoint(vimInstance.getAuthUrl()).credentials(vimInstance.getTenant() + ":" + vimInstance.getUsername(), vimInstance.getPassword()).modules(modules).overrides(overrides).buildApi(KeystoneApi.class);
        this.tenantId = keystoneApi.getTenantApi().get().getByName(vimInstance.getTenant()).getId();
        this.zones = novaApi.getConfiguredRegions();
        if (null == defaultZone) {
            this.defaultZone = zones.iterator().next();
        }
    }

    public void setZone(String zone) {
        if (null != zone && "" == zone) {
            defaultZone = zone;
        }
    }

    @Override
    public Server launchInstance(VimInstance vimInstance, String name, String imageId, String flavorId,
                                 String keypair, Set<String> network, Set<String> secGroup,
                                 String userData) {
        String script = new ScriptBuilder().addStatement(exec(userData)).render(OsFamily.UNIX);
        init(vimInstance);
        ServerApi serverApi = this.novaApi.getServerApi(defaultZone);
        CreateServerOptions options = CreateServerOptions.Builder.keyPairName(keypair).networks(network).securityGroupNames(secGroup).userData(script.getBytes());
        String extId = serverApi.create(name, imageId, flavorId, options).getId();
        Server server = getServerById(vimInstance, extId);
        return server;
    }

    public Server launchInstanceAndWait(VimInstance vimInstance, String name, String imageId, String flavorId, String keypair, Set<String> network, Set<String> secGroup, String userData) throws VimDriverException {
        return launchInstanceAndWait(vimInstance, name, imageId, flavorId, keypair, network, secGroup, userData, false);
    }

    @Override
    public Server launchInstanceAndWait(VimInstance vimInstance, String name, String imageId, String flavorId, String keypair, Set<String> network, Set<String> secGroup, String userData, boolean floatingIp) throws VimDriverException {
        boolean bootCompleted = false;
        Server server = launchInstance(vimInstance, name, imageId, flavorId, keypair, network, secGroup, userData);
        while (bootCompleted == false) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            server = getServerById(vimInstance, server.getExtId());
            if (server.getStatus().equals("ACTIVE")) {
                bootCompleted = true;
            }
            if (server.getStatus().equals("ERROR")) {
                throw new VimDriverException(server.getExtendedStatus());
            }
        }
        if (floatingIp)
            if (listFreeFloatingIps().size() > 0) {
                associateFloatingIp(vimInstance, server, listFreeFloatingIps().get(0));
            } else {
                log.error("Cannot assign FloatingIPs to server " + server.getId() + " . No FloatingIPs left...");
            }
        return server;
    }

    public void rebootServer(String extId, RebootType type) {
        init(vimInstance);
        ServerApi serverApi = this.novaApi.getServerApi(defaultZone);
        serverApi.reboot(extId, type);
    }

    public void deleteServerById(VimInstance vimInstance, String extId) {
        init(vimInstance);
        ServerApi serverApi = this.novaApi.getServerApi(defaultZone);
        serverApi.delete(extId);
    }

    @Override
    public void deleteServerByIdAndWait(VimInstance vimInstance, String extId) {
        init(vimInstance);
        boolean deleteCompleted = false;
        deleteServerById(vimInstance, extId);
        while (deleteCompleted == false) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            try {
                getServerById(vimInstance, extId);
            } catch (NullPointerException e) {
                deleteCompleted = true;
            }
        }
    }

    @Override
    public List<NFVImage> listImages(VimInstance vimInstance) {
        init(vimInstance);
        ImageApi imageApi = this.glanceApi.getImageApi(defaultZone);
        List<NFVImage> images = new ArrayList<NFVImage>();
        for (IterableWithMarker<ImageDetails> jcloudsImage : imageApi.listInDetail().toList()) {
            for (int i = 0; i < jcloudsImage.size(); i++) {
                NFVImage image = new NFVImage();
                image.setName(jcloudsImage.get(i).getName());
                image.setExtId(jcloudsImage.get(i).getId());
                image.setMinRam(jcloudsImage.get(i).getMinRam());
                image.setMinDiskSpace(jcloudsImage.get(i).getMinDisk());
                image.setCreated(jcloudsImage.get(i).getCreatedAt());
                image.setUpdated(jcloudsImage.get(i).getUpdatedAt());
                image.setIsPublic(jcloudsImage.get(i).isPublic());
                image.setDiskFormat(jcloudsImage.get(i).getDiskFormat().toString().toUpperCase());
                image.setContainerFormat(jcloudsImage.get(i).getContainerFormat().toString().toUpperCase());
                images.add(image);
            }
        }
        return images;
    }

    @Override
    public List<Server> listServer(VimInstance vimInstance) {
        init(vimInstance);
        List<Server> servers = new ArrayList<Server>();
        ServerApi serverApi = this.novaApi.getServerApi(defaultZone);
        for (org.jclouds.openstack.nova.v2_0.domain.Server jcloudsServer : serverApi.listInDetail().concat()) {
            if (jcloudsServer.getTenantId().equals(this.tenantId)) {
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
                server.setCreated(jcloudsServer.getCreated());
                server.setUpdated(jcloudsServer.getUpdated());
                server.setImage(getImageById(jcloudsServer.getImage().getId()));
                server.setFlavor(getFlavorById(jcloudsServer.getFlavor().getId()));
                servers.add(server);
            }
        }
        return servers;
    }

    private Server getServerById(VimInstance vimInstance, String extId) {
        init(vimInstance);
        ServerApi serverApi = this.novaApi.getServerApi(defaultZone);
        try {
            org.jclouds.openstack.nova.v2_0.domain.Server jcloudsServer = serverApi.get(extId);
            log.trace("" + jcloudsServer);
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
            server.setCreated(jcloudsServer.getCreated());
            server.setUpdated(jcloudsServer.getUpdated());
            server.setImage(getImageById(jcloudsServer.getImage().getId()));
            server.setFlavor(getFlavorById(jcloudsServer.getFlavor().getId()));
            return server;
        } catch (NullPointerException e) {
            throw new NullPointerException("Server with extId: " + extId + " not found.");
        }
    }

    @Override
    public NFVImage addImage(VimInstance vimInstance, NFVImage image, byte[] imageFile) {
        init(vimInstance);
        NFVImage addedImage = addImage(image.getName(), new ByteArrayInputStream(imageFile), image.getDiskFormat(), image.getContainerFormat(), image.getMinDiskSpace(), image.getMinRam(), image.isPublic());
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

    private NFVImage addImage(String name, InputStream payload, String diskFormat, String containerFromat, long minDisk, long minRam, boolean isPublic) {
        ImageApi imageApi = this.glanceApi.getImageApi(this.defaultZone);
        CreateImageOptions createImageOptions = new CreateImageOptions();
        createImageOptions.minDisk(minDisk);
        createImageOptions.minRam(minRam);
        createImageOptions.isPublic(isPublic);
        createImageOptions.diskFormat(DiskFormat.valueOf(diskFormat));
        createImageOptions.containerFormat(ContainerFormat.valueOf(containerFromat));

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
        return image;
    }

    @Override
    public boolean deleteImage(VimInstance vimInstance, NFVImage image) {
        init(vimInstance);
        ImageApi imageApi = this.glanceApi.getImageApi(this.defaultZone);
        boolean isDeleted = imageApi.delete(image.getExtId());
        return isDeleted;
    }

    @Override
    public NFVImage updateImage(VimInstance vimInstance, NFVImage image) {
        init(vimInstance);
        NFVImage updatedImage = updateImage(image.getExtId(), image.getName(), image.getDiskFormat(), image.getContainerFormat(), image.getMinDiskSpace(), image.getMinRam(), image.isPublic());
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

    private NFVImage updateImage(String extId, String name, String diskFormat, String containerFormat, long minDisk, long minRam, boolean isPublic) {
        ImageApi imageApi = this.glanceApi.getImageApi(this.defaultZone);
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
        return image;
    }

    @Override
    public NFVImage copyImage(VimInstance vimInstance, NFVImage image, byte[] imageFile) {
        init(vimInstance);
        NFVImage copiedImage = copyImage(image.getName(), new ByteArrayInputStream(imageFile), image.getDiskFormat(), image.getContainerFormat(), image.getMinDiskSpace(), image.getMinRam(), image.isPublic());
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

    private NFVImage copyImage(String name, InputStream inputStream, String diskFormat, String containerFormat, long minDisk, long minRam, boolean isPublic) {
        ImageApi imageApi = this.glanceApi.getImageApi(this.defaultZone);
        NFVImage image = addImage(name, inputStream, diskFormat, containerFormat, minDisk, minRam, isPublic);
        return image;
    }

    private NFVImage getImageById(String extId) {
        org.jclouds.openstack.nova.v2_0.features.ImageApi imageApi = this.novaApi.getImageApi(this.defaultZone);
        try {
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
            return image;
        } catch (NullPointerException e) {
            log.warn(e.getMessage(), new NullPointerException("Image with extId: " + extId + " not found."));
            NFVImage image = new NFVImage();
            image.setExtId(extId);
            return image;
        }
    }

    @Override
    public DeploymentFlavour addFlavor(VimInstance vimInstance, DeploymentFlavour flavor) {
        init(vimInstance);
        DeploymentFlavour addedFlavor = addFlavor(flavor.getFlavour_key(), flavor.getVcpus(), flavor.getRam(), flavor.getDisk());
        flavor.setExtId(addedFlavor.getExtId());
        flavor.setFlavour_key(addedFlavor.getFlavour_key());
        flavor.setVcpus(addedFlavor.getVcpus());
        flavor.setRam(addedFlavor.getRam());
        flavor.setDisk(addedFlavor.getVcpus());
        return flavor;
    }

    private DeploymentFlavour addFlavor(String name, int vcpus, int ram, int disk) {
        FlavorApi flavorApi = this.novaApi.getFlavorApi(this.defaultZone);
        UUID id = java.util.UUID.randomUUID();
        org.jclouds.openstack.nova.v2_0.domain.Flavor newFlavor = org.jclouds.openstack.nova.v2_0.domain.Flavor.builder().id(id.toString()).name(name).disk(disk).ram(ram).vcpus(vcpus).build();
        org.jclouds.openstack.nova.v2_0.domain.Flavor jcloudsFlavor = flavorApi.create(newFlavor);
        DeploymentFlavour flavor = new DeploymentFlavour();
        flavor.setExtId(jcloudsFlavor.getId());
        flavor.setFlavour_key(jcloudsFlavor.getName());
        flavor.setVcpus(jcloudsFlavor.getVcpus());
        flavor.setRam(jcloudsFlavor.getRam());
        flavor.setDisk(jcloudsFlavor.getVcpus());
        return flavor;
    }

    @Override
    public DeploymentFlavour updateFlavor(VimInstance vimInstance, DeploymentFlavour flavor) throws VimDriverException {
        init(vimInstance);
        try {
            DeploymentFlavour updatedFlavor = updateFlavor(vimInstance, flavor.getExtId(), flavor.getFlavour_key(), flavor.getVcpus(), flavor.getRam(), flavor.getDisk());
            flavor.setFlavour_key(updatedFlavor.getFlavour_key());
            flavor.setExtId(updatedFlavor.getExtId());
            flavor.setRam(updatedFlavor.getRam());
            flavor.setDisk(updatedFlavor.getDisk());
            flavor.setVcpus(updatedFlavor.getVcpus());
            return flavor;
        } catch (VimDriverException e) {
            throw new VimDriverException("Image with id: " + flavor.getId() + " not updated successfully");
        }
    }

    private DeploymentFlavour updateFlavor(VimInstance vimInstance, String extId, String name, int vcpus, int ram, int disk) throws VimDriverException {
        FlavorApi flavorApi = this.novaApi.getFlavorApi(this.defaultZone);
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
            return updatedFlavor;
        } else {
            throw new VimDriverException("Image with extId: " + extId + " not updated successfully");
        }
    }

    @Override
    public boolean deleteFlavor(VimInstance vimInstance, String extId) {
        init(vimInstance);
        FlavorApi flavorApi = this.novaApi.getFlavorApi(this.defaultZone);
        flavorApi.delete(extId);
        boolean isDeleted;
        try {
            getFlavorById(extId);
            isDeleted = true;
        } catch (NullPointerException e) {
            isDeleted = false;
        }
        return isDeleted;
    }


    private DeploymentFlavour getFlavorById(String extId) {
        FlavorApi flavorApi = this.novaApi.getFlavorApi(this.defaultZone);
        try {
            org.jclouds.openstack.nova.v2_0.domain.Flavor jcloudsFlavor = flavorApi.get(extId);
            DeploymentFlavour flavor = new DeploymentFlavour();
            flavor.setFlavour_key(jcloudsFlavor.getName());
            flavor.setExtId(jcloudsFlavor.getId());
            flavor.setRam(jcloudsFlavor.getRam());
            flavor.setDisk(jcloudsFlavor.getDisk());
            flavor.setVcpus(jcloudsFlavor.getVcpus());
            return flavor;
        } catch (NullPointerException e) {
            throw new NullPointerException("Flavor with extId: " + extId + " not found.");
        }
    }

    @Override
    public List<DeploymentFlavour> listFlavors(VimInstance vimInstance) {
        init(vimInstance);
        List<DeploymentFlavour> flavors = new ArrayList<DeploymentFlavour>();
        FlavorApi flavorApi = this.novaApi.getFlavorApi(this.defaultZone);
        for (org.jclouds.openstack.nova.v2_0.domain.Flavor jcloudsFlavor : flavorApi.listInDetail().concat()) {
            DeploymentFlavour flavor = new DeploymentFlavour();
            flavor.setExtId(jcloudsFlavor.getId());
            flavor.setFlavour_key(jcloudsFlavor.getName());
            flavor.setRam(jcloudsFlavor.getRam());
            flavor.setDisk(jcloudsFlavor.getDisk());
            flavor.setVcpus(jcloudsFlavor.getVcpus());
            flavors.add(flavor);
        }
        return flavors;
    }

    @Override
    public Network createNetwork(VimInstance vimInstance, Network network) {
        init(vimInstance);
        Network createdNetwork = createNetwork(network.getName(), network.isExternal(), network.isShared());
        network.setName(createdNetwork.getName());
        network.setExtId(createdNetwork.getExtId());
        network.setExternal(createdNetwork.isExternal());
        network.setShared(createdNetwork.isShared());
        return network;
    }

    private Network createNetwork(String name, boolean external, boolean shared) {
        NetworkApi networkApi = neutronApi.getNetworkApi(defaultZone);
        //CreateNetwork createNetwork = CreateNetwork.createBuilder(name).networkType(NetworkType.fromValue(networkType)).external(external).shared(shared).segmentationId(segmentationId).physicalNetworkName(physicalNetworkName).build();
        CreateNetwork createNetwork = CreateNetwork.createBuilder(name).external(external).shared(shared).build();
        org.jclouds.openstack.neutron.v2.domain.Network jcloudsNetwork = networkApi.create(createNetwork);
        Network network = new Network();
        network.setName(jcloudsNetwork.getName());
        network.setExtId(jcloudsNetwork.getId());
        network.setExternal(jcloudsNetwork.getExternal());
        network.setShared(jcloudsNetwork.getShared());
        return network;
    }

    @Override
    public Network updateNetwork(VimInstance vimInstance, Network network) {
        init(vimInstance);
        Network updatedNetwork = updateNetwork(network.getExtId(), network.getName(), network.isExternal(), network.isShared());
        network.setName(updatedNetwork.getName());
        network.setExtId(updatedNetwork.getExtId());
        network.setExternal(updatedNetwork.isExternal());
        network.setShared(updatedNetwork.isShared());
        return network;
    }

    private Network updateNetwork(String extId, String name, boolean external, boolean shared) {
        NetworkApi networkApi = neutronApi.getNetworkApi(defaultZone);
        //Plugin does not support updating provider attributes. -> NetworkType, SegmentationId, physicalNetworkName
        UpdateNetwork updateNetwork = UpdateNetwork.updateBuilder().name(name).build();
        org.jclouds.openstack.neutron.v2.domain.Network jcloudsNetwork = networkApi.update(extId, updateNetwork);
        Network network = new Network();
        network.setName(jcloudsNetwork.getName());
        network.setExtId(jcloudsNetwork.getId());
        network.setExternal(jcloudsNetwork.getExternal());
        network.setShared(jcloudsNetwork.getShared());
        return network;
    }

    @Override
    public boolean deleteNetwork(VimInstance vimInstance, String extId) {
        init(vimInstance);
        NetworkApi networkApi = neutronApi.getNetworkApi(defaultZone);
        boolean isDeleted = networkApi.delete(extId);
        return isDeleted;
    }


    @Override
    public Network getNetworkById(VimInstance vimInstance, String extId) {
        init(vimInstance);
        NetworkApi networkApi = neutronApi.getNetworkApi(defaultZone);
        try {
            org.jclouds.openstack.neutron.v2.domain.Network jcloudsNetwork = networkApi.get(extId);
            Network network = new Network();
            network.setName(jcloudsNetwork.getName());
            network.setExtId(jcloudsNetwork.getId());
            network.setExternal(jcloudsNetwork.getExternal());
            network.setShared(jcloudsNetwork.getShared());
            return network;
        } catch (Exception e) {
            throw new NullPointerException("Network not found");
        }
    }

    @Override
    public List<String> getSubnetsExtIds(VimInstance vimInstance, String extId) {
        init(vimInstance);
        NetworkApi networkApi = neutronApi.getNetworkApi(defaultZone);
        List<String> subnets = new ArrayList<String>();
        try {
            org.jclouds.openstack.neutron.v2.domain.Network jcloudsNetwork = networkApi.get(extId);
            subnets = jcloudsNetwork.getSubnets().asList();
            return subnets;
        } catch (Exception e) {
            throw new NullPointerException("Network not found");
        }
    }

    @Override
    public List<Network> listNetworks(VimInstance vimInstance) {
        init(vimInstance);
        List<Network> networks = new ArrayList<Network>();
        for (org.jclouds.openstack.neutron.v2.domain.Network jcloudsNetwork : this.neutronApi.getNetworkApi(defaultZone).list().concat()) {
            if (jcloudsNetwork.getTenantId().equals(this.tenantId)) {
                log.trace("OpenstackNetwork: " + jcloudsNetwork.toString());
                Network network = new Network();
                network.setName(jcloudsNetwork.getName());
                network.setExtId(jcloudsNetwork.getId());
                network.setExternal(jcloudsNetwork.getExternal());
                network.setShared(jcloudsNetwork.getShared());
                networks.add(network);
            }
        }
        return networks;
    }

    @Override
    public Subnet createSubnet(VimInstance vimInstance, Network network, Subnet subnet) {
        init(vimInstance);
        Subnet createdSubnet = createSubnet(network, subnet.getName(), subnet.getCidr());
        subnet.setExtId(createdSubnet.getExtId());
        subnet.setName(createdSubnet.getName());
        subnet.setCidr(createdSubnet.getCidr());
        return subnet;
    }

    private Subnet createSubnet(Network network, String name, String cidr) {
        SubnetApi subnetApi = neutronApi.getSubnetApi(defaultZone);
        CreateSubnet createSubnet = CreateSubnet.createBuilder(network.getExtId(), cidr).name(name).ipVersion(4).build();
        org.jclouds.openstack.neutron.v2.domain.Subnet jcloudsSubnet = subnetApi.create(createSubnet);
        Subnet subnet = new Subnet();
        subnet.setExtId(jcloudsSubnet.getId());
        subnet.setName(jcloudsSubnet.getName());
        subnet.setCidr(jcloudsSubnet.getCidr());
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
        return subnet;
    }

    @Override
    public Subnet updateSubnet(VimInstance vimInstance, Network network, Subnet subnet) {
        init(vimInstance);
        Subnet updatedSubnet = updateSubnet(network, subnet.getExtId(), subnet.getName());
        subnet.setExtId(updatedSubnet.getExtId());
        subnet.setName(updatedSubnet.getName());
        subnet.setCidr(updatedSubnet.getCidr());
        return subnet;
    }

    private Subnet updateSubnet(Network network, String subnetExtId, String name) {
        SubnetApi subnetApi = neutronApi.getSubnetApi(defaultZone);
        //Cannot update read-only attribute cidr
        //Cannot update read-only attribute network_id
        //Cannot update read-only attribute ip_version
        UpdateSubnet updateSubnet = UpdateSubnet.updateBuilder().name(name).build();
        org.jclouds.openstack.neutron.v2.domain.Subnet jcloudsSubnet = subnetApi.update(subnetExtId, updateSubnet);
        Subnet subnet = new Subnet();
        subnet.setExtId(jcloudsSubnet.getId());
        subnet.setName(jcloudsSubnet.getName());
        subnet.setCidr(jcloudsSubnet.getCidr());
        return subnet;
    }

    private String getRouter(VimInstance vimInstance) {
        log.debug("Getting existing Router to associate with new Subnet");
        init(vimInstance);
        RouterApi routerApi = neutronApi.getRouterApi(defaultZone).get();
        PagedIterable routerList = routerApi.list();
        if (routerList.iterator().hasNext()) {
            for (Router router : (FluentIterable<Router>) routerList.concat())
                if (router.getTenantId().equals(this.tenantId)) {
                    ExternalGatewayInfo externalGatewayInfo = router.getExternalGatewayInfo();
                    if (externalGatewayInfo != null) {
                        String networkId = externalGatewayInfo.getNetworkId();
                        if (getNetworkById(vimInstance, networkId).isExternal()) {
                            return router.getId();
                        }
                    }
                }
        }
        return null;
    }

    private String createRouter(VimInstance vimInstance) {
        log.debug("Creating a new Router");
        init(vimInstance);
        RouterApi routerApi = neutronApi.getRouterApi(defaultZone).get();
        //Find external network
        String externalNetId = null;
        for (Network network : listNetworks(vimInstance)) {
            if (network.isExternal()) {
                externalNetId = network.getExtId();
            }
        }
        if (externalNetId == null) {
            log.error("Not found external network for creating the router");
            return null;
        }
        ExternalGatewayInfo externalGatewayInfo = ExternalGatewayInfo.builder().networkId(externalNetId).build();
        Router.CreateRouter options = Router.CreateRouter.createBuilder().name(vimInstance.getTenant() + "_" + (int)(Math.random() * 1000) + "_router").adminStateUp(true).externalGatewayInfo(externalGatewayInfo).build();
        Router router = routerApi.create(options);
        return router.getId();
    }

    private String attachInterface(VimInstance vimInstance, String routerId, String subnetId) {
        init(vimInstance);
        log.debug("Associating Subnet to Router");
        RouterApi routerApi = neutronApi.getRouterApi(defaultZone).get();
        RouterInterface routerInterface = routerApi.addInterfaceForSubnet(routerId, subnetId);
        return routerInterface.getSubnetId();
    }

    private String attachPort(VimInstance vimInstance, String routerId, String portId) {
        init(vimInstance);
        log.debug("Associating Subnet to Router");
        RouterApi routerApi = neutronApi.getRouterApi(defaultZone).get();
        RouterInterface routerInterface = routerApi.addInterfaceForPort(routerId, portId);
        return routerInterface.getSubnetId();
    }

    private String createPort(VimInstance vimInstance, Network network, Subnet subnet) {
        log.debug("Associating Subnet to Router");
        PortApi portApi = neutronApi.getPortApi(defaultZone);
        Port.CreatePort createPort = Port.createBuilder(network.getExtId()).name("Port_" + network.getName() + "_" + (int) (Math.random() * 1000)).fixedIps(ImmutableSet.<IP>of(IP.builder().subnetId(subnet.getExtId()).build())).build();
        Port port = portApi.create(createPort);
        return port.getId();
    }

    @Override
    public boolean deleteSubnet(VimInstance vimInstance, String extId) {
        init(vimInstance);
        SubnetApi subnetApi = neutronApi.getSubnetApi(defaultZone);
        boolean isDeleted = subnetApi.delete(extId);
        return isDeleted;
    }

    private List<String> listFreeFloatingIps() {
        List<String> floatingIPs = new LinkedList<String>();
        org.jclouds.openstack.nova.v2_0.extensions.FloatingIPApi floatingIPApi = novaApi.getFloatingIPApi(defaultZone).get();
        Iterator<FloatingIP> floatingIpIterator = floatingIPApi.list().iterator();
        while (floatingIpIterator.hasNext()) {
            FloatingIP floatingIP = floatingIpIterator.next();
            if (floatingIP.getInstanceId() == null) {
                floatingIPs.add(floatingIP.getIp());
            }
        }
        return floatingIPs;
    }

    private void associateFloatingIp(VimInstance vimInstance, Server server, String floatingIp) {
        org.jclouds.openstack.nova.v2_0.extensions.FloatingIPApi floatingIPApi = novaApi.getFloatingIPApi(defaultZone).get();
        floatingIPApi.addToServer(floatingIp, server.getExtId());
        log.info("Associated floatingIp " + floatingIp + " to server: " + server.getName());
        server.setFloatingIp(floatingIp);
    }

    @Override
    public Quota getQuota(VimInstance vimInstance) {
        init(vimInstance);
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
        HttpURLConnection connection = null;
        try {
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
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return quota;
    }

    @Override
    public String getType(VimInstance vimInstance) {
        return "openstack";
    }
}
