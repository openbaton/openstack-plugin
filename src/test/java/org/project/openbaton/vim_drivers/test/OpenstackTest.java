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

package org.project.openbaton.vim_drivers.test;

import com.google.common.base.Optional;
import com.google.common.collect.*;
import org.jclouds.collect.IterableWithMarker;
import org.jclouds.collect.IterableWithMarkers;
import org.jclouds.collect.PagedIterable;
import org.jclouds.io.Payload;
import org.jclouds.openstack.glance.v1_0.GlanceApi;
import org.jclouds.openstack.glance.v1_0.domain.ContainerFormat;
import org.jclouds.openstack.glance.v1_0.domain.DiskFormat;
import org.jclouds.openstack.glance.v1_0.domain.ImageDetails;
import org.jclouds.openstack.glance.v1_0.features.ImageApi;
import org.jclouds.openstack.glance.v1_0.options.CreateImageOptions;
import org.jclouds.openstack.glance.v1_0.options.UpdateImageOptions;
import org.jclouds.openstack.keystone.v2_0.KeystoneApi;
import org.jclouds.openstack.neutron.v2.NeutronApi;
import org.jclouds.openstack.neutron.v2.extensions.RouterApi;
import org.jclouds.openstack.neutron.v2.features.NetworkApi;
import org.jclouds.openstack.neutron.v2.features.SubnetApi;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.domain.*;
import org.jclouds.openstack.nova.v2_0.extensions.FloatingIPApi;
import org.jclouds.openstack.nova.v2_0.extensions.QuotaApi;
import org.jclouds.openstack.nova.v2_0.features.FlavorApi;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.jclouds.openstack.v2_0.domain.Link;
import org.jclouds.openstack.v2_0.domain.Resource;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.mockito.Matchers;
import org.project.openbaton.catalogue.mano.common.DeploymentFlavour;
import org.project.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.project.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.project.openbaton.catalogue.nfvo.*;
import org.project.openbaton.catalogue.nfvo.Network;
import org.project.openbaton.catalogue.nfvo.Quota;
import org.project.openbaton.catalogue.nfvo.Server;
import org.project.openbaton.clients.exceptions.VimDriverException;
import org.project.openbaton.clients.interfaces.client.openstack.OpenstackClient;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import java.rmi.RemoteException;
import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;


/**
 * Created by mpa on 07.05.15.
 */
@TestExecutionListeners({DependencyInjectionTestExecutionListener.class})
@TestPropertySource(properties = {"timezone = GMT", "port: 4242"})
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

    private MyServer expServer;
    private MyServer faultyServer;
    private MyServer errorServer;
    private MyResource expServerResource;
    private MyGlanceImage expImageResource;
    private MyResource expFlavorResource;
    private MyFlavor expFlavor;
    private MyFlavor faultyFlavor;
    private MyNovaImage expImage;
    private MyNovaImage faultyImage;
    private MyFloatingIP expFreeFloatingIP;
    private MyFloatingIP expUsedFloatingIP;
    private MyQuota expQuota;

    @Before
    public void init() throws RemoteException {
        openstackClient = spy(new OpenstackClient());
        doNothing().when(openstackClient).init(any(VimInstance.class));

        //pre-defined entities
        vimInstance = createVimInstance();
        definedImage = createImage();
        definedNetwork = createNetwork();
        definedSubnet = createSubnet();
        definedFlavor = createFlavor();
        definedServer = createServer();
        definedQuota = createQuota();
        //VimInstance
        openstackClient.setVimInstance(vimInstance);
        //NeutronApi
        NeutronApi neutronApi = mock(NeutronApi.class);
        openstackClient.setNeutronApi(neutronApi);
        //NovaApi
        NovaApi novaApi = mock(NovaApi.class);
        openstackClient.setNovaApi(novaApi);
        //Glance Api
        GlanceApi glanceApi = mock(GlanceApi.class);
        openstackClient.setGlanceApi(glanceApi);
        //TenantId
        openstackClient.setTenantId("mocked_tenant_id");

        //Flavor
        expFlavor = new MyFlavor(definedFlavor.getExtId(), definedFlavor.getFlavour_key(), new HashSet<Link>(), 512, 1, 2, "", 1.1, 1);
        faultyFlavor = new MyFlavor("not_existing_flavor_ext_id", definedFlavor.getFlavour_key(), new HashSet<Link>(), 512, 1, 2, "", 1.1, 1);
        //Image
        expImage = new MyNovaImage(definedImage.getExtId(), definedImage.getName(), new HashSet<Link>(), new Date(), new Date(), "", "", Image.Status.ACTIVE, 1, (int) definedImage.getMinDiskSpace(), (int) definedImage.getMinRam(), new ArrayList<BlockDeviceMapping>(), expImageResource, new HashMap<String, String>());
        faultyImage = new MyNovaImage("not_existing_image_ext_id", definedImage.getName(), new HashSet<Link>(), new Date(), new Date(), "", "", Image.Status.ACTIVE, 1, (int) definedImage.getMinDiskSpace(), (int) definedImage.getMinRam(), new ArrayList<BlockDeviceMapping>(), expImageResource, new HashMap<String, String>());
        //Server and Resources
        ServerExtendedStatus extStatus = new MyExtendedStatus("mocked_id", "mocked_name", 0);
        Map<String, Collection<Address>> addressMap = new HashMap<>();
        Collection<Address> addresses = new HashSet<>();
        addresses.add(new MyAddress("mocked_address", 4));
        addressMap.put("network", addresses);
        Multimap<String, Address> multimap = ArrayListMultimap.create();
        for (String key : addressMap.keySet()) {
            multimap.putAll(key, addressMap.get(key));
        }
        expServer = new MyServer(definedServer.getExtId(), definedServer.getName(), new HashSet<Link>(), definedServer.getExtId(), "", "", definedServer.getUpdated(), definedServer.getCreated(), "", "mocked_ip4", "mocked_ip6", org.jclouds.openstack.nova.v2_0.domain.Server.Status.fromValue(definedServer.getStatus()), expImage, expFlavor, "", "", multimap , new HashMap<String, String>(), extStatus, mock(ServerExtendedAttributes.class), "", "");
        faultyServer = new MyServer("faulty_server_mocked_ext_id", "faulty_server", new HashSet<Link>(), definedServer.getExtId(), "", "", definedServer.getUpdated(), definedServer.getCreated(), "", "mocked_ip4", "mocked_ip6", org.jclouds.openstack.nova.v2_0.domain.Server.Status.ERROR, faultyImage, faultyFlavor, "", "", mock(Multimap.class), new HashMap<String, String>(), extStatus, mock(ServerExtendedAttributes.class), "", "");
        errorServer = new MyServer("error_server_mocked_ext_id", "error_server", new HashSet<Link>(), definedServer.getExtId(), "", "", definedServer.getUpdated(), definedServer.getCreated(), "", "mocked_ip4", "mocked_ip6", org.jclouds.openstack.nova.v2_0.domain.Server.Status.ERROR, expImage, expFlavor, "", "", mock(Multimap.class), new HashMap<String, String>(), extStatus, mock(ServerExtendedAttributes.class), "", "");
        ServerCreated serverCreated = mock(ServerCreated.class);
        ServerCreated faultyServerCreated = mock(ServerCreated.class);
        ServerCreated errorServerCreated = mock(ServerCreated.class);

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

        //Server
        List<org.jclouds.openstack.nova.v2_0.domain.Server> serServerArray = new ArrayList<org.jclouds.openstack.nova.v2_0.domain.Server>();
        serServerArray.add(expServer);
        FluentIterable<org.jclouds.openstack.nova.v2_0.domain.Server> serServerFI = FluentIterable.from(serServerArray);

        //Flavor
        List<Flavor> flaFlavorArray = new ArrayList<Flavor>();
        flaFlavorArray.add(expFlavor);
        FluentIterable<Flavor> flaFlavorFI = FluentIterable.from(flaFlavorArray);

        //FloatingIP
        expFreeFloatingIP = new MyFloatingIP("mocked_ext_id", "mocked_free_ip", "mocked_fixed_ip", null, "mocked_pool");
        expUsedFloatingIP = new MyFloatingIP("mocked_ext_id", "mocked_used_ip", "mocked_fixed_ip", "mocked_instance_id", "mocked_pool");
        Set<FloatingIP> fipSet = new HashSet<FloatingIP>();
        fipSet.add(expFreeFloatingIP);
        fipSet.add(expUsedFloatingIP);
        FluentIterable<FloatingIP> fipFI = FluentIterable.from(fipSet);

        //Quota
        expQuota = new MyQuota(definedQuota.getTenant(), 10, 10, 10, 10, definedQuota.getRam(), definedQuota.getFloatingIps(), definedQuota.getInstances(), 10, definedQuota.getCores(), 10, 10, definedQuota.getKeyPairs());

        //exception.expect(NullPointerException.class);
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
        when(serverApi.create(eq("faulty_server"), anyString(), anyString(), any(CreateServerOptions.class))).thenReturn(faultyServerCreated);
        when(serverApi.create(eq("error_server"), anyString(), anyString(), any(CreateServerOptions.class))).thenReturn(errorServerCreated);

        //ImageApi
        org.jclouds.openstack.nova.v2_0.features.ImageApi novaImageApi = mock(org.jclouds.openstack.nova.v2_0.features.ImageApi.class);
        when(novaImageApi.get(definedImage.getExtId())).thenReturn(expImage);
        ImageApi imageApi = mock(ImageApi.class);
        ImageDetails imageDetails = new MyImageDetails(definedImage.getExtId(), definedImage.getName(), new HashSet<Link>(), ContainerFormat.fromValue(definedImage.getContainerFormat()), DiskFormat.fromValue(definedImage.getDiskFormat()), new Long(1), "", definedImage.getMinDiskSpace(), definedImage.getMinRam(), "", "", definedImage.getUpdated(), definedImage.getCreated(), new Date(), org.jclouds.openstack.glance.v1_0.domain.Image.Status.ACTIVE, definedImage.isPublic(), new HashMap<String, String>());
        Set<ImageDetails> imageSet = new HashSet<ImageDetails>();
        imageSet.add(imageDetails);
        ImmutableList<IterableWithMarker<ImageDetails>> imageILIWM = ImmutableList.of(IterableWithMarkers.from(imageSet));
        when(glanceApi.getImageApi(anyString())).thenReturn(imageApi);
        when(imageApi.get(definedImage.getExtId())).thenReturn(imageDetails);
        when(imageApi.get("not_existing_image_ext_id")).thenThrow(new NullPointerException());
        when(novaApi.getImageApi(anyString())).thenReturn(novaImageApi);
        when(imageApi.listInDetail()).thenReturn(mock(PagedIterable.class));
        //when(imageApi.listInDetail().toList()).thenReturn(imageILIWM);
        when(imageApi.list()).thenReturn(mock(PagedIterable.class));
        when(imageApi.list().concat()).thenReturn(resImageFI);
        when(imageApi.create(anyString(), any(Payload.class), any(CreateImageOptions.class))).thenReturn(imageDetails);
        when(imageApi.update(anyString(), any(UpdateImageOptions.class))).thenReturn(imageDetails);
        when(imageApi.delete(anyString())).thenReturn(true);

        //FlavorApi
        FlavorApi flavorApi = mock(FlavorApi.class);
        when(novaApi.getFlavorApi(anyString())).thenReturn(flavorApi);
        when(flavorApi.get(definedFlavor.getExtId())).thenReturn(expFlavor);
        when(flavorApi.get("not_existing_flavor_ext_id")).thenThrow(new NullPointerException());
        when(flavorApi.create(Matchers.<Flavor>anyObject())).thenReturn(expFlavor);
        when(flavorApi.list()).thenReturn(mock(PagedIterable.class));
        when(flavorApi.list().concat()).thenReturn(resFlavorFI);
        when(flavorApi.listInDetail()).thenReturn(mock(PagedIterable.class));
        when(flavorApi.listInDetail().concat()).thenReturn(flaFlavorFI);

        //NetworkApi
        NetworkApi networkApi = mock(NetworkApi.class);
        when(neutronApi.getNetworkApi(anyString())).thenReturn(networkApi);
        final org.jclouds.openstack.neutron.v2.domain.Network network = mock(org.jclouds.openstack.neutron.v2.domain.Network.class);
        when(networkApi.create(any(org.jclouds.openstack.neutron.v2.domain.Network.CreateNetwork.class))).thenReturn(network);
        when(networkApi.update(anyString(), any(org.jclouds.openstack.neutron.v2.domain.Network.UpdateNetwork.class))).thenReturn((network));
        when(networkApi.delete(anyString())).thenReturn(true);
        when(networkApi.get(definedNetwork.getExtId())).thenReturn(network);
        when(networkApi.list()).thenReturn(mock(PagedIterable.class));
        when(networkApi.list().concat()).thenReturn(FluentIterable.from(new ArrayList<org.jclouds.openstack.neutron.v2.domain.Network>() {{
            add(network);
        }}));
        when(network.getName()).thenReturn(definedNetwork.getName());
        when(network.getId()).thenReturn(definedNetwork.getExtId());
        when(network.getSubnets()).thenReturn(ImmutableSet.<String>of(definedSubnet.getExtId()));
        RouterApi routerApi = mock(RouterApi.class);
        when(neutronApi.getRouterApi(anyString())).thenReturn(mock(Optional.class));
        when(neutronApi.getRouterApi(anyString()).get()).thenReturn(routerApi);

        //SubnetApi
        SubnetApi subnetApi = mock(SubnetApi.class);
        when(neutronApi.getSubnetApi(anyString())).thenReturn(subnetApi);
        org.jclouds.openstack.neutron.v2.domain.Subnet subnet = mock(org.jclouds.openstack.neutron.v2.domain.Subnet.class);
        when(subnetApi.create(any(org.jclouds.openstack.neutron.v2.domain.Subnet.CreateSubnet.class))).thenReturn(subnet);
        when(subnetApi.update(anyString(), any(org.jclouds.openstack.neutron.v2.domain.Subnet.UpdateSubnet.class))).thenReturn((subnet));
        when(subnetApi.delete(anyString())).thenReturn(true);
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
    }

    @Test
    public void testSetZone() {
        openstackClient.setZone("");
        openstackClient.setZone("mocked");
    }

    @Test
    public void testLaunchInstance() {
        Server server = openstackClient.launchInstance(vimInstance, definedServer.getName(), definedServer.getImage().getExtId(), definedServer.getFlavor().getExtId(), "keypair", new HashSet<String>(), new HashSet<String>(), "#userdata");
        assertEqualsServers(definedServer, server);
        exception.expect(NullPointerException.class);
        server = openstackClient.launchInstance(vimInstance, "faulty_server", definedServer.getImage().getExtId(), definedServer.getFlavor().getExtId(), "keypair", new HashSet<String>(), new HashSet<String>(), "#userdata");

    }

    @Test
    public void testLauchInstanceAndWait() throws VimDriverException {
        Server server = openstackClient.launchInstanceAndWait(vimInstance, definedServer.getName(), definedServer.getImage().getExtId(), definedServer.getFlavor().getExtId(), "keypair", new HashSet<String>(), new HashSet<String>(), "#userdata");
        assertEqualsServers(definedServer, server);
        exception.expect(VimDriverException.class);
        server = openstackClient.launchInstanceAndWait(vimInstance, "error_server", definedServer.getImage().getExtId(), definedServer.getFlavor().getExtId(), "keypair", new HashSet<String>(), new HashSet<String>(), "#userdata");
    }

    @Test
    public void testLauchInstanceAndWaitFloatingIp() throws VimDriverException {
        Server server = openstackClient.launchInstanceAndWait(vimInstance, definedServer.getName(), definedServer.getImage().getExtId(), definedServer.getFlavor().getExtId(), "keypair", new HashSet<String>(), new HashSet<String>(), "#userdata", true);
        assertEqualsServers(definedServer, server);
    }

    @Test
    public void testListServer() {
        List<Server> servers = openstackClient.listServer(vimInstance);
        assertEqualsServers(definedServer, servers.get(0));
    }

    @Test
    public void deleteServerByIdAndWait() {
        doThrow(new NullPointerException()).when(openstackClient);
        openstackClient.deleteServerByIdAndWait(vimInstance, definedServer.getExtId());
    }

    @Test
    public void testRebootServer() {
        openstackClient.rebootServer(definedServer.getExtId(), RebootType.SOFT);
    }

    @Test
    public void testDeleteServerById() {
        openstackClient.deleteServerById(vimInstance, definedServer.getExtId());
    }

    @Test
    public void testDeleteServerByIdAndWait() {
        openstackClient.deleteServerByIdAndWait(vimInstance, "not_existing_server_ext_id");
    }

    @Test
    public void testAddImage() {
        NFVImage image = openstackClient.addImage(vimInstance, definedImage, "mocked_inputstream".getBytes());
        assertEqualsImages(image, definedImage);
    }

    @Test
    public void testUpdateImage() {
        NFVImage image = openstackClient.updateImage(vimInstance, definedImage);
        assertEqualsImages(image, definedImage);
    }

    @Test
    public void testDeleteImage() {
        boolean isDeleted = openstackClient.deleteImage(vimInstance, definedImage);
        Assert.assertEquals(true, isDeleted);
    }

    @Ignore
    @Test
    public void testListImages() {
        List<NFVImage> images = openstackClient.listImages(vimInstance);
        assertEqualsImages(definedImage, images.get(0));
    }

    @Test
    public void testCopyImage() {
        NFVImage image = openstackClient.copyImage(vimInstance, definedImage, new byte[0]);
        assertEqualsImages(image, definedImage);
    }

    @Test
    public void testAddFlavor() {
        DeploymentFlavour flavor = openstackClient.addFlavor(vimInstance, definedFlavor);
        assertEqualsFlavors(definedFlavor, flavor);
    }

    @Test
    public void testUpdateFlavor() throws VimDriverException {
        DeploymentFlavour flavor = openstackClient.updateFlavor(vimInstance, definedFlavor);
        assertEqualsFlavors(definedFlavor, flavor);
        exception.expect(VimDriverException.class);
        openstackClient.updateFlavor(vimInstance, new DeploymentFlavour());
    }

    @Test
    public void testListFlavors() {
        List<DeploymentFlavour> flavors = openstackClient.listFlavors(vimInstance);
        assertEqualsFlavors(definedFlavor, flavors.get(0));
    }

    @Test
    public void testCreateNetwork() {
        Network network = openstackClient.createNetwork(vimInstance, definedNetwork);
        assertEqualsNetworks(definedNetwork, network);
    }

    @Test
    public void testUpdateNetwork() {
        Network network = openstackClient.updateNetwork(vimInstance, definedNetwork);
        assertEqualsNetworks(definedNetwork, network);
    }

    @Test
    public void testDeleteNetwork() {
        boolean isDeleted = openstackClient.deleteNetwork(vimInstance, definedNetwork.getExtId());
        Assert.assertEquals(true, isDeleted);
        isDeleted = openstackClient.deleteNetwork(vimInstance, definedNetwork.getExtId());
        Assert.assertEquals(true, isDeleted);
    }

    @Test
    public void testGetNetworkById() {
        Network network = openstackClient.getNetworkById(vimInstance, definedNetwork.getExtId());
        assertEqualsNetworks(definedNetwork, network);
        exception.expect(NullPointerException.class);
        openstackClient.getNetworkById(vimInstance, "not_existing_id");
    }

    @Test
    public void testGetNetworkIdByName() {

    }

    @Test
    public void testGetSubnetsExtIds() {
        List<String> subnetExtIds = openstackClient.getSubnetsExtIds(vimInstance, definedNetwork.getExtId());
        Assert.assertEquals(subnetExtIds.get(0), definedSubnet.getExtId());
        exception.expect(NullPointerException.class);
        openstackClient.getSubnetsExtIds(vimInstance, "not_existing_id");

    }

    @Test
    public void testListNetworks() {
        List<Network> networks = openstackClient.listNetworks(vimInstance);
        assertEqualsNetworks(definedNetwork, networks.get(0));
    }

    @Test
    public void testCreateSubnet() {
        Subnet subnet = openstackClient.createSubnet(vimInstance, definedNetwork, definedSubnet);
        assertEqualsSubnets(definedSubnet, subnet);
    }

    @Test
    public void testUpdateSubnet() {
        Subnet subnet = openstackClient.updateSubnet(vimInstance, definedNetwork, definedSubnet);
        assertEqualsSubnets(definedSubnet, subnet);
    }

    @Test
    public void testDeleteSubnet() {
        boolean isDeleted = openstackClient.deleteSubnet(vimInstance, definedSubnet.getExtId());
        Assert.assertEquals(true, isDeleted);
    }

    @Test
    public void testListSubnets() {

    }

    @Test
    public void testGetType() {
        String type = openstackClient.getType(vimInstance);
        Assert.assertEquals("openstack", type);
    }

    @Ignore
    @Test
    public void testGetQuota() {
        Quota quota = openstackClient.getQuota(vimInstance);
        assertEqualsQuotas(definedQuota, quota);
    }

    private VimInstance createVimInstance() {
        VimInstance vimInstance = new VimInstance();
        vimInstance.setName("mock_vim_instance");
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
        server.setFloatingIp("mocked_floating_ip");
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
        Assert.assertEquals(expectedNetwork.getSubnets(), actualNetwork.getSubnets());
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
