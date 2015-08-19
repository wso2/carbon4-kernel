/*
 *  Copyright (c) 2005-2009, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.wso2.carbon.registry.core.jdbc.dao;

import org.apache.axis2.context.MessageContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.registry.core.ResourceIDImpl;
import org.wso2.carbon.registry.core.ResourceImpl;
import org.wso2.carbon.registry.core.Tag;
import org.wso2.carbon.registry.core.dao.ResourceDAO;
import org.wso2.carbon.registry.core.dao.TagsDAO;
import org.wso2.carbon.registry.core.dataaccess.DAOManager;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.jdbc.DatabaseConstants;
import org.wso2.carbon.registry.core.jdbc.dataaccess.JDBCDatabaseTransaction;
import org.wso2.carbon.registry.core.jdbc.dataobjects.TaggingDO;
import org.wso2.carbon.registry.core.pagination.PaginationConstants;
import org.wso2.carbon.registry.core.pagination.PaginationContext;
import org.wso2.carbon.registry.core.session.CurrentSession;
import org.wso2.carbon.registry.core.pagination.PaginationUtils;
import org.wso2.carbon.registry.core.utils.RegistryUtils;
import org.wso2.carbon.utils.DBUtils;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An extension of {@link JDBCTagsDAO} implements {@link TagsDAO} to store tags on a JDBC-based
 * database, when versioning for tags has been enabled.
 */
public class JDBCTagsVersionDAO extends JDBCTagsDAO implements TagsDAO {

    private static final Log log = LogFactory.getLog(JDBCTagsVersionDAO.class);
    private ResourceDAO resourceDAO;
    private String enableApiPagination = PaginationConstants.ENABLE_API_PAGINATE;

    /**
     * Default constructor
     *
     * @param daoManager instance of the data access object manager.
     */
    public JDBCTagsVersionDAO(DAOManager daoManager) {
        super(daoManager);
        this.resourceDAO = daoManager.getResourceDAO();
    }

    /**
     * Method to persist a tag.
     *
     * @param resource the resource
     * @param userID   the id of the user who added the tag.
     * @param tagName  the name of tag to be persisted.
     *
     * @throws RegistryException if some error occurs while adding a tag
     */
    public void addTagging(String tagName, ResourceImpl resource, String userID)
            throws RegistryException {

        JDBCDatabaseTransaction.ManagedRegistryConnection conn =
                JDBCDatabaseTransaction.getConnection();
        PreparedStatement ps1 = null;
        PreparedStatement ps2 = null;
        PreparedStatement ps3 = null;
        ResultSet result = null;
        try {
            String sql1 =
                    "INSERT INTO REG_TAG (REG_TAG_NAME, REG_USER_ID, REG_TAGGED_TIME, " +
                            "REG_TENANT_ID) VALUES (?,?,?,?)";
            String sql2 = "SELECT MAX(REG_ID) FROM REG_TAG";
            long now = System.currentTimeMillis();

            String dbProductName = conn.getMetaData().getDatabaseProductName();
            boolean returnsGeneratedKeys = DBUtils.canReturnGeneratedKeys(dbProductName);
            if (returnsGeneratedKeys) {
                ps1 = conn.prepareStatement(sql1, new String[]{DBUtils
                        .getConvertedAutoGeneratedColumnName(dbProductName,
                                DatabaseConstants.ID_FIELD)});
            } else {
                ps1 = conn.prepareStatement(sql1);
            }
            ps1.setString(1, tagName);
            ps1.setString(2, userID);
            ps1.setDate(3, new Date(now));
            ps1.setInt(4, CurrentSession.getTenantId());
            if (returnsGeneratedKeys) {
                ps1.executeUpdate();
                result = ps1.getGeneratedKeys();
            } else {
                synchronized (ADD_TAG_LOCK) {
                    ps1.executeUpdate();
                    ps2 = conn.prepareStatement(sql2);
                    result = ps2.executeQuery();
                }
            }
            if (result.next()) {
                int tagId = result.getInt(1);
                String sql3 =
                        "INSERT INTO REG_RESOURCE_TAG (REG_TAG_ID, REG_VERSION, REG_TENANT_ID) " +
                                "VALUES(?,?,?)";
                ps3 = conn.prepareStatement(sql3);

                ps3.setInt(1, tagId);
                ps3.setLong(2, resource.getVersionNumber());
                ps3.setInt(3, CurrentSession.getTenantId());

                ps3.executeUpdate();
            }

        } catch (SQLException e) {

            String msg = "Failed to add tag " + tagName + " to resource " + resource.getPath() +
                    " by user " + userID + ". " + e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                try {
                    if (result != null) {
                        result.close();
                    }
                } finally {
                    try {
                        if (ps1 != null) {
                            ps1.close();
                        }
                    } finally {
                        try {
                            if (ps2 != null) {
                                ps2.close();
                            }
                        } finally {
                            if (ps3 != null) {
                                ps3.close();
                            }
                        }
                    }
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }
    }

    /**
     * Method to persist tags.
     *
     * @param resource   the resource
     * @param taggingDOs the tags to be persisted.
     *
     * @throws RegistryException if some error occurs while adding tags
     */
    public void addTaggings(ResourceImpl resource, TaggingDO[] taggingDOs)
            throws RegistryException {
        JDBCDatabaseTransaction.ManagedRegistryConnection conn =
                JDBCDatabaseTransaction.getConnection();
        long now = System.currentTimeMillis();

        for (TaggingDO taggingDO : taggingDOs) {
            PreparedStatement ps = null;
            PreparedStatement ps2 = null;
            ResultSet result = null;
            try {
                String sql =
                        "INSERT INTO REG_TAG (REG_TAG_NAME, REG_USER_ID, REG_TAGGED_TIME, " +
                                "REG_TENANT_ID) VALUES (?,?,?,?)";

                String dbProductName = conn.getMetaData().getDatabaseProductName();
                ps = conn.prepareStatement(sql, new String[]{DBUtils
                        .getConvertedAutoGeneratedColumnName(dbProductName,
                        "REG_ID")});
                ps.setString(1, taggingDO.getTagName());
                ps.setString(2, taggingDO.getTaggedUserName());
                ps.setDate(3, new Date(now));
                ps.setInt(4, CurrentSession.getTenantId());

                ps.executeUpdate();

                result = ps.getGeneratedKeys();
                if (result.next()) {
                    int tagId = result.getInt(1);
                    String sql2 =
                            "INSERT INTO REG_RESOURCE_TAG (REG_TAG_ID, REG_VERSION, " +
                                    "REG_TENANT_ID) VALUES(?,?,?)";
                    ps2 = conn.prepareStatement(sql2);

                    ps2.setInt(1, tagId);
                    ps2.setLong(2, resource.getVersionNumber());
                    ps2.setInt(3, CurrentSession.getTenantId());

                    ps2.executeUpdate();
                }
            } catch (SQLException e) {
                String msg =
                        "Failed to add tags to resource " + resource.getPath() + ". " +
                                e.getMessage();
                log.error(msg, e);
                throw new RegistryException(msg, e);
            } finally {
                // closing open prepared statements & result sets before moving on to next iteration
                try {
                    try {
                        if (result != null) {
                            result.close();
                        }
                    } finally {
                        try {
                            if (ps != null) {
                                ps.close();
                            }
                        } finally {
                            if (ps2 != null) {
                                ps2.close();
                            }
                        }
                    }
                } catch (SQLException ex) {
                    String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                    log.error(msg, ex);
                }
            }
        }
    }

    /**
     * Method to copy tags.
     *
     * @param fromResource the source resource.
     * @param toResource   the target resource.
     *
     * @throws RegistryException if some error occurs while copying tags
     */
    public void copyTags(ResourceImpl fromResource, ResourceImpl toResource)
            throws RegistryException {

        List<TaggingDO> tagList = getTagDOs(fromResource);

        addTaggings(toResource, tagList.toArray(new TaggingDO[tagList.size()]));
    }

    /**
     * Method to determine whether the given tag exists.
     *
     * @param resourceImpl the resource
     * @param userID       the id of the user who added the tag.
     * @param tagName      the name of tag to be persisted.
     *
     * @return whether the given tag exists.
     * @throws RegistryException if some error occurs while checking whether a tag exists.
     */
    public boolean taggingExists(String tagName, ResourceImpl resourceImpl, String userID)
            throws RegistryException {

        JDBCDatabaseTransaction.ManagedRegistryConnection conn =
                JDBCDatabaseTransaction.getConnection();

        ResultSet result = null;
        PreparedStatement ps = null;

        try {
            String sql =
                    "SELECT T.REG_ID FROM REG_TAG T, REG_RESOURCE_TAG RT WHERE " +
                            "LOWER(T.REG_TAG_NAME)=? AND T.REG_USER_ID =? AND " +
                            "T.REG_ID=RT.REG_TAG_ID AND RT.REG_VERSION=? AND T.REG_TENANT_ID=? " +
                            "AND RT.REG_TENANT_ID=?";
            ps = conn.prepareStatement(sql);
            if (tagName == null) {
                ps.setString(1, null);
            } else {
                ps.setString(1, tagName.toLowerCase());
            }
            ps.setString(2, userID);
            ps.setLong(3, resourceImpl.getVersionNumber());
            ps.setInt(4, CurrentSession.getTenantId());
            ps.setInt(5, CurrentSession.getTenantId());
            result = ps.executeQuery();

            boolean tagExists = false;
            if (result.next()) {
                tagExists = true;
            }

            return tagExists;

        } catch (SQLException e) {

            String msg = "Failed to check the existence of the tag " + tagName +
                    " on resource " + resourceImpl.getPath() + " by user " + userID + ". " +
                    e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                try {
                    if (result != null) {
                        result.close();
                    }
                } finally {
                    if (ps != null) {
                        ps.close();
                    }
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }

    }

    /**
     * Method to get the names of tags added to the given resource.
     *
     * @param resourceImpl the resource.
     *
     * @return array of tag names.
     * @throws RegistryException if an error occurs while getting the tag names.
     */
    public String[] getTags(ResourceImpl resourceImpl) throws RegistryException {

        JDBCDatabaseTransaction.ManagedRegistryConnection conn =
                JDBCDatabaseTransaction.getConnection();
        ResultSet results = null;
        PreparedStatement ps = null;
        try {
            String sql =
                    "SELECT T.REG_TAG_NAME FROM REG_TAG T, REG_RESOURCE_TAG RT WHERE " +
                            "T.REG_ID=RT.REG_TAG_ID AND RT.REG_VERSION=? AND T.REG_TENANT_ID=? " +
                            "AND RT.REG_TENANT_ID=?";
            ps = conn.prepareStatement(sql);
            ps.setLong(1, resourceImpl.getVersionNumber());
            ps.setInt(2, CurrentSession.getTenantId());
            ps.setInt(3, CurrentSession.getTenantId());
            results = ps.executeQuery();

            List<String> tagList = new ArrayList<String>();
            while (results.next()) {
                tagList.add(results.getString(DatabaseConstants.TAG_NAME_FIELD));
            }

            return tagList.toArray(new String[tagList.size()]);

        } catch (SQLException e) {

            String msg = "Failed to get tags associated with the resource path " +
                    resourceImpl.getPath() + ". " + e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                try {
                    if (results != null) {
                        results.close();
                    }
                } finally {
                    if (ps != null) {
                        ps.close();
                    }
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }
    }

    /**
     * Method to get the data objects of tags added to the given resource.
     *
     * @param resourceImpl the resource.
     *
     * @return list of tagging data objects.
     * @throws RegistryException if an error occurs while getting the tagging data objects.
     */
    public List<TaggingDO> getTagDOs(ResourceImpl resourceImpl) throws RegistryException {

        JDBCDatabaseTransaction.ManagedRegistryConnection conn =
                JDBCDatabaseTransaction.getConnection();
        ResultSet results = null;
        PreparedStatement ps = null;
        List<TaggingDO> tagList = new ArrayList<TaggingDO>();
        try {
            String sql =
                    "SELECT T.REG_TAG_NAME, T.REG_USER_ID, T.REG_TAGGED_TIME " +
                            "FROM REG_TAG T, REG_RESOURCE_TAG RT WHERE " +
                            "T.REG_ID=RT.REG_TAG_ID AND RT.REG_VERSION=? AND " +
                            "T.REG_TENANT_ID=? AND RT.REG_TENANT_ID=?";
            ps = conn.prepareStatement(sql);
            ps.setLong(1, resourceImpl.getVersionNumber());
            ps.setInt(2, CurrentSession.getTenantId());
            ps.setInt(3, CurrentSession.getTenantId());
            results = ps.executeQuery();

            while (results.next()) {
                TaggingDO taggingDO = new TaggingDO();
                taggingDO.setTagName(results.getString(1));
                taggingDO.setTaggedUserName(results.getString(2));
                taggingDO.setTaggedTime(results.getDate(3));
                tagList.add(taggingDO);
            }

            return tagList;

        } catch (SQLException e) {

            String msg = "Failed to get tags associated with the resource path " +
                    resourceImpl.getPath() + ". " + e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                try {
                    if (results != null) {
                        results.close();
                    }
                } finally {
                    if (ps != null) {
                        ps.close();
                    }
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }
    }

    /**
     * Method to obtain the list of paths having any of the given tags.
     *
     * @param tags the tags.
     *
     * @return a list of paths.
     * @throws RegistryException if an error occurs.
     */
    public List getPathsWithAnyTag(String[] tags) throws RegistryException {

        if (tags == null || tags.length == 0) {
            return null;
        }

        JDBCDatabaseTransaction.ManagedRegistryConnection conn =
                JDBCDatabaseTransaction.getConnection();

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("SELECT DISTINCT RT.REG_VERSION FROM " +
                "REG_RESOURCE_TAG RT, REG_TAG T WHERE RT.REG_TAG_ID=T.REG_ID ");
        stringBuilder.append(" AND (");
        for (int i = 0; i < tags.length; i++) {
            if (i > 0) {
                stringBuilder.append(" OR ");
            }
            stringBuilder.append("lower(T.REG_TAG_NAME)=?");
        }
        stringBuilder.append(") AND RT.REG_TENANT_ID=? AND T.REG_TENANT_ID=?");

        List<String> resourcePaths = new ArrayList<String>();

        ResultSet results = null;
        PreparedStatement s = null;
        try {
            s = conn.prepareStatement(stringBuilder.toString());

            int i;
            for (i = 0; i < tags.length; i++) {
                if (tags[i] == null) {
                    s.setString(i + 1 , tags[i]);
                } else {
                    s.setString(i + 1 , tags[i].toLowerCase());
                }
            }
            s.setInt(i + 1, CurrentSession.getTenantId());
            s.setInt(i + 2, CurrentSession.getTenantId());

            results = s.executeQuery();
            while (results.next()) {
                long version = results.getLong(DatabaseConstants.VERSION_FIELD);
                if (version > 0) {
                    String path = resourceDAO.getPath(version);
                    if (path != null) {
                        resourcePaths.add(path);
                    }
                }
            }

        } catch (SQLException e) {

            String msg = "Failed to resource paths with any of the tags " +
                    Arrays.toString(tags) + ". " + e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                try {
                    if (results != null) {
                        results.close();
                    }
                } finally {
                    if (s != null) {
                        s.close();
                    }
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }

        return resourcePaths;
    }

    /**
     * Method to get the number of tags added to the given resource, by the given name.
     *
     * @param resourceImpl the resource.
     * @param tag          the tag name
     *
     * @return the number of tags.
     * @throws RegistryException if an error occurred while getting the number of tags.
     */
    public long getTagCount(ResourceImpl resourceImpl, String tag) throws RegistryException {

        JDBCDatabaseTransaction.ManagedRegistryConnection conn =
                JDBCDatabaseTransaction.getConnection();

        ResultSet result = null;
        PreparedStatement ps = null;
        try {
            String sql =
                    "SELECT COUNT(T.REG_TAG_NAME) " +
                            "FROM REG_TAG T, REG_RESOURCE_TAG RT WHERE " +
                            "lower(T.REG_TAG_NAME)=? AND " +
                            "T.REG_ID=RT.REG_TAG_ID AND RT.REG_VERSION=? AND " +
                            "T.REG_TENANT_ID=? AND RT.REG_TENANT_ID=? " +
                            "GROUP BY RT.REG_VERSION";
            ps = conn.prepareStatement(sql);
            if (tag == null) {
                ps.setString(1, null);
            } else {
                ps.setString(1, tag.toLowerCase());
            }
            ps.setLong(2, resourceImpl.getVersionNumber());
            ps.setInt(3, CurrentSession.getTenantId());
            ps.setInt(4, CurrentSession.getTenantId());
            result = ps.executeQuery();

            long tagCount = 0;
            if (result.next()) {
                tagCount = result.getLong(1);
            }

            return tagCount;

        } catch (SQLException e) {

            String msg = "Failed to get tag count of tag " + tag +
                    " on resource " + resourceImpl.getPath() + ". " + e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                try {
                    if (result != null) {
                        result.close();
                    }
                } finally {
                    if (ps != null) {
                        ps.close();
                    }
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }
    }

    /**
     * Method to get tags added to the given resource, along with the count.
     *
     * @param resourceImpl the resource.
     *
     * @return an array of tags (with counts).
     * @throws RegistryException if an error occurred while getting tags.
     */
    public Tag[] getTagsWithCount(ResourceImpl resourceImpl) throws RegistryException {

        JDBCDatabaseTransaction.ManagedRegistryConnection conn =
                JDBCDatabaseTransaction.getConnection();

        try {
            String dbName = conn.getMetaData().getDatabaseProductName();
            if (dbName.contains("Microsoft") || dbName.equals("Oracle")) {
                enableApiPagination = "false";
            }
        } catch (SQLException e) {
            throw new RegistryException("Failed to get Database product name ", e);
        }

        List<Tag> tagList = new ArrayList<Tag>();
        ResultSet result = null;
        PreparedStatement ps = null;

        boolean paginated = false;
        int start = 0;
        int count = 0;
        String sortOrder ="";
        String sortBy  ="";
        MessageContext messageContext = null;
        //   enableApiPagination is the value of system property - enable.registry.api.paginating
        if (enableApiPagination == null || enableApiPagination.equals("true")) {
            messageContext = MessageContext.getCurrentMessageContext();
            if (messageContext != null && PaginationUtils.isPaginationHeadersExist(messageContext)) {

                PaginationContext paginationContext = PaginationUtils.initPaginationContext(messageContext);
                start = paginationContext.getStart();
                if(start == 0){
                    start =1;
                }
                count = paginationContext.getCount();
                sortBy = paginationContext.getSortBy();
                sortOrder = paginationContext.getSortOrder();
                paginated = true;
            }
        }
        try {
            String sql =
                    "SELECT T.REG_TAG_NAME, COUNT(T.REG_ID) FROM REG_TAG T, REG_RESOURCE_TAG RT " +
                            "WHERE RT.REG_VERSION=? AND T.REG_ID=RT.REG_TAG_ID AND " +
                            "T.REG_TENANT_ID=? AND RT.REG_TENANT_ID=? " +
                            "GROUP BY T.REG_TAG_NAME";

            if(paginated){
                if (!"".equals(sortBy) && !"".equals(sortOrder)) {
                    sql = sql + " ORDER BY " + sortBy + " " + sortOrder;

                }
            }
            if (enableApiPagination == null || enableApiPagination.equals("true")) {
                // TYPE_SCROLL_INSENSITIVE and CONCUR_UPDATABLE should be set to move the cursor through the resultSet
                ps = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
            } else {
                ps = conn.prepareStatement(sql);
            }
            ps.setLong(1, resourceImpl.getVersionNumber());
            ps.setInt(2, CurrentSession.getTenantId());
            ps.setInt(3, CurrentSession.getTenantId());

            result = ps.executeQuery();
            if (paginated) {
                //Check start index is a valid one
                if (result.relative(start)) {
                    //This is to get cursor to correct position to execute results.next().
                    result.previous();
                    int i = 0;
                    while (result.next() && i < count) {
                        i++;
                        tagList.add(getTag(result));
                    }
                } else {
                    log.debug("start index doesn't exist in the result set");
                }
                //move the cursor to the last index
                if (result.last()) {
                    log.debug("cursor move to the last index of result set");
                } else {
                    log.debug("cursor doesn't move to the last index of result set");
                }
                //set row count to the message context.
                PaginationUtils.setRowCount(messageContext, Integer.toString(result.getRow()));
            } else {
                while (result.next()) {
                    tagList.add(getTag(result));
                }
            }

        } catch (SQLException e) {

            String msg = "Failed to get tags and tag counts of the resource " +
                    resourceImpl.getPath() + ". " + e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                try {
                    if (result != null) {
                        result.close();
                    }
                } finally {
                    if (ps != null) {
                        ps.close();
                    }
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }

        return tagList.toArray(new Tag[tagList.size()]);
    }

    private Tag getTag(ResultSet result) throws SQLException {

        Tag tag = new Tag();
        tag.setTagName(result.getString(DatabaseConstants.TAG_NAME_FIELD));
        tag.setTagCount(result.getLong(2));
        return tag;
    }

    /**
     * Method to get a tagging added to a given resource by the given user.
     *
     * @param resource the resource.
     * @param tag      the name of the tag.
     * @param userID   the id of the user who added the tagging.
     *
     * @return the tagging data objects.
     * @throws RegistryException if an error occurs while getting the tagging.
     */
    public TaggingDO[] getTagging(ResourceImpl resource, String tag, String userID)
            throws RegistryException {

        JDBCDatabaseTransaction.ManagedRegistryConnection conn =
                JDBCDatabaseTransaction.getConnection();

        ResultSet result = null;
        PreparedStatement ps = null;

        List<TaggingDO> taggingDOs = new ArrayList<TaggingDO>();
        try {
            TaggingDO taggingDO;

            String sql = "SELECT T.REG_ID, T.REG_TAGGED_TIME FROM REG_TAG T, REG_RESOURCE_TAG RT " +
                    " WHERE RT.REG_VERSION = ? AND RT.REG_TAG_ID=T.REG_ID " +
                    " AND T.REG_TAG_NAME=? ";
            if (!userID.equals("*")) {
                sql = sql + "AND T.REG_USER_ID=? ";
            }
            sql = sql + " AND T.REG_TENANT_ID=? AND RT.REG_TENANT_ID=? ";
            ps = conn.prepareStatement(sql);
            ps.setLong(1, resource.getVersionNumber());
            ps.setString(2, tag);
            int nextParam = 3;
            if (!userID.equals("*")) {
                ps.setString(nextParam, userID);
                nextParam++;
            }
            ps.setInt(nextParam, CurrentSession.getTenantId());
            nextParam++;
            ps.setInt(nextParam, CurrentSession.getTenantId());

            result = ps.executeQuery();
            while (result.next()) {

                java.util.Date taggedTime = new java.util.Date(
                        result.getTimestamp(DatabaseConstants.TAGGED_TIME_FIELD).getTime());

                taggingDO = new TaggingDO();
                taggingDO.setResourcePath(resource.getPath());
                taggingDO.setTagName(tag);
                taggingDO.setTaggedTime(taggedTime);
                taggingDO.setTaggedUserName(userID);
                taggingDO.setTagID(result.getLong(DatabaseConstants.ID_FIELD));

                taggingDOs.add(taggingDO);
            }

            return taggingDOs.toArray(new TaggingDO[taggingDOs.size()]);

        } catch (SQLException e) {

            String msg = "Failed to get tagging information for tag " + tag +
                    " on resource " + resource.getPath() + " by user " + userID + ". " +
                    e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                try {
                    if (result != null) {
                        result.close();
                    }
                } finally {
                    if (ps != null) {
                        ps.close();
                    }
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }
    }

    /**
     * Method to get all taggings added to a given resource.
     *
     * @param resource the resource.
     *
     * @return the tagging data objects.
     * @throws RegistryException if an error occurs while getting the taggings.
     */
    public TaggingDO[] getTagging(ResourceImpl resource)
            throws RegistryException {

        JDBCDatabaseTransaction.ManagedRegistryConnection conn =
                JDBCDatabaseTransaction.getConnection();

        List<TaggingDO> taggingDOs = new ArrayList<TaggingDO>();
        ResultSet results = null;
        PreparedStatement ps = null;
        try {
            TaggingDO taggingDO;

            String sql = "SELECT T.REG_ID, T.REG_TAG_NAME, T.REG_USER_ID, " +
                    "T.REG_TAGGED_TIME FROM REG_TAG T, REG_RESOURCE_TAG RT " +
                    "WHERE RT.REG_VERSION =? AND RT.REG_TAG_ID=T.REG_ID AND " +
                    "T.REG_TENANT_ID=? AND RT.REG_TENANT_ID=?";
            ps = conn.prepareStatement(sql);
            ps.setLong(1, resource.getVersionNumber());
            ps.setInt(2, CurrentSession.getTenantId());
            ps.setInt(3, CurrentSession.getTenantId());


            results = ps.executeQuery();
            while (results.next()) {

                java.util.Date taggedTime = new java.util.Date(
                        results.getTimestamp(DatabaseConstants.TAGGED_TIME_FIELD).getTime());

                taggingDO = new TaggingDO();
                taggingDO.setResourcePath(resource.getPath());
                taggingDO.setTagName(results.getString(DatabaseConstants.TAG_NAME_FIELD));
                taggingDO.setTaggedTime(taggedTime);
                taggingDO.setTaggedUserName(results.getString(DatabaseConstants.USER_ID_FIELD));
                taggingDO.setTagID(results.getLong(DatabaseConstants.ID_FIELD));
                taggingDOs.add(taggingDO);
            }


        } catch (SQLException e) {

            String msg = "Failed to get tagging information for the resource " +
                    resource.getPath() + ". " + e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                try {
                    if (results != null) {
                        results.close();
                    }
                } finally {
                    if (ps != null) {
                        ps.close();
                    }
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }

        return taggingDOs.toArray(new TaggingDO[taggingDOs.size()]);
    }

    /**
     * Method to get a tagging by the given id.
     *
     * @param taggingID the id of the tagging.
     *
     * @return the tagging data object.
     * @throws RegistryException if an error occurs while getting the tagging.
     */
    public TaggingDO getTagging(long taggingID)
            throws RegistryException {

        JDBCDatabaseTransaction.ManagedRegistryConnection conn =
                JDBCDatabaseTransaction.getConnection();

        ResultSet result = null;
        PreparedStatement s = null;
        try {
            String sql = "SELECT T.REG_USER_ID, T.REG_TAG_NAME, T.REG_TAGGED_TIME, " +
                    "RT.REG_VERSION " +
                    "FROM REG_TAG T, REG_RESOURCE_TAG RT " +
                    "WHERE T.REG_ID=? AND T.REG_ID=RT.REG_TAG_ID AND T.REG_TENANT_ID=? " +
                    "AND RT.REG_TENANT_ID=?";

            s = conn.prepareStatement(sql);
            s.setLong(1, taggingID);
            s.setInt(2, CurrentSession.getTenantId());
            s.setInt(3, CurrentSession.getTenantId());

            TaggingDO taggingDO = null;
            result = s.executeQuery();
            if (result.next()) {

                java.util.Date taggedTime = new java.util.Date(
                        result.getTimestamp(DatabaseConstants.TAGGED_TIME_FIELD).getTime());

                taggingDO = new TaggingDO();
                // TODO
                //taggingDO.setResourceID(result.getString(DatabaseConstants.AID_FIELD));
                taggingDO.setTagName(result.getString(DatabaseConstants.TAG_NAME_FIELD));
                taggingDO.setTaggedUserName(result.getString(DatabaseConstants.USER_ID_FIELD));
                taggingDO.setTaggedTime(taggedTime);

                long version = result.getLong(DatabaseConstants.VERSION_FIELD);
                String resourcePath = null;
                if (version > 0) {
                    resourcePath = resourceDAO.getPath(version);
                }
                if (resourcePath != null) {
                    taggingDO.setResourcePath(resourcePath);
                }
            }

            return taggingDO;

        } catch (SQLException e) {

            String msg = "Failed to get tagging information for tag ID " +
                    taggingID + ". " + e.getMessage();
            log.error(msg, e);
            throw new RegistryException(msg, e);
        } finally {
            try {
                try {
                    if (result != null) {
                        result.close();
                    }
                } finally {
                    if (s != null) {
                        s.close();
                    }
                }
            } catch (SQLException ex) {
                String msg = RegistryConstants.RESULT_SET_PREPARED_STATEMENT_CLOSE_ERROR;
                log.error(msg, ex);
            }
        }
    }

    /**
     * Gets the resource with sufficient data to differentiate it from another resource. This would
     * populate a {@link ResourceImpl} with the <b>path</b>, <b>name</b> and <b>path identifier</b>
     * of a resource.
     *
     * @param path the path of the resource.
     *
     * @return the resource with minimum data.
     * @throws RegistryException if an error occurs while retrieving resource data.
     */
    public ResourceImpl getResourceWithMinimumData(String path) throws RegistryException {
        return RegistryUtils.getResourceWithMinimumData(path, resourceDAO, true);
    }

    /**
     * Method to move tags. This function is not applicable to versioned resources.
     *
     * @param source the source resource.
     * @param target the target resource.
     *
     * @throws RegistryException if some error occurs while moving tags
     */
    public void moveTags(ResourceIDImpl source, ResourceIDImpl target) throws RegistryException {
        // this is non-versioned specific function.
        // do nothing when the tags versioning on in configuration
    }

    /**
     * Method to move tag paths. This function is not applicable to versioned resources.
     *
     * @param source the source resource.
     * @param target the target resource.
     *
     * @throws RegistryException if some error occurs while moving tag paths
     */
    public void moveTagPaths(ResourceIDImpl source, ResourceIDImpl target)
            throws RegistryException {
        // this is non-versioned specific function.
        // do nothing when the tags versioning on in configuration
    }
}
