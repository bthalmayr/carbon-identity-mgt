/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.identity.mgt.store.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.identity.mgt.bean.Attribute;
import org.wso2.carbon.identity.mgt.bean.Domain;
import org.wso2.carbon.identity.mgt.bean.Group;
import org.wso2.carbon.identity.mgt.bean.User;
import org.wso2.carbon.identity.mgt.claim.Claim;
import org.wso2.carbon.identity.mgt.claim.MetaClaim;
import org.wso2.carbon.identity.mgt.claim.MetaClaimMapping;
import org.wso2.carbon.identity.mgt.domain.DomainManager;
import org.wso2.carbon.identity.mgt.exception.DomainException;
import org.wso2.carbon.identity.mgt.exception.GroupNotFoundException;
import org.wso2.carbon.identity.mgt.exception.IdentityStoreException;
import org.wso2.carbon.identity.mgt.exception.UserManagerException;
import org.wso2.carbon.identity.mgt.exception.UserNotFoundException;
import org.wso2.carbon.identity.mgt.internal.CarbonSecurityDataHolder;
import org.wso2.carbon.identity.mgt.model.GroupModel;
import org.wso2.carbon.identity.mgt.model.UserModel;
import org.wso2.carbon.identity.mgt.service.RealmService;
import org.wso2.carbon.identity.mgt.store.IdentityStore;
import org.wso2.carbon.identity.mgt.store.connector.IdentityStoreConnector;
import org.wso2.carbon.identity.mgt.user.UserManager;
import org.wso2.carbon.identity.mgt.util.IdentityMgtConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents a virtual identity store to abstract the underlying stores.
 *
 * @since 1.0.0
 */
public class IdentityStoreImpl implements IdentityStore {

    private static final Logger log = LoggerFactory.getLogger(IdentityStoreImpl.class);

    private DomainManager domainManager;

    private RealmService carbonRealmService;

    private UserManager userManager;

    @Override
    public void init(DomainManager domainManager)
            throws IdentityStoreException {

        this.domainManager = domainManager;
        carbonRealmService = CarbonSecurityDataHolder.getInstance().getCarbonRealmService();
        userManager = CarbonSecurityDataHolder.getInstance().getUserManager();

        if (log.isDebugEnabled()) {
            log.debug("Identity store successfully initialized.");
        }
    }


    @Override
    public Group getGroup(Claim claim, String domain) throws IdentityStoreException, GroupNotFoundException {
        return null;
    }

    @Override
    public List<Group> listGroups(int offset, int length) throws IdentityStoreException {
        return null;
    }

    @Override
    public List<Group> listGroups(int offset, int length, String domain) throws IdentityStoreException {
        return null;
    }

    @Override
    public List<Group> listGroups(Claim claim, int offset, int length) throws IdentityStoreException {
        return null;
    }

    @Override
    public List<Group> listGroups(Claim claim, int offset, int length, String domain) throws IdentityStoreException {
        return null;
    }

    @Override
    public List<Group> listGroups(MetaClaim metaClaim, String filterPattern, int offset, int length)
            throws IdentityStoreException {
        return null;
    }

    @Override
    public List<Group> listGroups(MetaClaim metaClaim, String filterPattern, int offset, int length, String domain)
            throws IdentityStoreException {
        return null;
    }

    @Override
    public User getUser(String userId) throws IdentityStoreException, UserNotFoundException {

        for (Domain domain : domainManager.getSortedDomains()) {


            for (IdentityStoreConnector identityStoreConnector : domain.getSortedIdentityStoreConnectors()) {

                String identityStoreConnectorId = identityStoreConnector.getIdentityStoreConnectorId();

                try {
                    String connectorUserId =
                            userManager.getConnectorUserId(userId, identityStoreConnectorId);

                    for (String attribute : identityStoreConnector.getIdentityStoreConfig().getUniqueAttributes()) {

                        try {
                            User.UserBuilder userBuilder =
                                    identityStoreConnector.getUserBuilder(attribute, connectorUserId);

                            return userBuilder.setUserId(userId)
                                    .setDomain(domain)
                                    .setIdentityStore(this)
                                    .setAuthorizationStore(carbonRealmService.getAuthorizationStore())
                                    .build();

                        } catch (UserNotFoundException e) { // not throwing since looping through all unique attributes
                            log.debug("A user with attribute " + attribute + " : " + connectorUserId +
                                    " was not found in connector " + identityStoreConnectorId);
                        }

                    }


                } catch (UserManagerException e) { // not throwing since looping through all connectors
                    if (log.isDebugEnabled()) {
                        log.debug("Couldn't find connect specific user Id for " + userId + " in connector " +
                                identityStoreConnectorId);
                    }
                }

            }

        }

        throw new UserNotFoundException("User with unique user Id " + userId + " was not found");

    }

    @Override
    public User getUser(String userId, String domain) throws IdentityStoreException, UserNotFoundException {
        return null;
    }

    @Override
    public User getUser(Claim claim) throws IdentityStoreException, UserNotFoundException {
        String claimValue = claim.getValue();

        String[] domainSplit = claimValue.split(IdentityMgtConstants.URL_SPLITTER, 2);


        // If domain is provided in claim value, retrieve user from the specific domain
        if (domainSplit.length == 2) {
            return getUser(claim, domainSplit[0]);
        }

        String claimURI = claim.getClaimURI();

        for (Domain domain : domainManager.getSortedDomains()) {

            if (domain.isClaimAvailable(claimURI)) {

                Map<String, List<MetaClaimMapping>> claimMappings = domain.getClaimMappings();

                for (IdentityStoreConnector identityStoreConnector : domain.getSortedIdentityStoreConnectors()) {

                    String identityStoreConnectorId = identityStoreConnector.getIdentityStoreConfig().getConnectorId();

                    List<String> uniqueAttributes =
                            identityStoreConnector.getIdentityStoreConfig().getUniqueAttributes();

                    for (MetaClaimMapping claimMapping : claimMappings.get(identityStoreConnectorId)) {

                        // Skip if the claim does not match or not a unique attribute
                        if (!claimMapping.getMetaClaim().getClaimURI().equals(claimURI) ||
                                !uniqueAttributes.contains(claimMapping.getAttributeName())) {
                            continue;
                        }

                        try {
                            User.UserBuilder userBuilder = identityStoreConnector
                                    .getUserBuilder(claimMapping.getAttributeName(), claimValue);

                            return userBuilder
                                    .setUserId(userManager.getUniqueUserId(userBuilder.getUserId(),
                                            identityStoreConnectorId))
                                    .setDomain(domain)
                                    .setIdentityStore(this)
                                    .setAuthorizationStore(carbonRealmService.getAuthorizationStore())
                                    .build();

                        } catch (UserNotFoundException e) {
                            // not throwing since looping through all unique attributes
                            if (log.isDebugEnabled()) {
                                log.debug("User for claim " + claimURI + " with value " + claimValue +
                                        " was not found in connector : " + identityStoreConnectorId);
                            }
                        } catch (UserManagerException e) {
                            throw new IdentityStoreException("Error retrieving a unique user id for user", e);
                        }

                    }
                }
            }
        }

        throw new UserNotFoundException("User for claim " + claimURI + " with value " + claimValue + " was not found");
    }

    @Override
    public User getUser(Claim claim, String domainName) throws IdentityStoreException, UserNotFoundException {

        String claimURI = claim.getClaimURI();
        String claimValue = claim.getValue();


        try {
            Domain domain = domainManager.getDomainFromDomainName(domainName);

            if (domain.isClaimAvailable(claimURI)) {

                String[] domainSplit = claimValue.split(IdentityMgtConstants.URL_SPLITTER, 2);

                String domainUnawareClaimValue;
                // If domain is provided in claim value, retrieve user from the specific domain
                if (domainSplit.length == 2) {
                    domainUnawareClaimValue = domainSplit[1];
                } else {
                    domainUnawareClaimValue = claimValue;
                }

                Map<String, List<MetaClaimMapping>> metaClaimMappings = domain.getClaimMappings();

                for (IdentityStoreConnector identityStoreConnector : domain.getSortedIdentityStoreConnectors()) {

                    String identityStoreConnectorId = identityStoreConnector.getIdentityStoreConfig().getConnectorId();


                    List<String> uniqueAttributes =
                            identityStoreConnector.getIdentityStoreConfig().getUniqueAttributes();

                    for (MetaClaimMapping claimMapping : metaClaimMappings.get(identityStoreConnectorId)) {

                        // Skip if the claim does not match or not a unique attribute
                        if (!claimMapping.getMetaClaim().getClaimURI().equals(claimURI) ||
                                !uniqueAttributes.contains(claimMapping.getAttributeName())) {
                            continue;
                        }

                        try {
                            User.UserBuilder userBuilder = identityStoreConnector
                                    .getUserBuilder(claimMapping.getAttributeName(), domainUnawareClaimValue);

                            return userBuilder
                                    .setUserId(userManager.getUniqueUserId(
                                            userBuilder.getUserId(), identityStoreConnectorId))
                                    .setDomain(domain)
                                    .setIdentityStore(this)
                                    .setAuthorizationStore(carbonRealmService.getAuthorizationStore())
                                    .build();

                        } catch (UserNotFoundException e) {
                            // not throwing since looping through all connectors
                            if (log.isDebugEnabled()) {
                                log.debug("User for claim " + claimURI + " with value " + claimValue +
                                        " was not found in connector : " + identityStoreConnectorId);
                            }
                        }

                    }
                }
            }
        } catch (DomainException e) {
            throw new IdentityStoreException("Invalid domain information is provided to retrieve user", e);
        } catch (UserManagerException e) {
            throw new IdentityStoreException("Error retrieving a unique user id for user", e);
        }

        throw new UserNotFoundException("User for claim " + claimURI + " with value " + claimValue + " was not found");
    }

    @Override
    public List<User> listUsers(int offset, int length) throws IdentityStoreException {
        return null;
    }

    @Override
    public List<User> listUsers(int offset, int length, String domain) throws IdentityStoreException {
        return null;
    }

    @Override
    public List<User> listUsers(Claim claim, int offset, int length) throws IdentityStoreException {

        List<User> users = new ArrayList<>();

        String claimURI = claim.getClaimURI();
        String claimValue = claim.getValue();

        int currentOffset = 0;
        int currentCount = 0;

        for (Domain domain : domainManager.getSortedDomains()) {

            Map<String, List<MetaClaimMapping>> metaClaimMappings = domain.getClaimMappings();


            for (IdentityStoreConnector identityStoreConnector : domain.getSortedIdentityStoreConnectors()) {

                String identityStoreConnectorId = identityStoreConnector.getIdentityStoreConfig().getConnectorId();

                for (MetaClaimMapping metaClaimMapping :
                        metaClaimMappings.get(identityStoreConnector.getIdentityStoreConnectorId())) {

                    // Required number of users have been retrieved
                    if (currentCount >= length) {
                        break;
                    }

                    if (metaClaimMapping.getMetaClaim().getClaimURI().equals(claimURI)) {

                        List<User.UserBuilder> userBuilderList =
                                identityStoreConnector.getUserBuilderList(metaClaimMapping.getAttributeName(),
                                        claimValue, offset, length - currentCount);

                        for (User.UserBuilder userBuilder : userBuilderList) {

                            if (currentOffset < offset) {
                                currentOffset++;
                                continue;
                                // Skip all before the offset
                            }

                            try {
                                userBuilder.setUserId(userManager.getUniqueUserId(userBuilder.getUserId(),
                                        identityStoreConnectorId));

                                userBuilder.setIdentityStore(this);
                                userBuilder.setAuthorizationStore(carbonRealmService.getAuthorizationStore());
                                userBuilder.setDomain(domain);

                                users.add(userBuilder.build());

                                currentCount++;
                            } catch (UserManagerException e) {
                                // not throwing since looping through all connectors
                                throw new IdentityStoreException("Error retrieving unique user Id for user " +
                                        userBuilder.getUserId(), e);
                            }
                        }
                    }
                }

            }
        }

        return users;
    }

    @Override
    public List<User> listUsers(Claim claim, int offset, int length, String domain) throws IdentityStoreException {
        return null;
    }

    @Override
    public List<User> listUsers(MetaClaim metaClaim, String filterPattern, int offset, int length)
            throws IdentityStoreException {
        return null;
    }

    @Override
    public List<User> listUsers(MetaClaim metaClaim, String filterPattern, int offset, int length, String domain)
            throws IdentityStoreException {
        return null;
    }

    @Override
    public Group getGroup(String groupName) throws IdentityStoreException, GroupNotFoundException {

        Claim claim = new Claim();
        claim.setDialectURI(""); // TODO: Set the dialect URI for the primary attribute.
        claim.setClaimURI(""); // TODO: Set the URI for the primary attribute.
        claim.setValue(groupName);

        return getGroup(claim);
    }

    @Override
    public Group getGroup(String groupId, String domain) throws IdentityStoreException, GroupNotFoundException {
        return null;
    }

    @Override
    public Group getGroup(Claim claim) throws IdentityStoreException, GroupNotFoundException {

        String claimURI = claim.getClaimURI();
        String claimValue = claim.getValue();


        for (Domain domain : domainManager.getSortedDomains()) {

            Map<String, List<MetaClaimMapping>> claimMappings = domain.getClaimMappings();

            for (IdentityStoreConnector identityStoreConnector : domain.getSortedIdentityStoreConnectors()) {

                String identityStoreConnectorId = identityStoreConnector.getIdentityStoreConfig().getConnectorId();

                for (MetaClaimMapping metaClaimMapping :
                        claimMappings.get(identityStoreConnector.getIdentityStoreConnectorId())) {

                    if (metaClaimMapping.getMetaClaim().getClaimURI().equals(claimURI)) {

                        try {
                            Group.GroupBuilder groupBuilder = identityStoreConnector
                                    .getGroupBuilder(metaClaimMapping.getAttributeName(), claimValue);

                            groupBuilder.setDomain(domain);

                            return groupBuilder.build();

                        } catch (GroupNotFoundException e) {
                            // not throwing since looping through all connectors
                            if (log.isDebugEnabled()) {
                                log.debug("Group " + claimValue + " not found in identity store connector" +
                                        identityStoreConnectorId);
                            }
                        }
                    }
                }
            }
        }

        throw new GroupNotFoundException(String.format("Group not found for claim %s : %s", claimURI, claimValue));
    }

    @Override
    public List<Group> getGroupsOfUser(String userId) throws IdentityStoreException {

        List<Group> groupList = new ArrayList<>();

        try {
            User user = getUser(userId);


            Domain domain = user.getDomain();

            for (IdentityStoreConnector identityStoreConnector : domain.getSortedIdentityStoreConnectors()) {

                String identityStoreConnectorId = identityStoreConnector.getIdentityStoreConfig().getConnectorId();

                try {

                    String connectorUserId =
                            userManager.getConnectorUserId(userId, identityStoreConnectorId);

                    for (Group.GroupBuilder groupBuilder :
                            identityStoreConnector.getGroupBuildersOfUser(connectorUserId)) {

                        Group group = groupBuilder
                                .setDomain(domain)
                                .setGroupId(userManager.getUniqueUserId(groupBuilder.getGroupId(),
                                        identityStoreConnectorId))
                                .build();

                        groupList.add(group);
                    }

                } catch (UserManagerException e) {
                    // not throwing since looping through all connectors
                    throw new IdentityStoreException("Error resolving globally unique Id", e);
                }
            }

            return groupList;
        } catch (UserNotFoundException e) {
            throw new IdentityStoreException("User with Id " + userId + " was not found to retrieve groups", e);
        }
    }

    @Override
    public List<User> getUsersOfGroup(String groupID) throws IdentityStoreException {
        // TODO: implement
        return null;
    }

    @Override
    public List<Group> getGroupsOfUser(String userId, String domain) throws IdentityStoreException {
        return null;
    }

    @Override
    public List<User> getUsersOfGroup(String groupId, String domain) throws IdentityStoreException {
        return null;
    }

    @Override
    public boolean isUserInGroup(String userId, String groupId) throws IdentityStoreException {

        try {
            User user = getUser(userId);

            for (IdentityStoreConnector identityStoreConnector : user.getDomain().getSortedIdentityStoreConnectors()) {

                String identityStoreConnectorId = identityStoreConnector.getIdentityStoreConnectorId();

                try {

                    String connectorUserId =
                            userManager.getConnectorUserId(userId, identityStoreConnectorId);

                    if (identityStoreConnector.isUserInGroup(connectorUserId, groupId)) {
                        return true;
                    }
                } catch (UserManagerException e) {
                    // not throwing since looping through all connectors
                    if (log.isDebugEnabled()) {
                        log.debug("User " + userId + " is not mapped to connector " + identityStoreConnectorId);
                    }
                }
            }
        } catch (UserNotFoundException e) {
            throw new IdentityStoreException("User for userId " + userId + " was not found to validate groups", e);
        }

        return false;
    }

    @Override
    public boolean isUserInGroup(String userId, String groupId, String domain) throws IdentityStoreException {
        return false;
    }

    @Override
    public List<Claim> getClaims(User user) throws IdentityStoreException {
        List<Claim> claims = new ArrayList<>();
        Domain domain = user.getDomain();

        Map<String, List<MetaClaimMapping>> claimMappings = domain.getClaimMappings();

        for (IdentityStoreConnector identityStoreConnector : domain.getSortedIdentityStoreConnectors()) {

            String identityStoreConnectorId = identityStoreConnector.getIdentityStoreConfig().getConnectorId();
            List<MetaClaimMapping> metaClaimMappings = claimMappings.get(identityStoreConnectorId);

            // Create <AttributeName, MetaClaim> map
            Map<String, MetaClaim> attributeMapping = metaClaimMappings.stream()
                    .collect(Collectors.toMap(MetaClaimMapping::getAttributeName, MetaClaimMapping::getMetaClaim));

            try {
                String connectorUserId = userManager.getConnectorUserId(user.getUserId(), identityStoreConnectorId);

                List<Attribute> attributeValues = identityStoreConnector.getUserAttributeValues(connectorUserId,
                        new ArrayList<>(attributeMapping.keySet()));
                claims.addAll(buildClaims(attributeValues, attributeMapping));
            } catch (IdentityStoreException | UserManagerException e) {
                throw new IdentityStoreException("Error retrieving claims for user : " + user.getUserId(), e);
            }
        }

        return claims;
    }

    @Override
    public List<Claim> getClaims(User user, List<String> claimURIs) throws IdentityStoreException {
        List<Claim> claims = new ArrayList<>();
        Domain domain = user.getDomain();

        Map<String, List<MetaClaimMapping>> claimMappings = domain.getClaimMappings();

        for (IdentityStoreConnector identityStoreConnector : domain.getSortedIdentityStoreConnectors()) {

            String identityStoreConnectorId = identityStoreConnector.getIdentityStoreConfig().getConnectorId();

            List<MetaClaimMapping> metaClaimMappings = claimMappings.get(identityStoreConnectorId);

            // Create <AttributeName, MetaClaim> map
            Map<String, MetaClaim> attributeMapping = metaClaimMappings.stream().
                    filter(metaClaimMapping -> claimURIs.contains(metaClaimMapping.getMetaClaim().getClaimURI()))
                    .collect(Collectors.toMap(MetaClaimMapping::getAttributeName, MetaClaimMapping::getMetaClaim));

            try {
                String connectorUserId = userManager.getConnectorUserId(user.getUserId(), identityStoreConnectorId);

                List<Attribute> attributeValues = identityStoreConnector.getUserAttributeValues(connectorUserId,
                        new ArrayList<>(attributeMapping.keySet()));
                claims.addAll(buildClaims(attributeValues, attributeMapping));
            } catch (IdentityStoreException | UserManagerException e) {
                throw new IdentityStoreException("Error retrieving claims for user : " + user.getUserId(), e);
            }
        }

        if (claims.size() < claimURIs.size()) {
            log.warn("Some of the requested claims for the user " + user.getUserId() + " could not be found");
        }

        return claims;
    }

    @Override
    public User addUser(UserModel user) throws IdentityStoreException {
        return null;
    }

    @Override
    public User addUser(UserModel user, String domain) throws IdentityStoreException {
        return null;
    }

    @Override
    public List<User> addUsers(List<UserModel> users) throws IdentityStoreException {
        return null;
    }

    @Override
    public List<User> addUsers(List<UserModel> users, String domain) throws IdentityStoreException {
        return null;
    }

    @Override
    public void updateUserClaims(String userId, List<Claim> userClaims) throws IdentityStoreException {

    }

    @Override
    public void updateUserClaims(String userId, List<Claim> userClaimsToAdd, List<Claim> userClaimsToRemove) throws
            IdentityStoreException {

    }

    @Override
    public void deleteUser(String userId) throws IdentityStoreException {

    }

    @Override
    public void updateGroupsOfUser(String userId, List<String> groupIds) throws IdentityStoreException {

    }

    @Override
    public void updateGroupsOfUser(String userId, List<String> groupIdsToAdd, List<String> groupIdsToRemove) throws
            IdentityStoreException {

    }

    @Override
    public Group addGroup(GroupModel groupModel) throws IdentityStoreException {
        return null;
    }

    @Override
    public Group addGroup(GroupModel groupModel, String domain) throws IdentityStoreException {
        return null;
    }

    @Override
    public List<Group> addGroups(List<GroupModel> groups) throws IdentityStoreException {
        return null;
    }

    @Override
    public List<Group> addGroups(List<GroupModel> groups, String domain) throws IdentityStoreException {
        return null;
    }

    @Override
    public void updateGroupClaims(String groupId, List<Claim> groupClaims) throws IdentityStoreException {

    }

    @Override
    public void updateGroupClaims(String groupId, List<Claim> groupClaimsToAdd, List<Claim> groupClaimsToRemove)
            throws IdentityStoreException {

    }

    @Override
    public void deleteGroup(String groupId) throws IdentityStoreException {

    }

    @Override
    public void updateUsersOfGroup(String groupId, List<String> userIds) throws IdentityStoreException {

    }

    @Override
    public void updateUsersOfGroup(String groupId, List<String> userIdsToAdd, List<String> userIdsToRemove) throws
            IdentityStoreException {

    }

    /**
     * Build Claim Objects from attribute values.
     *
     * @param attributes Attributes with populated values
     * @param attributeMapping Attribute to MetaClaim mappings for the requried claims
     * @return Claims built from attribute values
     */
    private List<Claim> buildClaims(List<Attribute> attributes, Map<String, MetaClaim> attributeMapping) {

        return attributes.stream().map(attribute -> {
            MetaClaim metaClaim = attributeMapping.get(attribute.getAttributeName());
            Claim claim = new Claim();
            claim.setClaimURI(metaClaim.getClaimURI());
            claim.setDialectURI(metaClaim.getDialectURI());
            claim.setValue(attribute.getAttributeValue());
            return claim;
        }).collect(Collectors.toList());
    }
}
