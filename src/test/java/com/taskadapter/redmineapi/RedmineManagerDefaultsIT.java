package com.taskadapter.redmineapi;

import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.IssueCategory;
import com.taskadapter.redmineapi.bean.IssueRelation;
import com.taskadapter.redmineapi.bean.Project;
import com.taskadapter.redmineapi.bean.Version;
import com.taskadapter.redmineapi.internal.Transport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests default redmine manager values in a response. Tries to provides
 * behavior compatible with an XML version.
 */
public class RedmineManagerDefaultsIT {

	private static final Logger logger = LoggerFactory.getLogger(RedmineManagerIT.class);

    private static String projectKey;
    private static int projectId;
    private static IssueManager issueManager;
    private static ProjectManager projectManager;
	private static Transport transport;

	@BeforeAll
	public static void oneTimeSetUp() {
        TestConfig testConfig = new TestConfig();
		logger.info("Running redmine tests using: " + testConfig.getURI());
        RedmineManager mgr = IntegrationTestHelper.createRedmineManager();
        transport = mgr.getTransport();
        issueManager = mgr.getIssueManager();
        projectManager = mgr.getProjectManager();

        Project junitTestProject = new Project(transport, "test project", "test" + Calendar.getInstance().getTimeInMillis());


        try {
			Project createdProject = projectManager.createProject(junitTestProject);
			projectKey = createdProject.getIdentifier();
			projectId = createdProject.getId();
		} catch (Exception e) {
			logger.error("Exception while creating test project", e);
			fail("can't create a test project. " + e.getMessage());
		}
	}

	@AfterAll
	public static void oneTimeTearDown() {
		try {
			if (projectManager != null && projectKey != null) {
                projectManager.deleteProject(projectKey);
			}
		} catch (Exception e) {
			logger.error("Exception while deleting test project", e);
			fail("can't delete the test project '" + projectKey
					+ ". reason: " + e.getMessage());
		}
	}

	@Test
	public void testProjectDefaults() throws RedmineException {
		Project template = new Project(transport, "Test name", "key" + Calendar.getInstance().getTimeInMillis());
		Project result = template.create();
		try {
			assertNotNull(result.getId());
			assertEquals(template.getIdentifier(),
					result.getIdentifier());
			assertEquals("Test name", result.getName());
			assertEquals("", result.getDescription());
			assertEquals("", result.getHomepage());
			assertNotNull(result.getCreatedOn());
			assertNotNull(result.getUpdatedOn());
			assertNotNull(result.getTrackers());
			assertNull(result.getParentId());
		} finally {
            projectManager.deleteProject(result.getIdentifier());
		}
	}

	@Test
	public void testIssueDefaults() throws RedmineException {
		Issue result = new Issue(transport, projectId, "This is a subject")
				.setStartDate(null)
				.create();
		
		try {
			assertNotNull(result.getId());
			assertEquals("This is a subject", result.getSubject());
			assertNull(result.getParentId());
			assertNull(result.getEstimatedHours());
			/* result.getSpentHours() is NULL for Redmine 3.0.0 and is equal to "0.0" for Redmine 2.6.2
			* so we can't really check this because we don't know the Redmine version.
			* Ideally we would want something like
			* if (redmine.getVersion()>=3) {
			*     assertThat()...
			* } else {
			*     assertThat()...
			* }
			*/
			assertNull(result.getAssigneeId());
			assertNotNull(result.getPriorityText());
			assertNotNull(result.getPriorityId());
			assertEquals(Integer.valueOf(0), result.getDoneRatio());
			assertNotNull(result.getProjectId());
			assertNotNull(result.getAuthorId());
			assertNotNull(result.getAuthorName());
			assertNull(result.getStartDate());
			assertNull(result.getDueDate());
			assertNotNull(result.getTracker());
			assertThat(result.getDescription()).isNull();
			assertNotNull(result.getCreatedOn());
			assertNotNull(result.getUpdatedOn());
			assertNotNull(result.getStatusId());
			assertNotNull(result.getStatusName());
			assertNull(result.getTargetVersion());
			assertNull(result.getCategory());
			assertNull(result.getNotes());
			assertNotNull(result.getCustomFields());
			assertNotNull(result.getJournals());
			assertNotNull(result.getRelations());
			assertNotNull(result.getAttachments());
		} finally {
            result.delete();
		}
	}

	@Test
	public void issueWithStartDateNotSetGetsDefaultValue() throws RedmineException {
		Issue issue = new Issue(transport, projectId).setSubject("Issue with no start date set in code")
				.create();
		try {
			assertNotNull(issue.getStartDate());
		} finally {
			issue.delete();
		}
	}

	@Test
	public void issueWithStartDateSetToNullDoesNotGetDefaultValueForStartDate() throws RedmineException {
		Issue issue = new Issue(transport, projectId).setSubject("Issue with NULL start date")
				.setStartDate(null)
				.create();
		try {
			assertNull(issue.getStartDate());
		} finally {
			issue.delete();
		}
	}

	@Test
	public void testRelationDefaults() throws RedmineException {
		Issue issue1 = new Issue(transport, projectId, "this is a test")
				.create();
		// TODO why is not everything inside TRY? fix!
		try {
			Issue issue2 = new Issue(transport, projectId, "this is a test")
					.create();
			try {
				IssueRelation relation = new IssueRelation(transport, issue1.getId(), issue2.getId(), "blocks")
						.create();
				assertNotNull(relation.getId());
				assertEquals(issue1.getId(), relation.getIssueId());
				assertEquals(issue2.getId(), relation.getIssueToId());
				assertEquals("blocks", relation.getType());
				assertEquals(Integer.valueOf(0), relation.getDelay());
			} finally {
				issue2.delete();
			}
		} finally {
			issue1.delete();
		}
	}

	@Test
	public void testVersionDefaults() throws RedmineException {
		Version version = new Version(transport, projectId, "2.3.4.5").create();
		try {
			assertNotNull(version.getId());
			assertNotNull(version.getProjectId());
			assertEquals("2.3.4.5", version.getName());
			assertEquals("", version.getDescription());
			assertNotNull(version.getStatus());
			assertNull(version.getDueDate());
			assertNotNull(version.getCreatedOn());
			assertNotNull(version.getUpdatedOn());
		} finally {
			version.delete();
		}
	}

	@Test
	public void testCategoryDefaults() throws RedmineException {
		Project projectByKey = projectManager.getProjectByKey(projectKey);
		IssueCategory category = new IssueCategory(transport, projectByKey.getId(), "test name")
				.create();
		try {
			assertNotNull(category.getId());
			assertEquals("test name", category.getName());
			assertNotNull(category.getProjectId());
			assertNull(category.getAssigneeId());
		} finally {
			category.delete();
		}
	}
}
