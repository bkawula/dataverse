package edu.harvard.iq.dataverse.privateurl;

import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.DatasetVersion;
import edu.harvard.iq.dataverse.DvObject;
import edu.harvard.iq.dataverse.RoleAssignment;
import edu.harvard.iq.dataverse.authorization.Permission;
import edu.harvard.iq.dataverse.authorization.RoleAssignee;
import edu.harvard.iq.dataverse.authorization.users.PrivateUrlUser;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class PrivateUrlUtil {

    private static final Logger logger = Logger.getLogger(PrivateUrlUtil.class.getCanonicalName());

    /**
     * Use of this method should be limited to
     * RoleAssigneeServiceBean.getRoleAssignee, which is the centralized place
     * to return a RoleAssignee (which can be either a User or a Group) when all
     * you have is the string that is their identifier.
     *
     * @todo Consider using a new character such as "#" (as suggested by
     * Michael) as the unique namespace for PrivateUrlUser rather than ":" which
     * means "builtin". Before the introduction of the Private URL feature, the
     * prefix ":" was only used for a short list of unchanging
     * "predefinedRoleAssignees" which consisted of the groups
     * ":authenticated-users" and ":AllUsers" and the user ":guest". A
     * PrivateUrlUser is something of a different animal in that its identifier
     * will vary based on the dataset that it is associated with
     * (":privateUrl42" for dataset 42, for example). The prefix we're using now
     * is ":privateUrl". If we switch to "#" I guess we would just make it
     * "#42"? Or would it be "#privateUrl42?" See also getRoleAssignee in
     * RoleAssigneeServiceBean which is where the code would be cleaner if we
     * use "#".
     *
     * @param identifier The identifier is expected to start with the
     * PrivateUrlUser.PREFIX and end with a number for a dataset,
     * ":privateUrl42", for example. The ":" indicates that this is a "builtin"
     * role assignee. The number at the end of the identifier of a
     * PrivateUrlUser is all we have to associate the role assignee identifier
     * with a dataset. If we had the role assignment itself in our hands, we
     * would simply get the dataset id from RoleAssignment.getDefinitionPoint
     * and then use it to instantiate a PrivateUrlUser.
     *
     * @return A valid PrivateUrlUser (which like any User or Group is a
     * RoleAssignee) if a valid identifier is provided or null.
     */
    public static RoleAssignee identifier2roleAssignee(String identifier) {
        String[] parts = identifier.split(PrivateUrlUser.PREFIX);
        long datasetId;
        try {
            datasetId = new Long(parts[1]);
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException ex) {
            logger.fine("Could not find dataset id in '" + identifier + "': " + ex);
            return null;
        }
        return new PrivateUrlUser(datasetId);
    }

    /**
     * @todo If there is a use case for this outside the context of Private URL,
     * move this method to somewhere more centralized.
     */
    static Dataset getDatasetFromRoleAssignment(RoleAssignment roleAssignment) {
        if (roleAssignment == null) {
            return null;
        }
        DvObject dvObject = roleAssignment.getDefinitionPoint();
        if (dvObject == null) {
            return null;
        }
        if (dvObject instanceof Dataset) {
            return (Dataset) roleAssignment.getDefinitionPoint();
        } else {
            return null;
        }
    }

    /**
     * @return DatasetVersion if a draft or null.
     *
     * @todo If there is a use case for this outside the context of Private URL,
     * move this method to somewhere more centralized.
     */
    static public DatasetVersion getDraftDatasetVersionFromRoleAssignment(RoleAssignment roleAssignment) {
        if (roleAssignment == null) {
            return null;
        }
        Dataset dataset = getDatasetFromRoleAssignment(roleAssignment);
        if (dataset != null) {
            DatasetVersion latestVersion = dataset.getLatestVersion();
            if (latestVersion.isDraft()) {
                return latestVersion;
            }
        }
        logger.fine("Couldn't find draft, returning null");
        return null;
    }

    static public PrivateUrlUser getPrivateUrlUserFromRoleAssignment(RoleAssignment roleAssignment) {
        if (roleAssignment == null) {
            return null;
        }
        Dataset dataset = getDatasetFromRoleAssignment(roleAssignment);
        if (dataset != null) {
            PrivateUrlUser privateUrlUser = new PrivateUrlUser(dataset.getId());
            return privateUrlUser;
        }
        return null;
    }

    /**
     * @return PrivateUrlRedirectData or null.
     *
     * @todo Show the Exception to the user?
     */
    public static PrivateUrlRedirectData getPrivateUrlRedirectData(RoleAssignment roleAssignment) {
        PrivateUrlUser privateUrlUser = PrivateUrlUtil.getPrivateUrlUserFromRoleAssignment(roleAssignment);
        String draftDatasetPageToBeRedirectedTo = PrivateUrlUtil.getDraftDatasetPageToBeRedirectedTo(roleAssignment);
        try {
            return new PrivateUrlRedirectData(privateUrlUser, draftDatasetPageToBeRedirectedTo);
        } catch (Exception ex) {
            logger.info("Exception caught trying to instantiate PrivateUrlRedirectData: " + ex);
            return null;
        }
    }

    /**
     * Returns a relative URL or "UNKNOWN."
     */
    static String getDraftDatasetPageToBeRedirectedTo(RoleAssignment roleAssignment) {
        DatasetVersion datasetVersion = getDraftDatasetVersionFromRoleAssignment(roleAssignment);
        return getDraftUrl(datasetVersion);
    }

    /**
     * Returns a relative URL or "UNKNOWN."
     */
    static String getDraftUrl(DatasetVersion draft) {
        if (draft != null) {
            Dataset dataset = draft.getDataset();
            if (dataset != null) {
                String persistentId = dataset.getGlobalId();
                /**
                 * @todo Investigate why dataset.getGlobalId() yields the String
                 * "null:null/null" when I expect null value. This smells like a
                 * bug.
                 */
                if (!"null:null/null".equals(persistentId)) {
                    String relativeUrl = "/dataset.xhtml?persistentId=" + persistentId + "&version=DRAFT";
                    return relativeUrl;
                }
            }
        }
        return "UNKNOWN";
    }

    static PrivateUrl getPrivateUrlFromRoleAssignment(RoleAssignment roleAssignment, String dataverseSiteUrl) {
        if (dataverseSiteUrl == null) {
            logger.info("dataverseSiteUrl was null. Can not instantiate a PrivateUrl object.");
            return null;
        }
        Dataset dataset = PrivateUrlUtil.getDatasetFromRoleAssignment(roleAssignment);
        if (dataset != null) {
            PrivateUrl privateUrl = new PrivateUrl(roleAssignment, dataset, dataverseSiteUrl);
            return privateUrl;
        } else {
            return null;
        }
    }

    static PrivateUrlUser getPrivateUrlUserFromRoleAssignment(RoleAssignment roleAssignment, RoleAssignee roleAssignee) {
        if (roleAssignment != null) {
            if (roleAssignee instanceof PrivateUrlUser) {
                return (PrivateUrlUser) roleAssignee;
            }
        }
        return null;
    }

    /**
     * @return A list of the CamelCase "names" of required permissions, not the
     * human-readable equivalents.
     *
     * @todo Move this to somewhere more central.
     */
    public static List<String> getRequiredPermissions(CommandException ex) {
        List<String> stringsToReturn = new ArrayList<>();
        Map<String, Set<Permission>> map = ex.getFailedCommand().getRequiredPermissions();
        map.entrySet().stream().forEach((entry) -> {
            entry.getValue().stream().forEach((permission) -> {
                stringsToReturn.add(permission.name());
            });
        });
        return stringsToReturn;
    }

}