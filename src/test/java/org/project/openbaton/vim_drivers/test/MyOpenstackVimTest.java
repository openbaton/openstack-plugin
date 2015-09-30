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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.project.openbaton.catalogue.mano.common.DeploymentFlavour;
import org.project.openbaton.catalogue.mano.common.VNFDeploymentFlavour;
import org.project.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.project.openbaton.catalogue.mano.record.Status;
import org.project.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.project.openbaton.catalogue.nfvo.*;
import org.project.openbaton.clients.interfaces.client.openstack.OpenstackClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

//import org.project.openbaton.nfvo.common.exceptions.VimException;


/**
 * Created by mpa on 07.05.15.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@TestExecutionListeners({DependencyInjectionTestExecutionListener.class})
@TestPropertySource(properties = {"timezone = GMT", "port: 4242"})
public class MyOpenstackVimTest {

    @Autowired
    OpenstackClient openstackClient;

    VimInstance vimInstance;

    VirtualDeploymentUnit vdu;

    VirtualNetworkFunctionRecord vnfr;

    Network definedNetwork;

    Subnet definedSubnet;

    @Before
    public void init() {
        vimInstance = createVimInstance();
        vdu = createVDU();
        vdu.setVimInstance(vimInstance);
        vnfr = createVNFR();
        definedNetwork = createNetwork();
        definedSubnet = createSubnet();
        Set<Subnet> subnets = new HashSet<Subnet>();
        subnets.add(definedSubnet);
        definedNetwork.setSubnets(subnets);
    }

    @Test
    public void test_quota() {
        openstackClient.getQuota(vimInstance);
    }

    private VimInstance createVimInstance() {
        VimInstance vimInstance = new VimInstance();
        vimInstance.setName("mock_vim_instance");
        vimInstance.setUsername("nubomedia");
        vimInstance.setPassword("nub0m3d1@");
        vimInstance.setTenant("nubomedia");
        vimInstance.setAuthUrl("http://80.96.122.48:5000/v2.0");
        return vimInstance;
    }

    private VirtualNetworkFunctionRecord createVNFR() {
        VirtualNetworkFunctionRecord vnfr = new VirtualNetworkFunctionRecord();
        vnfr.setName("testVnfr");
        vnfr.setStatus(Status.INITIALIZED);
        vnfr.setAudit_log("audit_log");
        vnfr.setDescriptor_reference("test_dr");
        VNFDeploymentFlavour deployment_flavour = new VNFDeploymentFlavour();
        deployment_flavour.setFlavour_key("m1.small");
        vnfr.setDeployment_flavour_key("m1.small");
        return vnfr;
    }

    private VirtualDeploymentUnit createVDU() {
        VirtualDeploymentUnit vdu = new VirtualDeploymentUnit();
        vdu.setHostname("test_server");
        ArrayList<String> monitoring_parameter = new ArrayList<>();
        monitoring_parameter.add("parameter_1");
        monitoring_parameter.add("parameter_2");
        monitoring_parameter.add("parameter_3");
        //vdu.setMonitoring_parameter(monitoring_parameter);
        vdu.setComputation_requirement("computation_requirement");
        ArrayList<String> vm_images = new ArrayList<>();
        vm_images.add("cirros");
        //vdu.setVm_image(vm_images);
        return vdu;
    }

    private Network createNetwork() {
        Network network = new Network();
        network.setName("test_network");
        network.setExternal(false);
        network.setShared(false);
        return network;
    }

    private Subnet createSubnet() {
        Subnet subnet = new Subnet();
        subnet.setName("test_subnet");
        subnet.setCidr("192.168.123.0/24");
        return subnet;
    }

    private void assertEqualsServers(Server expectedServer, Server actualServer) {
        Assert.assertEquals(expectedServer.getName(), actualServer.getName());
        Assert.assertEquals(expectedServer.getExtId(), actualServer.getExtId());
        Assert.assertEquals(expectedServer.getStatus(), actualServer.getStatus());
        //Assert.assertEquals(expectedServer.getExtendedStatus(), actualServer.getExtendedStatus());
        //Assert.assertEquals(expectedServer.getCreated(), actualServer.getCreated());
        //Assert.assertEquals(expectedServer.getUpdated(), actualServer.getUpdated());
        //Assert.assertEquals(expectedServer.getIp(), actualServer.getIp());
    }

    private void assertEqualsImages(NFVImage expectedImage, NFVImage actualImage) {
        Assert.assertEquals(expectedImage.getName(), actualImage.getName());
        Assert.assertEquals(expectedImage.getExtId(), actualImage.getExtId());
        //Assert.assertEquals(expectedImage.getMinCPU(), actualImage.getMinCPU());
        Assert.assertEquals(expectedImage.getMinDiskSpace(), actualImage.getMinDiskSpace());
        Assert.assertEquals(expectedImage.getMinRam(), actualImage.getMinRam());
        Assert.assertEquals(expectedImage.getCreated(), actualImage.getCreated());
        Assert.assertEquals(expectedImage.getUpdated(), actualImage.getUpdated());
    }

    private void assertEqualsFlavors(DeploymentFlavour expectedFlavor, DeploymentFlavour actualFlavor) {
        Assert.assertEquals(expectedFlavor.getExtId(), actualFlavor.getExtId());
        Assert.assertEquals(expectedFlavor.getFlavour_key(), actualFlavor.getFlavour_key());
    }

    private void assertEqualsNetworks(Network expectedNetwork, Network actualNetwork) {
        Assert.assertEquals(expectedNetwork.getName(), actualNetwork.getName());
        Assert.assertEquals(expectedNetwork.isExternal(), actualNetwork.isExternal());
        Assert.assertEquals(expectedNetwork.isShared(), actualNetwork.isShared());
        Assert.assertEquals(expectedNetwork.getSubnets(), actualNetwork.getSubnets());
    }

    private void assertEqualsSubnets(Subnet expectedSubnet, Subnet actualSubnet) {
        Assert.assertEquals(expectedSubnet.getExtId(), actualSubnet.getExtId());
        Assert.assertEquals(expectedSubnet.getName(), actualSubnet.getName());
        Assert.assertEquals(expectedSubnet.getCidr(), actualSubnet.getCidr());
        Assert.assertEquals(expectedSubnet.getNetworkId(), actualSubnet.getNetworkId());
    }

}