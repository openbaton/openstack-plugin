package org.project.openbaton.clients.interfaces.client.amazon;

import org.project.openbaton.catalogue.mano.common.DeploymentFlavour;
import org.project.openbaton.catalogue.nfvo.*;
import org.project.openbaton.clients.interfaces.ClientInterfaces;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * Created by lto on 12/05/15.
 */
@Service
@Scope
public class AmazonClient implements ClientInterfaces {
    @Override
    public Server launchInstance(String name, String image, String flavor, String keypair, Set<String> network, Set<String> secGroup, String userData) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void init(VimInstance vimInstance) {

    }

    @Override
    public List<NFVImage> listImages() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Server> listServer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Network> listNetworks() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<DeploymentFlavour> listFlavors() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Server launchInstanceAndWait(String hostname, String image, String extId, String keyPair, Set<String> networks, Set<String> securityGroups, String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteServerByIdAndWait(String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Network createNetwork(Network network) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeploymentFlavour addFlavor(DeploymentFlavour deploymentFlavour) {
        throw new UnsupportedOperationException();
    }

    @Override
    public NFVImage addImage(NFVImage image, InputStream inputStream) {
        throw new UnsupportedOperationException();
    }

    @Override
    public NFVImage updateImage(NFVImage image) {
        throw new UnsupportedOperationException();
    }

    @Override
    public NFVImage copyImage(NFVImage image, InputStream inputStream) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean deleteImage(NFVImage image) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeploymentFlavour updateFlavor(DeploymentFlavour deploymentFlavour) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean deleteFlavor(String extId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Subnet createSubnet(Network createdNetwork, Subnet subnet) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Network updateNetwork(Network network) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Subnet updateSubnet(Network updatedNetwork, Subnet subnet) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getSubnetsExtIds(String network_extId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean deleteSubnet(String existingSubnetExtId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean deleteNetwork(String extId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Network getNetworkById(String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Quota getQuota() {
        throw new UnsupportedOperationException();
    }
}
