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

package org.openbaton.vim_drivers.test;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.*;
import com.google.inject.Injector;
import com.google.inject.Key;
import org.jclouds.ContextBuilder;
import org.jclouds.collect.IterableWithMarkers;
import org.jclouds.collect.PagedIterable;
import org.jclouds.collect.PagedIterables;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.Utils;
import org.jclouds.domain.Credentials;
import org.jclouds.io.Payload;
import org.jclouds.openstack.glance.v1_0.GlanceApi;
import org.jclouds.openstack.glance.v1_0.domain.ContainerFormat;
import org.jclouds.openstack.glance.v1_0.domain.DiskFormat;
import org.jclouds.openstack.glance.v1_0.domain.ImageDetails;
import org.jclouds.openstack.glance.v1_0.features.ImageApi;
import org.jclouds.openstack.glance.v1_0.options.CreateImageOptions;
import org.jclouds.openstack.glance.v1_0.options.UpdateImageOptions;
import org.jclouds.openstack.keystone.v2_0.domain.*;
import org.jclouds.openstack.neutron.v2.NeutronApi;
import org.jclouds.openstack.neutron.v2.domain.*;
import org.jclouds.openstack.neutron.v2.extensions.RouterApi;
import org.jclouds.openstack.neutron.v2.features.NetworkApi;
import org.jclouds.openstack.neutron.v2.features.PortApi;
import org.jclouds.openstack.neutron.v2.features.SubnetApi;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.domain.*;
import org.jclouds.openstack.nova.v2_0.domain.FloatingIP;
import org.jclouds.openstack.nova.v2_0.extensions.FloatingIPApi;
import org.jclouds.openstack.nova.v2_0.extensions.QuotaApi;
import org.jclouds.openstack.nova.v2_0.features.FlavorApi;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.jclouds.openstack.v2_0.domain.Link;
import org.jclouds.openstack.v2_0.domain.Resource;
import org.jclouds.rest.AuthorizationException;
import org.junit.*;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.openbaton.catalogue.mano.common.DeploymentFlavour;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.*;
import org.openbaton.catalogue.nfvo.Network;
import org.openbaton.catalogue.nfvo.Quota;
import org.openbaton.catalogue.nfvo.Server;
import org.openbaton.catalogue.nfvo.Subnet;
import org.openbaton.clients.interfaces.client.openstack.OpenstackClient;
import org.openbaton.vim.drivers.exceptions.VimDriverException;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import sun.net.www.protocol.http.*;
import sun.net.www.protocol.http.HttpURLConnection;

import java.io.*;
import java.net.*;
import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;


/**
 * Created by mpa on 07.05.15.
 */
@RunWith(PowerMockRunner.class)
//@PrepareForTest({ContextBuilder.class, Thread.class, URL.class, OpenstackClient.class})
@PrepareForTest({ContextBuilder.class, Thread.class, OpenstackClient.class})
public class OpenstackTest {

    OpenstackClient openstackClient;

    VimInstance vimInstance;

    VirtualDeploymentUnit vdu;

    VirtualNetworkFunctionRecord vnfr;

    Network definedNetwork;

    Subnet definedSubnet;

    Server definedServer;

    NFVImage definedImage;

    DeploymentFlavour definedFlavor;

    Quota definedQuota;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private class MyExtendedStatus extends ServerExtendedStatus {
        protected MyExtendedStatus(String taskState, String vmState, int powerState) {
            super(taskState, vmState, powerState);
        }
    }

    private class MyResource extends Resource {
        protected MyResource(String id, String name, Set<Link> links) {
            super(id, name, links);
        }
    }

    private class MyServer extends org.jclouds.openstack.nova.v2_0.domain.Server {
        protected MyServer(String id, String name, Set<Link> links, String uuid, String tenantId, String userId, Date updated, Date created, String hostId, String accessIPv4, String accessIPv6, Status status, Resource image, Resource flavor, String keyName, String configDrive, Multimap<String, Address> addresses, Map<String, String> metadata, ServerExtendedStatus extendedStatus, ServerExtendedAttributes extendedAttributes, String diskConfig, String availabilityZone) {
            super(id, name, links, uuid, tenantId, userId, updated, created, hostId, accessIPv4, accessIPv6, status, image, flavor, keyName, configDrive, addresses, metadata, extendedStatus, extendedAttributes, diskConfig, availabilityZone);
        }
    }

    private class MyFlavor extends org.jclouds.openstack.nova.v2_0.domain.Flavor {
        protected MyFlavor(String id, String name, Set<Link> links, int ram, int disk, int vcpus, String swap, Double rxtxFactor, Integer ephemeral) {
            super(id, name, links, ram, disk, vcpus, swap, rxtxFactor, ephemeral);
        }
    }

    private class MyNovaImage extends org.jclouds.openstack.nova.v2_0.domain.Image {
        protected MyNovaImage(String id, String name, Set<Link> links, Date updated, Date created, String tenantId, String userId, Status status, int progress, int minDisk, int minRam, List<BlockDeviceMapping> blockDeviceMapping, Resource server, Map<String, String> metadata) {
            super(id, name, links, updated, created, tenantId, userId, status, progress, minDisk, minRam, blockDeviceMapping, server, metadata);
        }
    }

    private class MyGlanceImage extends org.jclouds.openstack.glance.v1_0.domain.Image {
        protected MyGlanceImage(String id, String name, Set<Link> links, ContainerFormat containerFormat, DiskFormat diskFormat, Long size, String checksum) {
            super(id, name, links, containerFormat, diskFormat, size, checksum);
        }
    }

    private class MyImageDetails extends ImageDetails {
        protected MyImageDetails(String id, String name, Set<Link> links, ContainerFormat containerFormat, DiskFormat diskFormat, Long size, String checksum, long minDisk, long minRam, String location, String owner, Date updatedAt, Date createdAt, Date deletedAt, Status status, boolean isPublic, Map<String, String> properties) {
            super(id, name, links, containerFormat, diskFormat, size, checksum, minDisk, minRam, location, owner, updatedAt, createdAt, deletedAt, status, isPublic, properties);
        }
    }

    private class MyFloatingIP extends org.jclouds.openstack.nova.v2_0.domain.FloatingIP {
        protected MyFloatingIP(String id, String ip, String fixedIp, String instanceId, String pool) {
            super(id, ip, fixedIp, instanceId, pool);
        }
    }

    private class MyQuota extends org.jclouds.openstack.nova.v2_0.domain.Quota {
        protected MyQuota(String id, int metadataItems, int injectedFileContentBytes, int volumes, int gigabytes, int ram, int floatingIps, int instances, int injectedFiles, int cores, int securityGroups, int securityGroupRules, int keyPairs) {
            super(id, metadataItems, injectedFileContentBytes, volumes, gigabytes, ram, floatingIps, instances, injectedFiles, cores, securityGroups, securityGroupRules, keyPairs);
        }
    }

    private class MyAddress extends Address {
        protected MyAddress(String addr, int version) {
            super(addr, version);
        }
    }

    private class MyPort extends Port {
        protected MyPort(String id, NetworkStatus status, VIFType vifType, ImmutableMap<String, Object> vifDetails, String qosQueueId, String name, String networkId, Boolean adminStateUp, String macAddress, ImmutableSet<IP> fixedIps, String deviceId, String deviceOwner, String tenantId, ImmutableSet<String> securityGroups, ImmutableSet<AddressPair> allowedAddressPairs, ImmutableSet<ExtraDhcpOption> extraDhcpOptions, VNICType vnicType, String hostId, ImmutableMap<String, Object> profile, Boolean portSecurity, String profileId, Boolean macLearning, Integer qosRxtxFactor) {
            super(id, status, vifType, vifDetails, qosQueueId, name, networkId, adminStateUp, macAddress, fixedIps, deviceId, deviceOwner, tenantId, securityGroups, allowedAddressPairs, extraDhcpOptions, vnicType, hostId, profile, portSecurity, profileId, macLearning, qosRxtxFactor);
        }
    }

    private class MyExternalGatewayInfo extends org.jclouds.openstack.neutron.v2.domain.ExternalGatewayInfo {
        protected MyExternalGatewayInfo(String networkId, Boolean enableSnat) {
            super(networkId, enableSnat);
        }
    }

    private MyServer expServer;
    private MyServer faultyServer;
    private MyServer errorServer;
    private MyResource expServerResource;
    private MyGlanceImage expImageResource;
    private MyResource expFlavorResource;
    private MyFlavor expFlavor;
    private MyFlavor faultyFlavor;
    private MyFlavor errorFlavor;
    private MyNovaImage expImage;
    private MyNovaImage faultyImage;
    private MyNovaImage errorImage;
    private MyFloatingIP expFreeFloatingIP;
    private MyFloatingIP expUsedFloatingIP;
    private MyFloatingIP expFreeRealFloatingIP;
    private MyQuota expQuota;
    private MyPort expPort;

    @Before
    public void init() throws Exception {
        openstackClient = spy(new OpenstackClient());
        //doNothing().when(openstackClient).init();

        //pre-defined entities
        vimInstance = createVimInstance();
        definedImage = createImage();
        definedNetwork = createNetwork();
        definedSubnet = createSubnet();
        definedFlavor = createFlavor();
        definedServer = createServer();
        definedQuota = createQuota();

        //Expected Entities

        //Flavor
        expFlavor = new MyFlavor(definedFlavor.getExtId(), definedFlavor.getFlavour_key(), new HashSet<Link>(), 512, 1, 2, "", 1.1, 1);
        faultyFlavor = new MyFlavor("not_existing_flavor_ext_id", definedFlavor.getFlavour_key(), new HashSet<Link>(), 512, 1, 2, "", 1.1, 1);
        errorFlavor = new MyFlavor("error_flavor_ext_id", "error_flavor_name", new HashSet<Link>(), 512, 1, 2, "", 1.1, 1);

        //Image
        expImage = new MyNovaImage(definedImage.getExtId(), definedImage.getName(), new HashSet<Link>(), new Date(), new Date(), "", "", Image.Status.ACTIVE, 1, (int) definedImage.getMinDiskSpace(), (int) definedImage.getMinRam(), new ArrayList<BlockDeviceMapping>(), expImageResource, new HashMap<String, String>());
        faultyImage = new MyNovaImage("not_existing_image_ext_id", "not_existing_image_name", new HashSet<Link>(), new Date(), new Date(), "", "", Image.Status.ACTIVE, 1, (int) definedImage.getMinDiskSpace(), (int) definedImage.getMinRam(), new ArrayList<BlockDeviceMapping>(), expImageResource, new HashMap<String, String>());
        errorImage = new MyNovaImage("error_image_ext_id", "error_image_name", new HashSet<Link>(), new Date(), new Date(), "", "", Image.Status.ACTIVE, 1, (int) definedImage.getMinDiskSpace(), (int) definedImage.getMinRam(), new ArrayList<BlockDeviceMapping>(), expImageResource, new HashMap<String, String>());
        ImageDetails imageDetails = new MyImageDetails(definedImage.getExtId(), definedImage.getName(), new HashSet<Link>(), ContainerFormat.fromValue(definedImage.getContainerFormat()), DiskFormat.fromValue(definedImage.getDiskFormat()), new Long(1), "", definedImage.getMinDiskSpace(), definedImage.getMinRam(), "", "", definedImage.getUpdated(), definedImage.getCreated(), new Date(), org.jclouds.openstack.glance.v1_0.domain.Image.Status.ACTIVE, definedImage.isPublic(), new HashMap<String, String>());

        //Server
        ServerExtendedStatus extStatus = new MyExtendedStatus("mocked_id", "mocked_name", 0);
        Map<String, Collection<Address>> addressMap = new HashMap<String, Collection<Address>>();
        Collection<Address> addresses = new HashSet<Address>();
        addresses.add(new MyAddress("0.0.0.0", 4));
        addressMap.put("mocked_private_network_name", addresses);
        Multimap<String, Address> multimap = ArrayListMultimap.create();
        for (String key : addressMap.keySet()) {
            multimap.putAll(key, addressMap.get(key));
        }
        expServer = new MyServer(definedServer.getExtId(), definedServer.getName(), new HashSet<Link>(), definedServer.getExtId(), "mocked_tenant_id", "", definedServer.getUpdated(), definedServer.getCreated(), "", "mocked_ip4", "mocked_ip6", org.jclouds.openstack.nova.v2_0.domain.Server.Status.fromValue(definedServer.getStatus()), expImage, expFlavor, "", "", multimap , new HashMap<String, String>(), extStatus, mock(ServerExtendedAttributes.class), "", "");
        faultyServer = new MyServer("faulty_server_mocked_ext_id", "faulty_server", new HashSet<Link>(), definedServer.getExtId(), "mocked_tenant_id", "", definedServer.getUpdated(), definedServer.getCreated(), "", "mocked_ip4", "mocked_ip6", org.jclouds.openstack.nova.v2_0.domain.Server.Status.ERROR, faultyImage, faultyFlavor, "", "", mock(Multimap.class), new HashMap<String, String>(), extStatus, mock(ServerExtendedAttributes.class), "", "");
        //faultyServer = new MyServer("faulty_server_mocked_ext_id", "faulty_server", new HashSet<Link>(), definedServer.getExtId(), "", "", definedServer.getUpdated(), definedServer.getCreated(), "", "mocked_ip4", "mocked_ip6", org.jclouds.openstack.nova.v2_0.domain.Server.Status.ERROR, expImage, expFlavor, "", "", mock(Multimap.class), new HashMap<String, String>(), extStatus, mock(ServerExtendedAttributes.class), "", "");
        errorServer = new MyServer("error_server_mocked_ext_id", "error_server", new HashSet<Link>(), definedServer.getExtId(), "mocked_tenant_id", "", definedServer.getUpdated(), definedServer.getCreated(), "", "mocked_ip4", "mocked_ip6", org.jclouds.openstack.nova.v2_0.domain.Server.Status.ERROR, errorImage, errorFlavor, "", "", mock(Multimap.class), new HashMap<String, String>(), extStatus, mock(ServerExtendedAttributes.class), "", "");
        ServerCreated serverCreated = mock(ServerCreated.class);
        ServerCreated faultyServerCreated = mock(ServerCreated.class);
        ServerCreated errorServerCreated = mock(ServerCreated.class);

        List<org.jclouds.openstack.nova.v2_0.domain.Server> serServerArray = new ArrayList<org.jclouds.openstack.nova.v2_0.domain.Server>();
        serServerArray.add(expServer);
        //serServerArray.add(faultyServer);
        //serServerArray.add(errorServer);
        FluentIterable<org.jclouds.openstack.nova.v2_0.domain.Server> serServerFI = FluentIterable.from(serServerArray);

        //Port
        expPort = new MyPort("mocked_port_ext_id", NetworkStatus.ACTIVE, VIFType._802_QBG, ImmutableMap.copyOf(new HashMap<String, Object>()), "mocked_qos_queue_id", "mocked_name", "mocked_network_ext_id", true, "mocked_mac_address", ImmutableSet.copyOf(new HashSet<IP>()), "mocked_device_id", "mocked_device_owner", "mocked_tenant_id", ImmutableSet.copyOf(new HashSet<String>()), ImmutableSet.copyOf(new HashSet<AddressPair>()), ImmutableSet.copyOf(new HashSet<ExtraDhcpOption>()), VNICType.NORMAL, "mocked_host_id", ImmutableMap.copyOf(new HashMap<String, Object>()), false, "mocked_profile_id", false, 0);

        //Resources
        expFlavorResource = new MyResource(definedFlavor.getExtId(), definedFlavor.getFlavour_key(), new HashSet<Link>());
        List<Resource> resFlavorArray = new ArrayList<Resource>();
        resFlavorArray.add(expFlavorResource);
        FluentIterable<Resource> resFlavorFI = FluentIterable.from(resFlavorArray);

        expImageResource = new MyGlanceImage(definedImage.getExtId(), definedImage.getName(), new HashSet<Link>(), ContainerFormat.valueOf(definedImage.getContainerFormat()), DiskFormat.fromValue(definedImage.getDiskFormat()), (long) 1000, "");
        List<org.jclouds.openstack.glance.v1_0.domain.Image> resImageArray = new ArrayList<org.jclouds.openstack.glance.v1_0.domain.Image>();
        resImageArray.add(expImageResource);
        FluentIterable<org.jclouds.openstack.glance.v1_0.domain.Image> resImageFI = FluentIterable.from(resImageArray);

        expServerResource = new MyResource(definedServer.getExtId(), definedServer.getName(), new HashSet<Link>());
        List<Resource> resServerArray = new ArrayList<Resource>();
        resServerArray.add(expServerResource);
        FluentIterable<Resource> resServerFI = FluentIterable.from(resServerArray);

        //Flavor
        List<Flavor> flaFlavorArray = new ArrayList<Flavor>();
        flaFlavorArray.add(expFlavor);
        FluentIterable<Flavor> flaFlavorFI = FluentIterable.from(flaFlavorArray);

        //FloatingIP
        expFreeFloatingIP = new MyFloatingIP("mocked_ext_id", "mocked_free_ip", "mocked_fixed_ip", null, "mocked_pool");
        expUsedFloatingIP = new MyFloatingIP("mocked_ext_id", "mocked_used_ip", "mocked_fixed_ip", "mocked_instance_id", "mocked_pool");
        expFreeRealFloatingIP = new MyFloatingIP("mocked_ext_id", "0.0.0.0", "0.0.0.0", null, "mocked_pool");
        Set<FloatingIP> fipSet = new HashSet<FloatingIP>();
        fipSet.add(expFreeRealFloatingIP);
        fipSet.add(expFreeFloatingIP);
        fipSet.add(expUsedFloatingIP);
        FluentIterable<FloatingIP> fipFI = FluentIterable.from(fipSet);

        //Quota
        expQuota = new MyQuota(definedQuota.getTenant(), 10, 10, 10, 10, definedQuota.getRam(), definedQuota.getFloatingIps(), definedQuota.getInstances(), 10, definedQuota.getCores(), 10, 10, definedQuota.getKeyPairs());

        //jclouds APIs
        //Neutron API
        NeutronApi neutronApi = mock(NeutronApi.class);
        //Nova API
        NovaApi novaApi = mock(NovaApi.class);
        //Glance API
        GlanceApi glanceApi = mock(GlanceApi.class);

        //jclouds ContextBuilder
        PowerMockito.spy(ContextBuilder.class);
        ContextBuilder contextBuilder = mock(ContextBuilder.class);

        PowerMockito.doReturn(contextBuilder).when(ContextBuilder.class, "newBuilder", Mockito.anyString());

        when(contextBuilder.endpoint(anyString())).thenReturn(contextBuilder);
        when(contextBuilder.credentials(anyString(), anyString())).thenReturn(contextBuilder);
        when(contextBuilder.modules(any(Iterable.class))).thenReturn(contextBuilder);
        when(contextBuilder.overrides(any(Properties.class))).thenReturn(contextBuilder);
        when(contextBuilder.buildApi(NovaApi.class)).thenReturn(novaApi);
        when(contextBuilder.buildApi(GlanceApi.class)).thenReturn(glanceApi);
        when(contextBuilder.buildApi(NeutronApi.class)).thenReturn(neutronApi);

        //Zones of NovaAPI
        Set<String> zones = new HashSet<>();
        zones.add("mocked_zone");
        when(novaApi.getConfiguredRegions()).thenReturn(zones);

        //ServerApi
        ServerApi serverApi = mock(ServerApi.class);
        when(novaApi.getServerApi(anyString())).thenReturn(serverApi);
        when(serverApi.get(definedServer.getExtId())).thenReturn(expServer);
        when(serverApi.get("not_existing_server_ext_id")).thenThrow(new NullPointerException());
        when(serverApi.get("faulty_server_mocked_ext_id")).thenReturn(faultyServer);
        when(serverApi.get("error_server_mocked_ext_id")).thenReturn(errorServer);
        when(serverApi.list()).thenReturn(mock(PagedIterable.class));
        when(serverApi.list().concat()).thenReturn(resServerFI);
        when(serverApi.listInDetail()).thenReturn(mock(PagedIterable.class));
        when(serverApi.listInDetail().concat()).thenReturn(serServerFI);
        when(serverCreated.getId()).thenReturn(definedServer.getExtId());
        when(faultyServerCreated.getId()).thenReturn("faulty_server_mocked_ext_id");
        when(errorServerCreated.getId()).thenReturn("error_server_mocked_ext_id");
        when(serverApi.create(eq(definedServer.getName()), anyString(), anyString(), any(CreateServerOptions.class))).thenReturn(serverCreated);
        when(serverApi.create(eq("faulty_server"), anyString(), anyString(), any(CreateServerOptions.class))).thenThrow(new AuthorizationException());
        when(serverApi.create(eq("faulty_server"), eq(faultyImage.getId()), eq(faultyFlavor.getId()), any(CreateServerOptions.class))).thenReturn(faultyServerCreated);
        when(serverApi.create(eq("error_server"), anyString(), anyString(), any(CreateServerOptions.class))).thenReturn(errorServerCreated);

        when(serverApi.delete(definedServer.getExtId())).thenReturn(true);
        when(serverApi.delete("not_existing_ext_id")).thenThrow(NullPointerException.class);
        doNothing().when(serverApi).reboot(definedServer.getExtId(), RebootType.SOFT);
        doThrow(new NullPointerException("triggered NullPointerException")).when(serverApi).reboot("not_existing_ext_id", RebootType.SOFT);

        //ImageApi
        org.jclouds.openstack.nova.v2_0.features.ImageApi novaImageApi = mock(org.jclouds.openstack.nova.v2_0.features.ImageApi.class);
        when(novaImageApi.get(definedImage.getExtId())).thenReturn(expImage);
        when(novaImageApi.get("error_image_ext_id")).thenThrow(new AuthorizationException());
        when(novaImageApi.get("not_existing_image_ext_id")).thenThrow(new NullPointerException());
        ImageApi imageApi = mock(ImageApi.class);
        when(novaApi.getImageApi(anyString())).thenReturn(novaImageApi);
        List<ImageDetails> imageDetailsArray = new ArrayList<ImageDetails>();
        imageDetailsArray.add(imageDetails);
        FluentIterable<ImageDetails> imaImageFI = FluentIterable.from(imageDetailsArray);
        when(glanceApi.getImageApi(anyString())).thenReturn(imageApi);
        when(imageApi.get(definedImage.getExtId())).thenReturn(imageDetails);
        when(imageApi.get("not_existing_image_ext_id")).thenThrow(new NullPointerException());
        when(imageApi.get("error_image_ext_id")).thenThrow(new AuthorizationException());
        when(imageApi.list()).thenReturn(mock(PagedIterable.class));
        when(imageApi.list().concat()).thenReturn(resImageFI);
        when(imageApi.listInDetail()).thenReturn(mock(PagedIterable.class));
        when(imageApi.listInDetail().concat()).thenReturn(imaImageFI);
        when(imageApi.create(eq(definedImage.getName()), any(Payload.class), any(CreateImageOptions.class))).thenReturn(imageDetails);
        when(imageApi.create(eq("mocked_error_ext_image_id"), any(Payload.class), any(CreateImageOptions.class))).thenThrow(new AuthorizationException());
        when(imageApi.reserve(eq(definedImage.getName()), any(CreateImageOptions.class))).thenReturn(imageDetails);
        when(imageApi.reserve(eq("mocked_error_ext_image_id"), any(CreateImageOptions.class))).thenThrow(new AuthorizationException());
        when(imageApi.update(eq(definedImage.getExtId()), any(UpdateImageOptions.class))).thenReturn(imageDetails);
        when(imageApi.update(eq("mocked_error_ext_image_id"), any(UpdateImageOptions.class))).thenThrow(new AuthorizationException());
        when(imageApi.delete(definedImage.getExtId())).thenReturn(true);
        when(imageApi.delete("not_existing_image_ext_id")).thenThrow(NullPointerException.class);


        //FlavorApi
        FlavorApi flavorApi = mock(FlavorApi.class);
        when(novaApi.getFlavorApi(anyString())).thenReturn(flavorApi);
        when(flavorApi.get(definedFlavor.getExtId())).thenReturn(expFlavor);
        when(flavorApi.get("not_existing_flavor_ext_id")).thenThrow(new NullPointerException());
        when(flavorApi.get("error_flavor_ext_id_get")).thenThrow(new AuthorizationException());
//        PowerMockito.spy(Flavor.Builder.class);
//        Flavor.Builder builder = mock(Flavor.Builder.class);
//        Flavor flavor = mock(Flavor.class);
//        PowerMockito.doReturn(builder).when(Flavor.class, "builder");
//        //when(Flavor.builder()).thenReturn(builder);
//        //when(Flavor.class, "builder").thenReturn(builder);
//        when(builder.id(anyString())).thenReturn(builder);
//        when(builder.name(definedFlavor.getFlavour_key())).thenReturn(builder);
//        //when(builder.name(definedFlavor.getFlavour_key()).build()).thenReturn(flavor);
//        when(builder.name("error_flavor_ext_id")).thenThrow(new AuthorizationException());

        when(flavorApi.create(Matchers.<Flavor>anyObject())).thenReturn(expFlavor);
        doThrow(new AuthorizationException()).when(flavorApi).delete(errorFlavor.getId());
        when(flavorApi.list()).thenReturn(mock(PagedIterable.class));
        when(flavorApi.list().concat()).thenReturn(resFlavorFI);
        when(flavorApi.listInDetail()).thenReturn(mock(PagedIterable.class));
        when(flavorApi.listInDetail().concat()).thenReturn(flaFlavorFI);

        //NetworkApi
        NetworkApi networkApi = mock(NetworkApi.class);
        when(neutronApi.getNetworkApi(anyString())).thenReturn(networkApi);
        final org.jclouds.openstack.neutron.v2.domain.Network network = mock(org.jclouds.openstack.neutron.v2.domain.Network.class);
        final org.jclouds.openstack.neutron.v2.domain.Network otherNetwork = mock(org.jclouds.openstack.neutron.v2.domain.Network.class);
        final org.jclouds.openstack.neutron.v2.domain.Network publicNetwork = mock(org.jclouds.openstack.neutron.v2.domain.Network.class);
        when(networkApi.create(any(org.jclouds.openstack.neutron.v2.domain.Network.CreateNetwork.class))).thenReturn(network);
        //PowerMockito.spy(org.jclouds.openstack.neutron.v2.domain.Network.CreateNetwork.class);
        //org.jclouds.openstack.neutron.v2.domain.Network.CreateNetwork createNetwork = mock(org.jclouds.openstack.neutron.v2.domain.Network.CreateNetwork.class);
        //org.jclouds.openstack.neutron.v2.domain.Network.CreateBuilder createBuilder = mock(org.jclouds.openstack.neutron.v2.domain.Network.CreateBuilder.class);
        //doThrow(new AuthorizationException()).when(org.jclouds.openstack.neutron.v2.domain.Network.CreateNetwork.class)
        //when(org.jclouds.openstack.neutron.v2.domain.Network.CreateNetwork.createBuilder("error_network_name")).thenThrow(new AuthorizationException());
        //PowerMockito.when(org.jclouds.openstack.neutron.v2.domain.Network.CreateNetwork.class, "createBuilder", "error_network_name").thenThrow(new AuthorizationException());
        when(networkApi.update(eq(definedNetwork.getExtId()), any(org.jclouds.openstack.neutron.v2.domain.Network.UpdateNetwork.class))).thenReturn((network));
        when(networkApi.update(eq("error_network_ext_id"), any(org.jclouds.openstack.neutron.v2.domain.Network.UpdateNetwork.class))).thenThrow(new AuthorizationException());
        when(networkApi.delete(definedNetwork.getExtId())).thenReturn(true);
        when(networkApi.delete("not_delete_network_ext_id")).thenReturn(false);
        when(networkApi.delete("not_existing_network_ext_id")).thenThrow(new AuthorizationException());
        when(networkApi.get(definedNetwork.getExtId())).thenReturn(network);
        when(networkApi.get("mocked_public_network_ext_id")).thenReturn(publicNetwork);
        when(networkApi.get("not_existing_network_ext_id")).thenThrow(new NullPointerException());
        when(networkApi.get("error_network_ext_id")).thenThrow(new AuthorizationException());
        when(networkApi.list()).thenReturn(mock(PagedIterable.class));
        when(network.getName()).thenReturn(definedNetwork.getName());
        when(network.getId()).thenReturn(definedNetwork.getExtId());
        when(network.getSubnets()).thenReturn(ImmutableSet.<String>of(definedSubnet.getExtId()));
        when(network.getTenantId()).thenReturn("mocked_tenant_id");
        when(network.getExternal()).thenReturn(false);
        when(network.getShared()).thenReturn(false);
        when(publicNetwork.getName()).thenReturn("mocked_public_network_name");
        when(publicNetwork.getId()).thenReturn("mocked_public_network_ext_id");
        when(publicNetwork.getSubnets()).thenReturn(ImmutableSet.<String>of(definedSubnet.getExtId()));
        when(publicNetwork.getTenantId()).thenReturn("mocked_tenant_id");
        when(publicNetwork.getExternal()).thenReturn(true);
        when(publicNetwork.getShared()).thenReturn(false);
        when(otherNetwork.getTenantId()).thenReturn("mocked_other_tenant_id");
        when(networkApi.list().concat()).thenReturn(FluentIterable.from(new ArrayList<org.jclouds.openstack.neutron.v2.domain.Network>() {{
            add(network);
            add(otherNetwork);
            add(publicNetwork);
        }}));

        //RouterApi
        RouterApi routerApi = mock(RouterApi.class);
        when(neutronApi.getRouterApi(anyString())).thenReturn(mock(Optional.class));
        when(neutronApi.getRouterApi(anyString()).get()).thenReturn(routerApi);

        Set<Router> routerSet = new HashSet<Router>();
        Router router = mock(Router.class);
        routerSet.add(router);
        when(routerApi.list()).thenReturn(PagedIterables.onlyPage(IterableWithMarkers.from(routerSet)));
        when(router.getTenantId()).thenReturn("mocked_tenant_id");
        ExternalGatewayInfo externalGatewayInfo = mock(ExternalGatewayInfo.class);
        when(router.getExternalGatewayInfo()).thenReturn(externalGatewayInfo);
        when(externalGatewayInfo.getNetworkId()).thenReturn("mocked_public_network_ext_id");
        when(router.getId()).thenReturn("mocked_router_ext_id");
        Router.CreateRouter options = mock(Router.CreateRouter.class);
        Router.CreateBuilder createBuilder = mock(Router.CreateBuilder.class);
        when(routerApi.create(any(Router.CreateRouter.class))).thenReturn(router);
        when(router.getId()).thenReturn("mocked_router_ext_id");
        RouterInterface routerInterface = mock(RouterInterface.class);
        when(routerApi.addInterfaceForPort(anyString(), anyString())).thenReturn(routerInterface);
        when(routerInterface.getSubnetId()).thenReturn("mocked_subnet_ext_id");

        //PortApi
        PortApi portApi = mock(PortApi.class);
        when(neutronApi.getPortApi(anyString())).thenReturn(portApi);
        when(portApi.create(any(Port.CreatePort.class))).thenReturn(expPort);
        when(portApi.list()).thenReturn(mock(PagedIterable.class));
        final Port port = mock(Port.class);
        when(port.getId()).thenReturn("mocked_port_id");
        ArrayList<IP> ipList = new ArrayList<>();
        IP ip1 = mock(IP.class);
        IP ip2 = mock(IP.class);
        ipList.add(ip2);
        ipList.add(ip1);
        when(port.getFixedIps()).thenReturn(ImmutableSet.copyOf(ipList));
        when(ip2.getIpAddress()).thenReturn("0.0.0.0");
        when(ip1.getIpAddress()).thenReturn("mocked_ip_address");
        when(port.getFixedIps()).thenReturn(ImmutableSet.copyOf(ipList));
        when(portApi.list().concat()).thenReturn(FluentIterable.from(new ArrayList<Port>() {{
            add(port);
            add(expPort);
        }}));

        //SubnetApi
        SubnetApi subnetApi = mock(SubnetApi.class);
        when(neutronApi.getSubnetApi(anyString())).thenReturn(subnetApi);
        org.jclouds.openstack.neutron.v2.domain.Subnet subnet = mock(org.jclouds.openstack.neutron.v2.domain.Subnet.class);
        when(subnetApi.create(any(org.jclouds.openstack.neutron.v2.domain.Subnet.CreateSubnet.class))).thenReturn(subnet);
        when(subnetApi.update(eq(definedSubnet.getExtId()), any(org.jclouds.openstack.neutron.v2.domain.Subnet.UpdateSubnet.class))).thenReturn((subnet));
        when(subnetApi.update(eq("error_subnet_ext_id"), any(org.jclouds.openstack.neutron.v2.domain.Subnet.UpdateSubnet.class))).thenThrow(new AuthorizationException());
        when(subnetApi.delete(definedSubnet.getExtId())).thenReturn(true);
        when(subnetApi.delete("not_existing_subnet_ext_id")).thenReturn(false);
        when(subnetApi.delete("error_subnet_ext_id")).thenThrow(new AuthorizationException());
        when(subnetApi.get(definedSubnet.getExtId())).thenReturn(subnet);
        when(subnet.getName()).thenReturn(definedSubnet.getName());
        when(subnet.getId()).thenReturn(definedSubnet.getExtId());
        when(subnet.getCidr()).thenReturn(definedSubnet.getCidr());

        //FloatingIPApi
        FloatingIPApi floatingIPApi = mock(FloatingIPApi.class);
        when(novaApi.getFloatingIPApi(anyString())).thenReturn(mock(Optional.class));
        when(novaApi.getFloatingIPApi(anyString()).get()).thenReturn(floatingIPApi);
        when(floatingIPApi.list()).thenReturn(fipFI);
        when(floatingIPApi.allocateFromPool(anyString())).thenReturn(expFreeFloatingIP);

        //QuotaApi
        QuotaApi quotaApi = mock(QuotaApi.class);
        when(novaApi.getQuotaApi(anyString())).thenReturn(mock(Optional.class));
        when(novaApi.getQuotaApi(anyString()).get()).thenReturn(quotaApi);
        when(quotaApi.getByTenant(vimInstance.getTenant())).thenReturn(expQuota);

        //Specific stuff
        ComputeServiceContext computeServiceContext = mock(ComputeServiceContext.class);
        when(contextBuilder.buildView(ComputeServiceContext.class)).thenReturn(computeServiceContext);
        Utils utils = mock(Utils.class);
        when(computeServiceContext.utils()).thenReturn(utils);
        Injector injector = mock(Injector.class);
        when(utils.injector()).thenReturn(injector);
        Function<Credentials, Access> auth = mock(Function.class);
        when(injector.getInstance(any(Key.class))).thenReturn(auth);
        Access access = mock(Access.class);
        when(auth.apply(any(Credentials.class))).thenReturn(access);
        Token token = mock(Token.class);
        when(access.getToken()).thenReturn(token);
        Tenant tenant = mock(Tenant.class);
        when(tenant.getId()).thenReturn("mocked_tenant_id");
        Optional<Tenant> tenantOP = mock(Optional.class);
        when(token.getTenant()).thenReturn(tenantOP);
        when(tenantOP.get()).thenReturn(tenant);
        ArrayList<Service> services = new ArrayList<>();
        Service service_nova = mock(Service.class);
        Service service_neutron = mock(Service.class);
        services.add(service_nova);
        services.add(service_neutron);
        when(access.iterator()).thenReturn(services.iterator());
        when(service_neutron.getName()).thenReturn("neutron");
        when(service_nova.getName()).thenReturn("nova");
        ArrayList<org.jclouds.openstack.keystone.v2_0.domain.Endpoint> endpoints = new ArrayList<>();
        org.jclouds.openstack.keystone.v2_0.domain.Endpoint endpoint = mock(org.jclouds.openstack.keystone.v2_0.domain.Endpoint.class);
        endpoints.add(endpoint);
        when(service_nova.iterator()).thenReturn(endpoints.iterator());
        when(service_neutron.iterator()).thenReturn(endpoints.iterator());
        when(endpoint.getPublicURL()).thenReturn(new URI("http://mocked_URI"));
    }

    @Test
    public void testLaunchInstance() throws VimDriverException {
        Server server = openstackClient.launchInstance(vimInstance, definedServer.getName(), definedServer.getImage().getExtId(), definedServer.getFlavor().getExtId(), "keypair", new HashSet<String>(), new HashSet<String>(), "#userdata");
        assertEqualsServers(definedServer, server);
        try {
            server = openstackClient.launchInstance(vimInstance, faultyServer.getName(), faultyServer.getImage().getId(), faultyServer.getFlavor().getId(), "keypair", new HashSet<String>(), new HashSet<String>(), "#userdata");
        } catch (VimDriverException e) {
            //everything fine
        }
        try {
            server = openstackClient.launchInstance(vimInstance, faultyServer.getName(), definedServer.getImage().getExtId(), faultyServer.getFlavor().getId(), "keypair", new HashSet<String>(), new HashSet<String>(), "#userdata");
        } catch (VimDriverException e) {
            //everything fine
        }
        server = openstackClient.launchInstance(vimInstance, definedServer.getName(), definedServer.getImage().getExtId(), "not_existing_flavor_ext_id", "keypair", new HashSet<String>(), new HashSet<String>(), "#userdata");
        try {
            server = openstackClient.launchInstance(vimInstance, "faulty_server", definedServer.getImage().getExtId(), definedServer.getFlavor().getExtId(), "keypair", new HashSet<String>(), new HashSet<String>(), "#userdata");
        } catch (VimDriverException e) {
            //everything fine
        }

    }

    @Test
    public void testLauchInstanceAndWait() throws Exception {
        Server server = openstackClient.launchInstanceAndWait(vimInstance, definedServer.getName(), definedServer.getImage().getExtId(), definedServer.getFlavor().getExtId(), "keypair", new HashSet<String>(), new HashSet<String>(), "#userdata");
        assertEqualsServers(definedServer, server);
        try {
            server = openstackClient.launchInstanceAndWait(vimInstance, "error_server", definedServer.getImage().getExtId(), definedServer.getFlavor().getExtId(), "keypair", new HashSet<String>(), new HashSet<String>(), "#userdata");
        } catch (VimDriverException e) {
            //exception.expect(VimDriverException.class);
        }
        try {
            server = openstackClient.launchInstanceAndWait(vimInstance, "error_server", errorImage.getId(), errorFlavor.getId(), "keypair", new HashSet<String>(), new HashSet<String>(), "#userdata");
        } catch (VimDriverException e) {
            //exception.expect(VimDriverException.class);
        }
        PowerMockito.spy(Thread.class);
        PowerMockito.doThrow(new InterruptedException("tirggered InterruptedException")).when(Thread.class);
        Thread.sleep(Mockito.anyLong());
        server = openstackClient.launchInstanceAndWait(vimInstance, definedServer.getName(), definedServer.getImage().getExtId(), definedServer.getFlavor().getExtId(), "keypair", new HashSet<String>(), new HashSet<String>(), "#userdata");
    }

    @Test
    public void testLauchInstanceAndWaitFloatingIp() throws Exception {
        final HttpURLConnection connection = mock(HttpURLConnection.class);
        URL url = PowerMockito.mock(URL.class);
        PowerMockito.whenNew(URL.class).withAnyArguments().thenReturn(url);
        when(url.openConnection()).thenReturn(connection);
        OutputStream outs = mock(OutputStream.class);
        when(connection.getOutputStream()).thenReturn(outs);
        OutputStreamWriter outsw = new OutputStreamWriter(outs);
        Quota quota_expected = createQuota();
        String response = "";
        InputStream is = new ByteArrayInputStream(response.getBytes());
        when(connection.getInputStream()).thenReturn(is);

        HashMap<String, String> fip = new HashMap<>();
        fip.put("mocked_private_network_name", "0.0.0.0");
        Server server = openstackClient.launchInstanceAndWait(vimInstance, definedServer.getName(), definedServer.getImage().getExtId(), definedServer.getFlavor().getExtId(), "keypair", new HashSet<String>(), new HashSet<String>(), "#userdata", fip);
        assertEqualsServers(definedServer, server);
    }

    @Test
    public void testListServer() throws Exception {
        try {
            List<Server> servers = openstackClient.listServer(vimInstance);
            assertEqualsServers(definedServer, servers.get(0));
        } catch (VimDriverException e) {
            //expected
        }

        NovaApi novaApi = mock(NovaApi.class);
        PowerMockito.spy(ContextBuilder.class);
        ContextBuilder contextBuilder = mock(ContextBuilder.class);
        PowerMockito.doReturn(contextBuilder).when(ContextBuilder.class, "newBuilder", Mockito.anyString());
        when(contextBuilder.endpoint(anyString())).thenReturn(contextBuilder);
        when(contextBuilder.credentials(anyString(), anyString())).thenReturn(contextBuilder);
        when(contextBuilder.modules(any(Iterable.class))).thenReturn(contextBuilder);
        when(contextBuilder.overrides(any(Properties.class))).thenReturn(contextBuilder);
        when(contextBuilder.buildApi(NovaApi.class)).thenReturn(novaApi);
        ServerApi serverApi = mock(ServerApi.class);
        when(novaApi.getServerApi(anyString())).thenReturn(serverApi);
        when(serverApi.listInDetail()).thenThrow(new AuthorizationException());
        exception.expect(VimDriverException.class);
        List<Server> servers = openstackClient.listServer(vimInstance);
    }

    @Test
    public void deleteServerByIdAndWait() throws Exception {
        //doThrow(new NullPointerException()).when(openstackClient);
        openstackClient.deleteServerByIdAndWait(vimInstance, "not_existing_server_ext_id");
        PowerMockito.mockStatic(Thread.class);
        PowerMockito.doThrow(new InterruptedException("tirggered InterruptedException")).when(Thread.class, "sleep", (long) 1000);
        //PowerMockito.doThrow(new InterruptedException("tirggered InterruptedException")).when(Thread.class);
        Thread.sleep(1000);
        //doThrow(new InterruptedException("tirggered InterruptedException")).when(Thread.class);
        //when(Thread.class).thenThrow(new InterruptedException("tirggered InterruptedException"));

        openstackClient.deleteServerByIdAndWait(vimInstance, "not_existing_server_ext_id");

    }

    @Test
    public void testRebootServer() throws VimDriverException {
        openstackClient.rebootServer(vimInstance, definedServer.getExtId(), RebootType.SOFT);
        exception.expect(VimDriverException.class);
        openstackClient.rebootServer(vimInstance, "not_existing_ext_id", RebootType.SOFT);
    }

    @Test
    public void testDeleteServerById() throws VimDriverException {
        openstackClient.deleteServerById(vimInstance, definedServer.getExtId());
        exception.expect(VimDriverException.class);
        openstackClient.deleteServerById(vimInstance, "not_existing_ext_id");
    }

    @Test
    public void testDeleteServerByIdAndWait() throws VimDriverException {
        openstackClient.deleteServerByIdAndWait(vimInstance, "not_existing_server_ext_id");
    }

    @Test
    public void testAddImage() throws VimDriverException {
        NFVImage image = openstackClient.addImage(vimInstance, definedImage, "mocked_inputstream".getBytes());
        assertEqualsImages(image, definedImage);
        definedImage.setName("mocked_error_ext_image_id");
        exception.expect(VimDriverException.class);
        image = openstackClient.addImage(vimInstance, definedImage, "mocked_inputstream".getBytes());
    }

    @Test
    public void testAddImageByURL() throws VimDriverException {
        NFVImage image = openstackClient.addImage(vimInstance, definedImage, "mocked_image_url");
        assertEqualsImages(image, definedImage);
        definedImage.setName("mocked_error_ext_image_id");
        exception.expect(VimDriverException.class);
        image = openstackClient.addImage(vimInstance, definedImage, "mocked_image_url");
    }

    @Test
    public void testUpdateImage() throws VimDriverException {
        NFVImage image = openstackClient.updateImage(vimInstance, definedImage);
        assertEqualsImages(image, definedImage);
        definedImage = createImage();
        definedImage.setExtId("mocked_error_ext_image_id");
        exception.expect(VimDriverException.class);
        image = openstackClient.updateImage(vimInstance, definedImage);
    }

    @Test
    public void testDeleteImage() throws VimDriverException {
        boolean isDeleted = openstackClient.deleteImage(vimInstance, definedImage);
        Assert.assertEquals(true, isDeleted);
        definedImage.setExtId("not_existing_image_ext_id");
        exception.expect(VimDriverException.class);
        isDeleted = openstackClient.deleteImage(vimInstance, definedImage);

    }

    @Test
    public void testListImages() throws Exception {
        List<NFVImage> images = openstackClient.listImages(vimInstance);
        assertEqualsImages(definedImage, images.get(0));
        //throw exception
        GlanceApi glanceApi = mock(GlanceApi.class);
        PowerMockito.spy(ContextBuilder.class);
        ContextBuilder contextBuilder = mock(ContextBuilder.class);
        PowerMockito.doReturn(contextBuilder).when(ContextBuilder.class, "newBuilder", Mockito.anyString());
        when(contextBuilder.endpoint(anyString())).thenReturn(contextBuilder);
        when(contextBuilder.credentials(anyString(), anyString())).thenReturn(contextBuilder);
        when(contextBuilder.modules(any(Iterable.class))).thenReturn(contextBuilder);
        when(contextBuilder.overrides(any(Properties.class))).thenReturn(contextBuilder);
        when(contextBuilder.buildApi(GlanceApi.class)).thenReturn(glanceApi);
        ImageApi imageApi = mock(ImageApi.class);
        when(glanceApi.getImageApi(anyString())).thenReturn(imageApi);
        when(imageApi.listInDetail()).thenReturn(mock(PagedIterable.class));
        when(imageApi.listInDetail().concat()).thenThrow(new AuthorizationException());
        exception.expect(VimDriverException.class);
        images = openstackClient.listImages(vimInstance);
    }

    @Test
    public void testCopyImage() throws VimDriverException {
        NFVImage image = openstackClient.copyImage(vimInstance, definedImage, new byte[0]);
        assertEqualsImages(image, definedImage);
        definedImage.setExtId("not_existing_image_ext_id");
        exception.expect(VimDriverException.class);
        image = openstackClient.copyImage(vimInstance, definedImage, new byte[0]);
    }

    @Test
    public void testAddFlavor() throws VimDriverException {
        DeploymentFlavour flavor = openstackClient.addFlavor(vimInstance, definedFlavor);
        assertEqualsFlavors(definedFlavor, flavor);
        definedFlavor.setExtId("error_flavor_ext_id");
        flavor = openstackClient.addFlavor(vimInstance, definedFlavor);
    }

    @Test
    public void testUpdateFlavor() throws VimDriverException {
        DeploymentFlavour flavor;
        try {
            flavor = openstackClient.updateFlavor(vimInstance, definedFlavor);
        } catch (VimDriverException e) {
            //expected
        }
        definedFlavor.setExtId("not_existing_flavor_ext_id");
        flavor = openstackClient.updateFlavor(vimInstance, definedFlavor);
        assertEqualsFlavors(definedFlavor, flavor);
        exception.expect(VimDriverException.class);
        openstackClient.updateFlavor(vimInstance, new DeploymentFlavour());
    }

    @Test
    public void testListFlavors() throws Exception {
        List<DeploymentFlavour> flavors = openstackClient.listFlavors(vimInstance);
        assertEqualsFlavors(definedFlavor, flavors.get(0));
        NovaApi novaApi = mock(NovaApi.class);
        PowerMockito.spy(ContextBuilder.class);
        ContextBuilder contextBuilder = mock(ContextBuilder.class);
        PowerMockito.doReturn(contextBuilder).when(ContextBuilder.class, "newBuilder", Mockito.anyString());
        when(contextBuilder.endpoint(anyString())).thenReturn(contextBuilder);
        when(contextBuilder.credentials(anyString(), anyString())).thenReturn(contextBuilder);
        when(contextBuilder.modules(any(Iterable.class))).thenReturn(contextBuilder);
        when(contextBuilder.overrides(any(Properties.class))).thenReturn(contextBuilder);
        when(contextBuilder.buildApi(NovaApi.class)).thenReturn(novaApi);
        FlavorApi flavorApi = mock(FlavorApi.class);
        when(novaApi.getFlavorApi(anyString())).thenReturn(flavorApi);
        when(flavorApi.listInDetail()).thenReturn(mock(PagedIterable.class));
        when(flavorApi.listInDetail().concat()).thenThrow(new AuthorizationException());
        exception.expect(VimDriverException.class);
        openstackClient.listFlavors(vimInstance);

    }

    @Test
    public void testDeleteFlavor() throws VimDriverException {
        openstackClient.deleteFlavor(vimInstance, definedFlavor.getExtId());
        try {
            openstackClient.deleteFlavor(vimInstance, "not_existing_flavor_ext_id");
        } catch (VimDriverException e) {
            //everything fine
        }
        exception.expect(VimDriverException.class);
        openstackClient.deleteFlavor(vimInstance, "error_flavor_ext_id_get");
    }

    @Test
    public void testCreateNetwork() throws VimDriverException {
        Network network = openstackClient.createNetwork(vimInstance, definedNetwork);
        assertEqualsNetworks(definedNetwork, network);

        when(org.jclouds.openstack.neutron.v2.domain.Network.CreateNetwork.class).thenThrow(new AuthorizationException());
        exception.expect(VimDriverException.class);
        openstackClient.createNetwork(vimInstance, definedNetwork);
    }

    @Test
    public void testUpdateNetwork() throws VimDriverException {
        try {
            Network network = openstackClient.updateNetwork(vimInstance, definedNetwork);
            assertEqualsNetworks(definedNetwork, network);
            assert true;
        } catch (Exception e) {
            assert false;
        }
        try {
            definedNetwork.setExtId("error_network_ext_id");
            openstackClient.updateNetwork(vimInstance, definedNetwork);
            assert false;
        } catch (VimDriverException e) {
            assert true;
        }
    }

    @Test
    public void testDeleteNetwork() throws VimDriverException {
        boolean isDeleted = openstackClient.deleteNetwork(vimInstance, definedNetwork.getExtId());
        Assert.assertEquals(true, isDeleted);
        isDeleted = openstackClient.deleteNetwork(vimInstance, "not_delete_network_ext_id");
        Assert.assertEquals(false, isDeleted);
        exception.expect(VimDriverException.class);
        openstackClient.deleteNetwork(vimInstance, "not_existing_network_ext_id");

    }

    @Test
    public void testGetNetworkById() throws VimDriverException {
        try {
            Network network = openstackClient.getNetworkById(vimInstance, definedNetwork.getExtId());
            assertEqualsNetworks(definedNetwork, network);
            assert true;
        } catch (Exception e) {
            assert false;
        }
        try {
            openstackClient.getNetworkById(vimInstance, "not_existing_network_ext_id");
            assert false;
        } catch (NullPointerException e) {
            assert true;
        }
        try {
            openstackClient.getNetworkById(vimInstance, "error_network_ext_id");
            assert false;
        } catch (VimDriverException e) {
            assert true;
        }
    }

    @Test
    public void testGetNetworkIdByName() throws VimDriverException {

    }

    @Test
    public void testGetSubnetsExtIds() throws VimDriverException {
        try {
            List<String> subnetExtIds = openstackClient.getSubnetsExtIds(vimInstance, definedNetwork.getExtId());
            Assert.assertEquals(subnetExtIds.get(0), definedSubnet.getExtId());
            assert true;
        } catch (Exception e) {
            assert false;
        }
        try {
            openstackClient.getSubnetsExtIds(vimInstance, "not_existing_network_ext_id");
            assert false;
        } catch (NullPointerException e) {
            assert true;
        }
        try {
            openstackClient.getSubnetsExtIds(vimInstance, "error_network_ext_id");
            assert false;
        } catch (VimDriverException e) {
            assert true;
        }

    }

    @Test
    public void testListNetworks() throws VimDriverException {
        try {
            List<Network> networks = openstackClient.listNetworks(vimInstance);
            assertEqualsNetworks(definedNetwork, networks.get(0));
            assert true;
        } catch (Exception e) {
            assert false;
        }
        try {
            when(ContextBuilder.class).thenThrow(new AuthorizationException());
            List<Network> networks = openstackClient.listNetworks(vimInstance);
            assert false;
        } catch (VimDriverException e) {
            assert true;
        }
    }

    @Test
    public void testCreateSubnet() throws VimDriverException {
        try {
            Subnet subnet = openstackClient.createSubnet(vimInstance, definedNetwork, definedSubnet);
            assertEqualsSubnets(definedSubnet, subnet);
            assert true;
        } catch (Exception e) {
            assert false;
        }
        try {
            when(org.jclouds.openstack.neutron.v2.domain.Subnet.CreateSubnet.class).thenThrow(new AuthorizationException());
            openstackClient.createSubnet(vimInstance, definedNetwork, definedSubnet);
            assert false;
        } catch (VimDriverException e) {
            assert true;
        }
    }

    @Test
    public void testUpdateSubnet() throws VimDriverException {
        try {
            Subnet subnet = openstackClient.updateSubnet(vimInstance, definedNetwork, definedSubnet);
            assertEqualsSubnets(definedSubnet, subnet);
            assert true;
        } catch (Exception e) {
            assert false;
        }
        try {
            definedSubnet.setExtId("error_subnet_ext_id");
            openstackClient.updateSubnet(vimInstance, definedNetwork, definedSubnet);
            assert false;
        } catch (Exception e) {
            assert true;
        }

    }

    @Test
    public void testDeleteSubnet() throws VimDriverException {
        try {
            boolean isDeleted = openstackClient.deleteSubnet(vimInstance, definedSubnet.getExtId());
            Assert.assertEquals(true, isDeleted);
            assert true;
        } catch (Exception e) {
            assert false;
        }
        try {
            boolean isDeleted = openstackClient.deleteSubnet(vimInstance, "not_existing_subnet_ext_id");
            Assert.assertEquals(false, isDeleted);
            assert true;
        } catch (Exception e) {
            assert false;
        }
        try {
            boolean isDeleted = openstackClient.deleteSubnet(vimInstance, "error_subnet_ext_id");
            assert false;
        } catch (VimDriverException e) {
            assert true;
        }

    }

    @Test
    public void testListSubnets() {

    }

    @Test
    public void testGetType() {
        String type = openstackClient.getType(vimInstance);
        Assert.assertEquals("openstack", type);
    }

    @Test
    public void testGetQuota() throws Exception {
        final HttpURLConnection connection = mock(HttpURLConnection.class);
        URL url = PowerMockito.mock(URL.class);
        PowerMockito.whenNew(URL.class).withAnyArguments().thenReturn(url);
        when(url.openConnection()).thenReturn(connection);
        Quota quota_expected = createQuota();
        String response = "{quota_set:{cores:" + quota_expected.getCores() + "; ram:" + quota_expected.getRam() + "; instances:" + quota_expected.getInstances() + "; floating_ips:" + quota_expected.getFloatingIps() + "; key_pairs:" + quota_expected.getKeyPairs() + "}}";
        InputStream is = new ByteArrayInputStream(response.getBytes());
        when(connection.getInputStream()).thenReturn(is);

        Quota quota_actual = openstackClient.getQuota(vimInstance);
        assertEqualsQuotas(definedQuota, quota_actual);
    }

    private VimInstance createVimInstance() {
        VimInstance vimInstance = new VimInstance();
        vimInstance.setName("mocked_vim_instance");
        vimInstance.setTenant("mocked_tenant");
        return vimInstance;
    }

    private NFVImage createImage() {
        NFVImage image = new NFVImage();
        image.setName("mocked_image_name");
        image.setExtId("mocked_image_id");
        image.setMinRam(512);
        image.setMinDiskSpace(2);
        image.setIsPublic(true);
        image.setContainerFormat("AMI");
        image.setDiskFormat("AMI");
        image.setCreated(new Date());
        image.setUpdated(new Date());
        return image;
    }

    private DeploymentFlavour createFlavor() {
        DeploymentFlavour flavor = new DeploymentFlavour();
        flavor.setExtId("mocked_flavor_id");
        flavor.setFlavour_key("mocked_flavor_name");
        flavor.setRam(512);
        flavor.setDisk(1);
        flavor.setVcpus(2);
        return flavor;
    }

    private Server createServer() {
        Server server = new Server();
        server.setExtId("mocked_server_id");
        server.setName("mocked_server_name");
        server.setImage(definedImage);
        server.setFlavor(definedFlavor);
        server.setStatus("ACTIVE");
        server.setExtendedStatus("mocked_extended_status");
        HashMap<String, List<String>> ipMap = new HashMap<String, List<String>>();
        LinkedList<String> ips = new LinkedList();
        ips.add("mocked_ip");
        ipMap.put("mocked_network", ips);
        server.setIps(ipMap);
//        server.setFloatingIp("mocked_floating_ip");
        server.setCreated(new Date());
        server.setUpdated(new Date());
        return server;
    }

    private Network createNetwork() {
        Network network = new Network();
        network.setName("mocked_network_name");
        network.setExtId("mocked_network_ext_id");
        network.setExternal(false);
        network.setShared(false);
        network.setSubnets(new HashSet<Subnet>());
        network.getSubnets().add(createSubnet());
        return network;
    }

    private Subnet createSubnet() {
        Subnet subnet = new Subnet();
        subnet.setName("mocked_subnet_name");
        subnet.setExtId("mocked_subnet_ext_id");
        subnet.setCidr("192.168.123.0/24");
        return subnet;
    }

    private Quota createQuota() {
        Quota quota = new Quota();
        quota.setCores(10);
        quota.setInstances(10);
        quota.setRam(10);
        quota.setTenant("mocked_tenant");
        quota.setKeyPairs(10);
        quota.setFloatingIps(10);
        return quota;
    }

    private void assertEqualsServers(Server expectedServer, Server actualServer) {
        Assert.assertEquals(expectedServer.getName(), actualServer.getName());
        Assert.assertEquals(expectedServer.getExtId(), actualServer.getExtId());
        Assert.assertEquals(expectedServer.getStatus(), actualServer.getStatus());
    }

    private void assertEqualsImages(NFVImage expectedImage, NFVImage actualImage) {
        Assert.assertEquals(expectedImage.getName(), actualImage.getName());
        Assert.assertEquals(expectedImage.getExtId(), actualImage.getExtId());
        Assert.assertEquals(expectedImage.getMinCPU(), actualImage.getMinCPU());
        Assert.assertEquals(expectedImage.getMinDiskSpace(), actualImage.getMinDiskSpace());
        Assert.assertEquals(expectedImage.getMinRam(), actualImage.getMinRam());
    }

    private void assertEqualsFlavors(DeploymentFlavour expectedFlavor, DeploymentFlavour actualFlavor) {
        Assert.assertEquals(expectedFlavor.getExtId(), actualFlavor.getExtId());
        Assert.assertEquals(expectedFlavor.getFlavour_key(), actualFlavor.getFlavour_key());
    }

    private void assertEqualsNetworks(Network expectedNetwork, Network actualNetwork) {
        Assert.assertEquals(expectedNetwork.getName(), actualNetwork.getName());
        Assert.assertEquals(expectedNetwork.getExternal(), actualNetwork.getExternal());
        Assert.assertEquals(expectedNetwork.getExtId(), actualNetwork.getExtId());
        //Assert.assertEquals(expectedNetwork.getSubnets(), actualNetwork.getSubnets());
    }

    private void assertEqualsSubnets(Subnet expectedSubnet, Subnet actualSubnet) {
        Assert.assertEquals(expectedSubnet.getExtId(), actualSubnet.getExtId());
        Assert.assertEquals(expectedSubnet.getName(), actualSubnet.getName());
        Assert.assertEquals(expectedSubnet.getCidr(), actualSubnet.getCidr());
        Assert.assertEquals(expectedSubnet.getNetworkId(), actualSubnet.getNetworkId());
    }

    private void assertEqualsQuotas(Quota expectedQuota, Quota actualQuota) {
        Assert.assertEquals(expectedQuota.getTenant(), actualQuota.getTenant());
        Assert.assertEquals(expectedQuota.getCores(), actualQuota.getCores());
        Assert.assertEquals(expectedQuota.getFloatingIps(), actualQuota.getFloatingIps());
        Assert.assertEquals(expectedQuota.getInstances(), actualQuota.getInstances());
        Assert.assertEquals(expectedQuota.getKeyPairs(), actualQuota.getKeyPairs());
        Assert.assertEquals(expectedQuota.getRam(), actualQuota.getRam());
    }
}
