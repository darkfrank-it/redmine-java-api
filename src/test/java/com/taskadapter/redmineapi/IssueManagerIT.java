package com.taskadapter.redmineapi;

import com.taskadapter.redmineapi.bean.*;
import com.taskadapter.redmineapi.internal.RequestParam;
import com.taskadapter.redmineapi.internal.ResultsWrapper;
import com.taskadapter.redmineapi.internal.Transport;
import org.apache.hc.client5.http.classic.HttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.taskadapter.redmineapi.CustomFieldResolver.getCustomFieldByName;
import static com.taskadapter.redmineapi.IssueHelper.createIssue;
import static com.taskadapter.redmineapi.IssueHelper.createIssues;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;


public class IssueManagerIT {

    private static IssueManager issueManager;
    private static ProjectManager projectManager;
    private static Project project;
    private static int projectId;
    private static String projectKey;
    private static Project project2;
    private static String projectKey2;
    private static RedmineManager mgr;
    private static UserManager userManager;
    private static Group demoGroup;
    private static Transport transport;
    private static User ourUser;

    @BeforeAll
    public static void oneTimeSetup() throws RedmineException {
        mgr = IntegrationTestHelper.createRedmineManagerWithAPIKey();
        transport = mgr.getTransport();
        userManager = mgr.getUserManager();
        issueManager = mgr.getIssueManager();
        projectManager = mgr.getProjectManager();
        project = IntegrationTestHelper.createProject(transport);
        projectId = project.getId();
        projectKey = project.getIdentifier();
        project2 = IntegrationTestHelper.createProject(transport);
        projectKey2 = project2.getIdentifier();
        demoGroup = new Group(transport).setName("Group" + System.currentTimeMillis())
                .create();
        // Add membership of group for the demo projects
        Collection<Role> allRoles = Arrays.asList(new Role().setId(3), // Manager
                new Role().setId(4), // Developer
                new Role().setId(5)  // Reporter
        );

        new Membership(transport, project, demoGroup.getId())
                .addRoles(allRoles)
                .create();

        new Membership(transport, project2, demoGroup.getId())
                .addRoles(allRoles)
                .create();

        ourUser = IntegrationTestHelper.getOurUser(transport);
    }

    @AfterAll
    public static void oneTimeTearDown() throws RedmineException {
        project.delete();
        project2.delete();
        demoGroup.delete();
    }

    @Test
    public void issueCreated() throws RedmineException {

        Calendar startCal = Calendar.getInstance();
        // have to clear them because they are ignored by Redmine and
        // prevent from comparison later
        startCal.clear(Calendar.HOUR_OF_DAY);
        startCal.clear(Calendar.MINUTE);
        startCal.clear(Calendar.SECOND);
        startCal.clear(Calendar.MILLISECOND);

        startCal.add(Calendar.DATE, 5);

        Calendar due = Calendar.getInstance();
        due.add(Calendar.MONTH, 1);

        String description = """
                This is the description for the new task.
                
                It has several lines.
                This is the last line.""";
        float estimatedHours = 44;

        Issue newIssue = new Issue(transport, projectId).setSubject("test zzx")
                .setStartDate(startCal.getTime())
                .setDueDate(due.getTime())
                .setAssigneeId(ourUser.getId())
                .setDescription(description)
                .setEstimatedHours(estimatedHours)
                .create();

        assertNotNull(newIssue, "Checking returned result");
        assertNotNull(newIssue.getId(), "New issue must have some ID");

        // check startDate
        Calendar returnedStartCal = Calendar.getInstance();
        returnedStartCal.setTime(newIssue.getStartDate());

        assertEquals(startCal.get(Calendar.YEAR), returnedStartCal.get(Calendar.YEAR));
        assertEquals(startCal.get(Calendar.MONTH), returnedStartCal.get(Calendar.MONTH));
        assertEquals(startCal.get(Calendar.DAY_OF_MONTH), returnedStartCal.get(Calendar.DAY_OF_MONTH));

        // check dueDate
        Calendar returnedDueCal = Calendar.getInstance();
        returnedDueCal.setTime(newIssue.getDueDate());

        assertEquals(due.get(Calendar.YEAR), returnedDueCal.get(Calendar.YEAR));
        assertEquals(due.get(Calendar.MONTH), returnedDueCal.get(Calendar.MONTH));
        assertEquals(due.get(Calendar.DAY_OF_MONTH), returnedDueCal.get(Calendar.DAY_OF_MONTH));

        // check ASSIGNEE
        assertThat(newIssue.getAssigneeId()).isEqualTo(ourUser.getId());

        // check AUTHOR
        Integer EXPECTED_AUTHOR_ID = IntegrationTestHelper.getOurUser(transport).getId();
        assertEquals(EXPECTED_AUTHOR_ID, newIssue.getAuthorId());

        // check ESTIMATED TIME
        assertEquals((Float) estimatedHours,
                newIssue.getEstimatedHours());

        // check multi-line DESCRIPTION
        String regexpStripExtra = "\\r|\\n|\\s";
        description = description.replaceAll(regexpStripExtra, "");
        String actualDescription = newIssue.getDescription();
        actualDescription = actualDescription.replaceAll(regexpStripExtra,
                "");
        assertEquals(description, actualDescription);

        // PRIORITY
        assertNotNull(newIssue.getPriorityId());
        assertTrue(newIssue.getPriorityId() > 0);
    }

    @Test
    public void issueWithParentCreated() throws RedmineException {
        Issue parentIssue = new Issue(transport, projectId)
                .setSubject("parent 1")
                .create();

        assertNotNull( parentIssue, "Checking parent was created");
        assertNotNull( parentIssue.getId(), "Checking ID of parent issue is not null");

        Issue childIssue = new Issue(transport, projectId)
                .setSubject("child 1")
                .setParentId(parentIssue.getId())
                .create();

        assertEquals(parentIssue.getId(), childIssue.getParentId(), "Checking parent ID of the child issue");
    }

    /**
     * Regression test for https://github.com/taskadapter/redmine-java-api/issues/117
     */
    @Test
    public void parentIdCanBeErased() throws RedmineException {
        Issue parentIssue = new Issue(transport, projectId).setSubject("parent task")
                .create();
        Integer parentId = parentIssue.getId();

        Issue childIssue = new Issue(transport, projectId).setSubject("child task")
                .setParentId(parentId)
                .create();
        assertThat(childIssue.getParentId()).isEqualTo(parentId);

        childIssue.setParentId(null)
                .update();

        final Issue reloadedIssue = issueManager.getIssueById(childIssue.getId());
        assertThat(reloadedIssue.getParentId()).isNull();
    }

    @Test
    public void testUpdateIssue() throws RedmineException {
        String originalSubject = "Issue " + new Date();
        Issue issue = new Issue(transport, projectId).setSubject(originalSubject)
                .create();
        String changedSubject = "changed subject";
        issue.setSubject(changedSubject)
            .update();

        Issue reloadedFromRedmineIssue = issueManager.getIssueById(issue.getId());

        assertEquals("Checking if 'update issue' operation changed 'subject' field",
                changedSubject, reloadedFromRedmineIssue.getSubject());
    }

    /**
     * Tests the retrieval of an {@link Issue} by its ID.
     *
     * @throws com.taskadapter.redmineapi.RedmineException
     *                                        thrown in case something went wrong in Redmine
     * @throws RedmineAuthenticationException thrown in case something went wrong while trying to login
     * @throws com.taskadapter.redmineapi.NotFoundException
     *                                        thrown in case the objects requested for could not be found
     */
    @Test
    public void testGetIssueById() throws RedmineException {
        String originalSubject = "Issue " + new Date();
        Issue issue = new Issue(transport, projectId).setSubject( originalSubject)
                .create();

        Issue reloadedFromRedmineIssue = issueManager.getIssueById(issue.getId());

        assertEquals(
                "Checking if 'get issue by ID' operation returned issue with same 'subject' field",
                originalSubject, reloadedFromRedmineIssue.getSubject());
        Tracker tracker = reloadedFromRedmineIssue.getTracker();
        assertNotNull(tracker,"Tracker of issue should not be null");
        assertNotNull(tracker.getId(), "ID of tracker of issue should not be null");
        assertNotNull(tracker.getName(), "Name of tracker of issue should not be null");
        issue.delete();
    }

    @Test
    public void testGetIssues() throws RedmineException {
        // create at least 1 issue
        Issue newIssue = new Issue(transport, projectId).setSubject( "testGetIssues: " + new Date())
                .create();
        List<Issue> issues = issueManager.getIssues(projectKey, null);
        assertTrue(!issues.isEmpty());
        boolean found = false;
        for (Issue issue : issues) {
            if (issue.getId().equals(newIssue.getId())) {
                found = true;
                break;
            }
        }
        if (!found) {
            fail("getIssues() didn't return the issue we just created. The query "
                    + " must have returned all issues created during the last 2 days");
        }
    }

    @Test
    public void testGetIssuesInvalidQueryId() {
        Integer invalidQueryId = 9999999;
        assertThrows(NotFoundException.class, () -> issueManager.getIssues(projectKey, invalidQueryId));
    }

    @Test
    public void testCreateIssueNonUnicodeSymbols() throws RedmineException {
        String nonLatinSymbols = "Example with accents A��o";
        Issue issue = new Issue(transport, projectId).setSubject( nonLatinSymbols)
                .create();
        assertEquals(nonLatinSymbols, issue.getSubject());
    }

    @Test
    public void testCreateIssueSummaryOnly() throws RedmineException {
        Issue issue = new Issue(transport, projectId).setSubject( "This is the summary line 123")
                .create();
        assertNotNull(issue, "Checking returned result");
        assertNotNull(issue.getId(), "New issue must have some ID");

        // check AUTHOR
        Integer EXPECTED_AUTHOR_ID = IntegrationTestHelper.getOurUser(transport).getId();
        assertEquals(EXPECTED_AUTHOR_ID, issue.getAuthorId());
    }

    @Test
    public void testCreateIssueWithParam() throws RedmineException {
        RequestParam param = new RequestParam("name", "value");
        create(new Issue(transport, projectId)
                .setSubject("This is the Issue with one param"), param);
    }

    @Test
    public void testCreateIssueWithNullParams() throws RedmineException {
        RequestParam param1 = new RequestParam("name1", "param1");
        RequestParam param2 = null;
        RequestParam param3 = new RequestParam("name3", "param3");
        RequestParam param4 = new RequestParam("name4", "param4");
        create(new Issue(transport, projectId).setSubject("This is the Issue with null params"), param1, param2, param3, param4);
    }

    @Test
    public void testCreateIssueWithDuplicateAndNullParams() throws RedmineException {
        RequestParam param1 = new RequestParam("name1", "param1");
        RequestParam param2 = null;
        RequestParam param3 = new RequestParam("name3", "param3");
        RequestParam param4 = new RequestParam("name3", "param4");
        create(new Issue(transport, projectId).setSubject("This is the Issue with duplicate and null params"), param1, param2, param3, param4);
    }

    @Test
    public void testCreateIssueWithNullName() {
        RequestParam param1 = new RequestParam(null, "param1");
        RequestParam param2 = new RequestParam("name2", "param2");
        assertThrows(NullPointerException.class, () -> create(new Issue(transport, projectId).setSubject("This is the Issue with null name params"), param1, param2));
    }

    @Test
    public void testCreateIssueWithNullValue() {
        RequestParam param1 = new RequestParam("name1", "param1");
        RequestParam param2 = new RequestParam("name2", null);
        assertThrows(NullPointerException.class, () -> create(new Issue(transport, projectId).setSubject("This is the Issue with null value params"), param1, param2));
    }

    @Test
    public void testCreateIssueWithoutParams() throws RedmineException {
        create(new Issue(transport, projectId).setSubject("This is the Issue without params"));
    }

    private Issue create(Issue issue, RequestParam... params) throws RedmineException {
        Issue responseIssue = issue.create(params);
        assertNotNull(responseIssue, "Checking returned result");
        assertNotNull(responseIssue.getId(), "New issue must have some ID");
        return responseIssue;
    }

    @Test
    public void privateFlagIsRespectedWhenCreatingIssues() throws RedmineException {

        Issue newIssue = new Issue(transport, projectId).setSubject( "private issue")
                .setPrivateIssue(true)
                .create();
        assertThat(newIssue.isPrivateIssue()).isTrue();

        Issue newPublicIssue = new Issue(transport, projectId).setSubject( "public issue")
                .setPrivateIssue(false)
                .create();
        assertThat(newPublicIssue.isPrivateIssue()).isFalse();

        // default value for "is private" should be false
        Issue newDefaultIssue = new Issue(transport, projectId).setSubject( "default public issue").create();
        assertThat(newDefaultIssue.isPrivateIssue()).isFalse();
    }

    /* this test fails with Redmine 3.0.0-3.0.3 because Redmine 3.0.x started
     * returning "not authorized" instead of "not found" for projects with unknown Ids.
     * This worked differently with Redmine 2.6.x.
     * <p>
     * This test is not critical for the release of Redmine Java API library. I am marking it as "ignored" for now.
    */
    @Disabled
    @Test
    public void creatingIssueWithNonExistingProjectIdGivesNotFoundException() {
        int nonExistingProjectId = 99999999; // hopefully this does not exist :)
        assertThrows(NotFoundException.class, () -> new Issue(transport, nonExistingProjectId).setSubject("Summary line 100").create());
    }

    @Test
    public void retrievingIssueWithNonExistingIdGivesNotFoundException() {
        int someNonExistingID = 999999;
        assertThrows(NotFoundException.class, () -> issueManager.getIssueById(someNonExistingID));
    }

    @Test
    public void updatingIssueWithNonExistingIdGivesNotFoundException() {
        int nonExistingId = 999999;
            assertThrows(NotFoundException.class, () -> new Issue(transport, projectId).setId(nonExistingId)
                    .update());
    }

    @Test
    public void testGetIssuesPaging() throws RedmineException {
        // create 27 issues. default page size is 25.
        createIssues(transport, projectId, 27);
        List<Issue> issues = issueManager.getIssues(projectKey, null);
        assertThat(issues.size()).isGreaterThan(26);

        // check that there are no duplicates in the list.
        Set<Issue> issueSet = new HashSet<>(issues);
        assertThat(issueSet.size()).isEqualTo(issues.size());
    }

    @Test
    public void canControlLimitAndOffsetDirectly() throws RedmineException {
        // create 27 issues. default Redmine page size is usually 25 (unless changed in the server settings).
        createIssues(transport, projectId, 27);
        Map<String, String> params = new HashMap<>();
        params.put("limit", "3");
        params.put("offset", "0");
        params.put("project_id", projectId + "");
        List<Issue> issues = issueManager.getIssues(params).getResults();
        // only the requested number of issues is loaded, not all result pages.
        assertThat(issues.size()).isEqualTo(3);
    }

    @Test
    public void testDeleteIssue() throws RedmineException {
        Issue issue = createIssues(transport, projectId, 1).get(0);
        Issue retrievedIssue = issueManager.getIssueById(issue.getId());
        assertEquals(issue, retrievedIssue);

        assertThrows(NotFoundException.class, () -> {
            issue.delete();
            issueManager.getIssueById(issue.getId());
        });
    }

    @Test
    public void testUpdateIssueSpecialXMLtags() throws Exception {
        String newSubject = "\"text in quotes\" and <xml> tags";
        String newDescription = "<taghere>\"abc\"</here>";
        Issue issue = createIssues(transport, projectId, 1).get(0)
                .setSubject(newSubject)
                .setDescription(newDescription);
        issue.update();


        Issue updatedIssue = issueManager.getIssueById(issue.getId());
        assertEquals(newSubject, updatedIssue.getSubject());
        assertEquals(newDescription, updatedIssue.getDescription());
    }

    @Test
    public void testCreateRelation() throws RedmineException {
        List<Issue> issues = createIssues(transport, projectId, 2);
        Issue src = issues.get(0);
        Issue target = issues.get(1);

        String relationText = IssueRelation.TYPE.precedes.toString();
        IssueRelation r = new IssueRelation(transport, src.getId(), target.getId(), relationText)
                .create();
        assertEquals(src.getId(), r.getIssueId());
        assertEquals(target.getId(), r.getIssueToId());
        assertEquals(relationText, r.getType());
    }

    private IssueRelation createTwoRelatedIssues() throws RedmineException {
        List<Issue> issues = createIssues(transport, projectId, 2);
        Issue src = issues.get(0);
        Issue target = issues.get(1);

        String relationText = IssueRelation.TYPE.precedes.toString();
        return new IssueRelation(transport, src.getId(), target.getId(), relationText)
                .create();
    }

    @Test
    public void issueRelationsAreCreatedAndLoadedOK() throws RedmineException {
        IssueRelation relation = createTwoRelatedIssues();
        Issue issue = issueManager.getIssueById(relation.getIssueId(), Include.relations);
        Issue issueTarget = issueManager.getIssueById(relation.getIssueToId(), Include.relations);

        assertThat(issue.getRelations().size()).isEqualTo(1);
        assertThat(issueTarget.getRelations().size()).isEqualTo(1);

        IssueRelation relation1 = issue.getRelations().iterator().next();
        assertThat(relation1.getIssueId()).isEqualTo(issue.getId());
        assertThat(relation1.getIssueToId()).isEqualTo(issueTarget.getId());
        assertThat(relation1.getType()).isEqualTo("precedes");
        assertThat(relation1.getDelay()).isEqualTo((Integer) 0);

        IssueRelation reverseRelation = issueTarget.getRelations().iterator().next();
        // both forward and reverse relations are the same!
        assertThat(reverseRelation).isEqualTo(relation1);
    }

    @Test
    public void issueRelationIsDeleted() throws RedmineException {
        IssueRelation relation = createTwoRelatedIssues();
        relation.delete();
        Issue issue = issueManager.getIssueById(relation.getIssueId(), Include.relations);
        assertThat(issue.getRelations()).isEmpty();
    }

    @Test
    public void testIssueRelationsDelete() throws RedmineException {
        List<Issue> issues = createIssues(transport, projectId, 3);
        Issue src = issues.get(0);
        Issue target = issues.get(1);
        String relationText = IssueRelation.TYPE.precedes.toString();

        new IssueRelation(transport, src.getId(), target.getId(), relationText)
                .create();

        target = issues.get(2);

        new IssueRelation(transport, src.getId(), target.getId(), relationText)
                .create();

        src = issueManager.getIssueById(src.getId(), Include.relations);
        src.getRelations().forEach(r -> {
            try {
                r.delete();
            } catch (RedmineException e) {
                throw new RuntimeException(e);
            }
        });

        Issue issue = issueManager.getIssueById(src.getId(), Include.relations);
        assertTrue(issue.getRelations().isEmpty());
    }

    /**
     * Requires Redmine 2.3
     */
    @Test
    public void testAddDeleteIssueWatcher() throws RedmineException {
        Issue issue = createIssues(transport, projectId, 1).get(0);

        User newUser = UserGenerator.generateRandomUser(transport).create();
        try {
            issue.addWatcher(newUser.getId());

            Collection<Watcher> watchers = issueManager.getIssueById(issue.getId(), Include.watchers).getWatchers();
            assertThat(watchers).hasSize(1);
            assertThat(watchers.iterator().next().getId()).isEqualTo(newUser.getId());

            issue.deleteWatcher(newUser.getId());
            assertThat(
                    issueManager.getIssueById(issue.getId()).getWatchers())
                    .isEmpty();
        } finally {
            newUser.delete();
        }
        issue.delete();
    }

    /**
     * Requires Redmine 2.3
     */
    @Test
    public void testGetIssueWatcher() throws RedmineException {
        Issue issue = createIssues(transport, projectId, 1).get(0);
        Issue retrievedIssue = issueManager.getIssueById(issue.getId());
        assertEquals(issue, retrievedIssue);

        User newUser = UserGenerator.generateRandomUser(transport).create();
        try {
            issue.addWatcher(newUser.getId());
            Issue includeWatcherIssue = issueManager.getIssueById(issue.getId(),
                    Include.watchers);
            if (!includeWatcherIssue.getWatchers().isEmpty()) {
                Watcher watcher1 = includeWatcherIssue.getWatchers().iterator().next();
                assertThat(watcher1.getId()).isEqualTo(newUser.getId());
            }
        } finally {
            newUser.delete();
        }

        issue.delete();
    }

    @Test
    public void testAddIssueWithWatchers() throws RedmineException {
        User newUserWatcher = UserGenerator.generateRandomUser(transport).create();

        try {
            List<Watcher> watchers = new ArrayList<>();
            Watcher watcher = new Watcher().setId(newUserWatcher.getId());
            watchers.add(watcher);

            Issue issue = IssueHelper.generateRandomIssue(transport, projectId)
                    .addWatchers(watchers)
                    .create();

            Issue retrievedIssueWithWatchers =  issueManager.getIssueById(issue.getId(), Include.watchers);

            assertNotNull(retrievedIssueWithWatchers);
            assertNotNull(retrievedIssueWithWatchers.getWatchers());
            assertEquals(watchers.size(), retrievedIssueWithWatchers.getWatchers().size());
            assertEquals(watcher.getId(), retrievedIssueWithWatchers.getWatchers().iterator().next().getId());
        } finally {
            newUserWatcher.delete();
        }
    }

    @Test
    public void testGetIssuesBySummary() throws RedmineException {
        String summary = "issue with subject ABC";
        Issue issue = new Issue(transport, projectId).setSubject(summary)
                .setAssigneeId(ourUser.getId())
                .create();

        assertNotNull(issue, "Checking returned result");
        assertNotNull(issue.getId(), "New issue must have some ID");

        List<Issue> foundIssues = issueManager.getIssuesBySummary(projectKey, summary);

        assertNotNull(foundIssues, "Checking if search results is not NULL" );
        assertFalse(foundIssues.isEmpty(), "Search results must be not empty");

        Issue loadedIssue1 = RedmineTestUtils.findIssueInList(foundIssues,
                issue.getId());
        assertNotNull(loadedIssue1);
        assertEquals(summary, loadedIssue1.getSubject());
        issue.delete();
    }

    @Test
    public void findByNonExistingSummaryReturnsEmptyList() throws RedmineException {
        String summary = "some summary here for issue which does not exist";
        List<Issue> foundIssues = issueManager.getIssuesBySummary(projectKey, summary);
        assertNotNull(foundIssues, "Search result must be not null");
        assertTrue(foundIssues.isEmpty(), "Search result list must be empty");
    }

    @Test
    public void noAPIKeyOnCreateIssueThrowsAE() throws Exception {
        TestConfig testConfig = new TestConfig();
        HttpClient httpClient = IntegrationTestHelper.getHttpClientForTestServer();

        assertThrows(RedmineAuthenticationException.class, () -> {
            RedmineManager redmineMgrEmpty = RedmineManagerFactory.createUnauthenticated(testConfig.getURI(),
                    httpClient);
            new Issue(redmineMgrEmpty.getTransport(), projectId).setSubject("test zzx")
                    .create();
        });
    }

    @Test
    public void wrongAPIKeyOnCreateIssueThrowsAE() throws Exception {
        TestConfig testConfig = new TestConfig();
        final HttpClient httpClient = IntegrationTestHelper.getHttpClientForTestServer();

        assertThrows(RedmineAuthenticationException.class, () -> {
            RedmineManager redmineMgrInvalidKey = RedmineManagerFactory.createWithApiKey(
                    testConfig.getURI(), "wrong_key", httpClient);
            new Issue(redmineMgrInvalidKey.getTransport(), projectId).setSubject("test zzx")
                    .create();
        });
    }

    @Test
    public void testIssueDoneRatio() throws RedmineException {
        Issue createdIssue = new Issue(transport, projectId).setSubject( "Issue " + new Date())
            .create();
        assertEquals((Integer) 0, createdIssue.getDoneRatio(), "Initial 'done ratio' must be 0");
        Integer doneRatio = 50;
        createdIssue.setDoneRatio(doneRatio)
                .update();

        Integer issueId = createdIssue.getId();
        Issue reloadedFromRedmineIssue = issueManager.getIssueById(issueId);
        assertEquals(doneRatio, reloadedFromRedmineIssue.getDoneRatio(),
                "Checking if 'update issue' operation changed 'done ratio' field");

        Integer invalidDoneRatio = 130;
        try {
            reloadedFromRedmineIssue.setDoneRatio(invalidDoneRatio)
                    .update();
        } catch (RedmineProcessingException e) {
            assertEquals(1, e.getErrors().size(), "Must be 1 error");
            assertEquals("Checking error text",
                    "% Done is not included in the list", e.getErrors()
                            .get(0));
        }

        Issue reloadedFromRedmineIssueUnchanged = issueManager.getIssueById(issueId);
        assertEquals(doneRatio, reloadedFromRedmineIssueUnchanged.getDoneRatio(),
                "'done ratio' must have remained unchanged after invalid value");
    }

    @Test
    public void nullDescriptionErasesItOnServer() throws RedmineException {
        Issue issue = new Issue(transport, projectId)
                .setSubject("Issue " + new Date())
                .setDescription("Some description")
                .create();

        assertThat(issue.getDescription()).isEqualTo("Some description");

        issue.setDescription(null);
        issueManager.update(issue);

        Integer issueId = issue.getId();
        Issue reloadedFromRedmineIssue = issueManager.getIssueById(issueId);
        assertThat(reloadedFromRedmineIssue.getDescription()).isNull();
        issue.delete();
    }

    @Test
    public void testIssueJournals() throws RedmineException {
        // create at least 1 issue
        Issue newIssue = new Issue(transport, projectId)
                .setSubject("testGetIssues: " + new Date())
                .create();

        Issue loadedIssueWithJournals = issueManager.getIssueById(newIssue.getId(),
                Include.journals);
        assertTrue(loadedIssueWithJournals.getJournals().isEmpty());

        String commentDescribingTheUpdate = "some comment describing the issue update";
        loadedIssueWithJournals.setSubject("new subject");
        loadedIssueWithJournals.setNotes(commentDescribingTheUpdate);
        issueManager.update(loadedIssueWithJournals);

        Issue loadedIssueWithJournals2 = issueManager.getIssueById(newIssue.getId(),
                Include.journals);
        assertEquals(1, loadedIssueWithJournals2.getJournals()
                .size());

        Journal journalItem = loadedIssueWithJournals2.getJournals().iterator().next();
        assertEquals(commentDescribingTheUpdate, journalItem.getNotes());
        User ourUser = IntegrationTestHelper.getOurUser(transport);
        // can't compare User objects because either of them is not
        // completely filled
        assertEquals(ourUser.getId(), journalItem.getUser().getId());
        assertEquals(ourUser.getFirstName(), journalItem.getUser()
                .getFirstName());
        assertEquals(ourUser.getLastName(), journalItem.getUser()
                .getLastName());
        assertEquals(1, journalItem.getDetails().size());
        final JournalDetail journalDetail = journalItem.getDetails().get(0);
        assertEquals("new subject", journalDetail.getNewValue());
        assertEquals("subject", journalDetail.getName());
        assertEquals("attr", journalDetail.getProperty());

        Issue loadedIssueWithoutJournals = issueManager.getIssueById(newIssue.getId());
        assertTrue(loadedIssueWithoutJournals.getJournals().isEmpty());
    }

    @Test
    public void updateIssueDescription() throws RedmineException {
        Issue issue = new Issue(transport, projectId).setSubject( "test123")
                .create();

        new Issue(transport, projectId).setId(issue.getId())
                .setProjectId(projectId)
                .setDescription("This is a test")
                .update();

        final Issue iss3 = issueManager.getIssueById(issue.getId());
        assertEquals("test123", iss3.getSubject());
        assertEquals("This is a test", iss3.getDescription());
    }

    @Test
    public void updateIssueTitle() throws RedmineException {
        Issue issue = new Issue(transport, projectId).setSubject("test123")
                .setDescription("Original description")
                .create();

        issue.setSubject("New subject")
                .update();

        final Issue iss3 = issueManager.getIssueById(issue.getId());
        assertEquals("New subject", iss3.getSubject());
        assertEquals("Original description", iss3.getDescription());
    }

    @Test
    public void testIssuePriorities() throws RedmineException {
        assertTrue(!issueManager.getIssuePriorities().isEmpty());
    }

    @Test
    public void issueTargetVersionIsSetWhenCreatingOrUpdatingIssues() throws Exception {
        final String version1Name = "1.0";
        final String version2Name = "2.0";

        Version version1 = createVersion(version1Name);
        Issue createdIssue = IssueHelper.generateRandomIssue(transport, projectId)
                .setTargetVersion(version1)
                .create();

        assertNotNull(createdIssue.getTargetVersion());
        assertEquals(version1Name, createdIssue.getTargetVersion().getName());

        Version version2 = createVersion(version2Name);
        createdIssue.setTargetVersion(version2);
        issueManager.update(createdIssue);
        Issue updatedIssue = issueManager.getIssueById(createdIssue.getId());
        assertThat(updatedIssue.getTargetVersion().getName()).isEqualTo(version2Name);

        createdIssue.setTargetVersion(null);
        issueManager.update(createdIssue);
        updatedIssue = issueManager.getIssueById(createdIssue.getId());
        assertThat(updatedIssue.getTargetVersion()).isNull();
    }

    private Version createVersion(String versionName) throws RedmineException {
        return new Version(transport, projectId, versionName).create();
    }

    @Test
    public void testUpdateIssueDoesNotChangeEstimatedTime() throws RedmineException {
        String originalSubject = "Issue " + new Date();
        Issue issue = new Issue(transport, projectId).setSubject( originalSubject)
                .create();
        assertNull(issue.getEstimatedHours(), "Estimated hours must be NULL");

        issue.update();

        Issue reloadedFromRedmineIssue = issueManager.getIssueById(issue.getId());
        assertNull(reloadedFromRedmineIssue.getEstimatedHours(), "Estimated hours must be NULL");
    }

    /**
     * tests the retrieval of statuses.
     *
     * @throws RedmineProcessingException     thrown in case something went wrong in Redmine
     * @throws java.io.IOException                    thrown in case something went wrong while performing I/O
     *                                        operations
     * @throws RedmineAuthenticationException thrown in case something went wrong while trying to login
     * @throws NotFoundException              thrown in case the objects requested for could not be found
     */
    @Test
    public void testGetStatuses() throws RedmineException {
        // TODO we should create some statuses first, but the Redmine Java API
        // does not support this presently
        List<IssueStatus> statuses = issueManager.getStatuses();
        assertFalse(statuses.isEmpty(), "Expected list of statuses not to be empty");
        for (IssueStatus issueStatus : statuses) {
            // asserts on status
            assertNotNull(issueStatus.getId(),"ID of status must not be null");
            assertNotNull(issueStatus.getName(), "Name of status must not be null");
        }
    }

    /**
     * tests the creation and deletion of a {@link com.taskadapter.redmineapi.bean.IssueCategory}.
     *
     * @throws RedmineException               thrown in case something went wrong in Redmine
     * @throws java.io.IOException                    thrown in case something went wrong while performing I/O
     *                                        operations
     * @throws RedmineAuthenticationException thrown in case something went wrong while trying to login
     * @throws NotFoundException              thrown in case the objects requested for could not be found
     */
    @Test
    public void testCreateAndDeleteIssueCategory() throws RedmineException {
        Project project = projectManager.getProjectByKey(projectKey);
        IssueCategory category = new IssueCategory(transport, project.getId(), "Category" + new Date().getTime())
                .setAssigneeId(IntegrationTestHelper.getOurUser(transport).getId())
                .create();
        assertNotNull(category, "Expected new category not to be null");
        assertNotNull(category.getProjectId(), "Expected projectId of new category not to be null");
        assertNotNull(category.getAssigneeId(), "Expected assignee of new category not to be null");

        assertThat(category.getAssigneeId()).isEqualTo(IntegrationTestHelper.getOurUser(transport).getId());

        category.delete();

        // assert that the category is gone
        List<IssueCategory> categories = issueManager.getCategories(project.getId());
        assertTrue(categories.isEmpty(),
                "List of categories of test project must be empty now but is "
                        + categories);
    }

    /**
     * tests the creation and deletion of a {@link com.taskadapter.redmineapi.bean.IssueCategory}
     * with the group as assignee
     *
     * @throws RedmineException               thrown in case something went wrong in Redmine
     * @throws java.io.IOException                    thrown in case something went wrong while performing I/O
     *                                        operations
     * @throws RedmineAuthenticationException thrown in case something went wrong while trying to login
     * @throws NotFoundException              thrown in case the objects requested for could not be found
     */
    @Test
    public void testCreateAndDeleteIssueCategoryGroupAssignee() throws RedmineException {
        Project project = projectManager.getProjectByKey(projectKey);
        IssueCategory category = new IssueCategory(transport, project.getId(), "Category" + new Date().getTime())
                .setAssigneeId(demoGroup.getId())
                .create();
        assertNotNull(category, "Expected new category not to be null");
        assertNotNull(category.getProjectId(),"Expected projectId of new category not to be null");
        assertNotNull(category.getAssigneeId(), "Expected assignee of new category not to be null"
                );

        assertThat(category.getAssigneeId()).isEqualTo(demoGroup.getId());
        assertThat(category.getAssigneeName()).isEqualTo(demoGroup.getName());

        category.delete();

        // assert that the category is gone
        List<IssueCategory> categories = issueManager.getCategories(project.getId());
        assertTrue(categories.isEmpty(),
                "List of categories of test project must be empty now but is "
                        + categories);
    }

    /**
     * tests the retrieval of {@link IssueCategory}s.
     *
     * @throws RedmineException               thrown in case something went wrong in Redmine
     * @throws java.io.IOException                    thrown in case something went wrong while performing I/O
     *                                        operations
     * @throws RedmineAuthenticationException thrown in case something went wrong while trying to login
     * @throws NotFoundException              thrown in case the objects requested for could not be found
     */
    @Test
    public void testGetIssueCategories() throws RedmineException {
        Project project = projectManager.getProjectByKey(projectKey);
        // create some categories
        Integer ourUserId = IntegrationTestHelper.getOurUser(transport).getId();
        IssueCategory category1 = new IssueCategory(transport, project.getId(), "Category" + new Date().getTime())
                .setAssigneeId(ourUserId)
                .create();
        IssueCategory category2 = new IssueCategory(transport, project.getId(), "Category" + new Date().getTime())
                .setAssigneeId(ourUserId)
                .create();
        try {
            List<IssueCategory> categories = issueManager.getCategories(project.getId());
            assertEquals( 2,
                    categories.size(), "Wrong number of categories for project "
                            + project.getName() + " delivered by Redmine Java API");
            for (IssueCategory category : categories) {
                // assert category
                assertNotNull(category.getId(), "ID of category must not be null");
                assertNotNull(category.getName(), "Name of category must not be null");
                assertNotNull(category.getProjectId(), "ProjectId must not be null");
                assertNotNull(category.getAssigneeId(), "Assignee of category must not be null");
            }
        } finally {
            if (category1 != null) {
                category1.delete();
            }
            if (category2 != null) {
                category2.delete();
            }
        }
    }

    @Test
    public void createIssueCategoryFailsWithInvalidProject() {
        assertThrows(NotFoundException.class, () -> {
            new IssueCategory(transport, -1, "InvalidCategory" + new Date().getTime())
                    .create();
        });
    }

    /**
     * tests deletion of an invalid {@link IssueCategory}. Expects a {@link NotFoundException} to be thrown.
     */
    @Test
    public void testDeleteInvalidIssueCategory() {
        // create new test category
        assertThrows(NotFoundException.class, () -> {
            new IssueCategory(transport).setId(-1)
                    .setName("InvalidCategory" + new Date().getTime())
                    .delete();
        });
    }

    /**
     * Tests the creation and retrieval of an
     * {@link com.taskadapter.redmineapi.bean.Issue} with a
     * {@link IssueCategory}.
     */
    @Test
    public void testCreateAndGetIssueWithCategory() throws RedmineException {
        IssueCategory newIssueCategory = null;
        Issue newIssue = null;
        try {
            Project project = projectManager.getProjectByKey(projectKey);
            // create an issue category
            newIssueCategory = new IssueCategory(transport, project.getId(), "Category_" + new Date().getTime())
                .setAssigneeId(IntegrationTestHelper.getOurUser(transport).getId())
                    .create();

            // create an issue
            newIssue = new Issue(transport, projectId).setSubject("getIssueWithCategory_" + UUID.randomUUID())
                    .setCategory(newIssueCategory)
                    .create();
            // retrieve issue
            Issue retrievedIssue = issueManager.getIssueById(newIssue.getId());
            // assert retrieved category of issue
            IssueCategory retrievedCategory = retrievedIssue.getCategory();
            assertNotNull(retrievedCategory, "Category retrieved for issue " + newIssue.getId()
                    + " should not be null");
            assertEquals(newIssueCategory.getId(),
                    retrievedCategory.getId(), "ID of category retrieved for issue "
                            + newIssue.getId() + " is wrong");
            assertEquals(newIssueCategory.getName(), retrievedCategory.getName(), "Name of category retrieved for issue "
                    + newIssue.getId() + " is wrong");

            retrievedIssue.setCategory(null)
                    .update();
            Issue updatedIssue = issueManager.getIssueById(newIssue.getId());
            assertThat(updatedIssue.getCategory()).isNull();
        } finally {
            if (newIssue != null) {
                newIssue.delete();
            }
            if (newIssueCategory != null) {
                newIssueCategory.delete();
            }
        }
    }

    @Test
    public void nullStartDateIsPreserved() throws RedmineException {
        Issue issue = new Issue(transport, projectId).setSubject("test start date")
                .setStartDate(null)
                .create();
        Issue loadedIssue = issueManager.getIssueById(issue.getId());
        assertNull(loadedIssue.getStartDate());
    }

    /**
     * The custom fields used here MUST ALREADY EXIST on the server and be
     * associated with the required task type (bug/feature/task/..).
     * <p/>
     * See feature request http://www.redmine.org/issues/9664
     */
    @Test
    public void testCustomFields() throws Exception {
        Issue issue = createIssue(transport, projectId);

        // TODO this needs to be reworked, when Redmine gains a real CRUD interface for custom fields
        //
        // To test right now the test system needs:
        //
        // Custom Field with ID 1 needs to be:
        // name: my_custom_1
        // format: Text (string)
        // for all project, for all trackers
        //
        // Custom Field with ID 2 needs to be:
        // name: custom_boolean_1
        // format: Boolean (bool)
        //
        // Custom Field with ID 3 needs to be:
        // name: custom_multi_list
        // format: List (list)
        // multiple values: enabled
        // possible values: V1, V2, V3
        // default value: V2
        //
        // All fields: need to be issue fields, for all project, for all trackers
        List<CustomFieldDefinition> customFieldDefinitions = mgr.getCustomFieldManager().getCustomFieldDefinitions();
        CustomFieldDefinition customField1 = getCustomFieldByName(customFieldDefinitions, "my_custom_1");
        CustomFieldDefinition customField2 = getCustomFieldByName(customFieldDefinitions, "custom_boolean_1");

        String custom1Value = "some value 123";
        String custom2Value = "true";

        issue.clearCustomFields()
                .addCustomField(
                        new CustomField()
                                .setId(customField1.getId())
                                .setName(customField1.getName())
                                .setValue(custom1Value)
                )
                .addCustomField(
                        new CustomField()
                                .setId(customField2.getId())
                                .setName(customField2.getName())
                                .setValue(custom2Value));
        issue.update();

        Issue updatedIssue = issueManager.getIssueById(issue.getId());
        assertThat(updatedIssue.getCustomFieldByName(customField1.getName()).getValue()).isEqualTo(custom1Value);
        assertThat(updatedIssue.getCustomFieldByName(customField2.getName()).getValue()).isEqualTo(custom2Value);
    }

    @Test
    public void defaultValueUsedWhenCustomFieldNotProvidedWhenCreatingIssue() throws Exception {
        Issue createdIssue = new Issue(transport, projectId).setSubject("test for custom multi fields")
                .create();
        CustomField customField = createdIssue.getCustomFieldByName("custom_multi_list");
        assertThat(customField).isNotNull();
        assertThat(customField.getValues().size()).isEqualTo(1);
        assertThat(customField.getValues().get(0)).isEqualTo("V2");
        createdIssue.delete();
    }

    @Test
    public void setOneValueForMultiLineCustomField() throws Exception {
        CustomFieldDefinition multiFieldDefinition = loadMultiLineCustomFieldDefinition();
        CustomField customField = new CustomField().setId(multiFieldDefinition.getId());
        String defaultValue = multiFieldDefinition.getDefaultValue();
        customField.setValues(Collections.singletonList(defaultValue));

        Issue createdIssue = new Issue(transport, projectId).setSubject("test for custom multi fields - set one value")
                .addCustomField(customField)
                .create();
        customField = createdIssue.getCustomFieldByName("custom_multi_list");
        assertThat(customField).isNotNull();
        assertThat(customField.getValues().size()).isEqualTo(1);
        assertThat(customField.getValues().get(0)).isEqualTo(defaultValue);
        createdIssue.delete();
    }

    /**
     * See check for https://github.com/taskadapter/redmine-java-api/issues/54
     *
     * BUG in Redmine 3.0.0: multi-line custom fields values are ignored by Redmine 3.0.0 for new issues
     * without tracker_id value.
     * the server ignores values V1, V3 and assigns default V2 value to that multi-line custom field.
     * I submitted this as http://www.redmine.org/issues/19368 - fixed in Redmine 3.0.1
     */
    @Test
    public void setMultiValuesForMultiLineCustomField() throws Exception {
        CustomFieldDefinition multiFieldDefinition = loadMultiLineCustomFieldDefinition();
        CustomField customField = new CustomField().setId(multiFieldDefinition.getId())
                .setValues(Arrays.asList("V1", "V3"));
        Issue issue = new Issue(transport, projectId).setSubject("test for custom multi fields - set multiple values")
                .addCustomField(customField)
                .create();

        CustomField loadedCustomField = issue.getCustomFieldByName("custom_multi_list");
        assertThat(loadedCustomField).isNotNull();
        assertThat(loadedCustomField.getValues().size()).isEqualTo(2);
        List<String> values = new ArrayList<>(loadedCustomField.getValues());
        Collections.sort(values);
        assertThat(loadedCustomField.getValues().get(0)).isEqualTo("V1");
        assertThat(loadedCustomField.getValues().get(1)).isEqualTo("V3");
        issue.delete();
    }

    /**
     * This is to make sure we have a workaround for a known bug in redmine 2.6.
     */
    @Test
    public void createIssueWithEmptyListInMultilineCustomFields() throws Exception {
        CustomFieldDefinition multiFieldDefinition = loadMultiLineCustomFieldDefinition();
        CustomField customField = new CustomField().setId(multiFieldDefinition.getId())
                .setValues(Collections.EMPTY_LIST);

        Issue newIssue = new Issue(transport, projectId).setSubject("test for custom multi fields - set multiple values")
                .addCustomField(customField)
                .create();

        customField = newIssue.getCustomFieldByName("custom_multi_list");
        assertThat(customField).isNotNull();
        assertThat(customField.getValues().size()).isEqualTo(0);
        newIssue.delete();
    }

    private static CustomFieldDefinition loadMultiLineCustomFieldDefinition() throws RedmineException {
        List<CustomFieldDefinition> customFieldDefinitions = mgr.getCustomFieldManager().getCustomFieldDefinitions();
        return getCustomFieldByName(customFieldDefinitions, "custom_multi_list");
    }

    @Disabled
    @Test
    public void testChangesets() throws RedmineException {
        final Issue issue = issueManager.getIssueById(89, Include.changesets);
        assertThat(issue.getChangesets().size()).isEqualTo(2);
        final Changeset firstChange = issue.getChangesets().iterator().next();
        assertNotNull(firstChange.getComments());
    }

    /**
     * Tests the retrieval of {@link Tracker}s.
     *
     * @throws RedmineException               thrown in case something went wrong in Redmine
     * @throws RedmineAuthenticationException thrown in case something went wrong while trying to login
     * @throws NotFoundException              thrown in case the objects requested for could not be found
     */
    @Test
    public void testGetTrackers() throws RedmineException {
        List<Tracker> trackers = issueManager.getTrackers();
        assertNotNull(trackers,"List of trackers returned should not be null");
        assertFalse(trackers.isEmpty(), "List of trackers returned should not be empty");
        for (Tracker tracker : trackers) {
            assertNotNull(tracker, "Tracker returned should not be null");
            assertNotNull(tracker.getId(), "ID of tracker returned should not be null");
            assertNotNull(tracker.getName(), "Name of tracker returned should not be null");
        }
    }

    @Test
    public void getSavedQueriesDoesNotFailForTempProject()
            throws RedmineException {
        issueManager.getSavedQueries(projectKey);
    }

    @Test
    public void getSavedQueriesDoesNotFailForNULLProject()
            throws RedmineException {
        issueManager.getSavedQueries(null);
    }

    @Disabled("This test requires a specific project configuration")
    @Test
    public void testSavedQueries() throws RedmineException {
        final Collection<SavedQuery> queries = issueManager.getSavedQueries("test");
        assertTrue(!queries.isEmpty());
    }

    @Test
    public void statusIsUpdated() throws RedmineException {
        Issue issue = createIssues(transport, projectId, 1).get(0);
        Issue retrievedIssue = issueManager.getIssueById(issue.getId());
        Integer initialStatusId = retrievedIssue.getStatusId();

        List<IssueStatus> statuses = issueManager.getStatuses();
        // get some status ID that is not equal to the initial one
        Integer newStatusId = null;
        for (IssueStatus status : statuses) {
            if (!status.getId().equals(initialStatusId)) {
                newStatusId = status.getId();
                break;
            }
        }
        if (newStatusId == null) {
            throw new RuntimeException("can't run this test: no Issue Statuses are available except for the initial one");
        }
        retrievedIssue.setStatusId(newStatusId);
        issueManager.update(retrievedIssue);

        Issue issueWithUpdatedStatus = issueManager.getIssueById(retrievedIssue.getId());
        assertThat(issueWithUpdatedStatus.getStatusId()).isEqualTo(newStatusId);
    }

    @Test
    public void changeProject() throws RedmineException {
        Project project1 = mgr.getProjectManager().getProjectByKey(projectKey);
        Project project2 = mgr.getProjectManager().getProjectByKey(projectKey2);
        Issue issue = createIssue(transport, projectId);
        Issue retrievedIssue = issueManager.getIssueById(issue.getId());
        assertThat(retrievedIssue.getProjectId()).isEqualTo(project1.getId());
        issue.setProjectId(project2.getId());
        issueManager.update(issue);
        retrievedIssue = issueManager.getIssueById(issue.getId());
        assertThat(retrievedIssue.getProjectId()).isEqualTo(project2.getId());
        deleteIssueIfNotNull(issue);
    }

    /** This test requires one-time Redmine server configuration:
     * "Settings" -> "Issue Tracking" -> "allow issue assignment to groups" : ON
     */
    @Test
    public void issueAssignmentUserAndGroup() throws RedmineException {
        Issue issue = createIssue(transport, projectId);
        assertNull(issue.getAssigneeId());
        issue.setAssigneeId(IntegrationTestHelper.getOurUser(transport).getId());
        issueManager.update(issue);
        Issue retrievedIssue = issueManager.getIssueById(issue.getId());
        // User assignment succeeded
        assertThat(retrievedIssue.getAssigneeId()).isEqualTo(IntegrationTestHelper.getOurUser(transport).getId());
        issue.setAssigneeId(demoGroup.getId());
        issueManager.update(issue);
        retrievedIssue = issueManager.getIssueById(issue.getId());
        // Group assignment succeeded
        assertThat(retrievedIssue.getAssigneeId()).isEqualTo(demoGroup.getId());
        deleteIssueIfNotNull(issue);
    }

    @Test
    public void issueCanBeCreatedOnBehalfOfAnotherUser() throws RedmineException {
        User newUser = UserGenerator.generateRandomUser(transport).create();
        Issue issue = null;
        try {
            RedmineManager managerOnBehalfOfUser = IntegrationTestHelper.createRedmineManager();
            managerOnBehalfOfUser.setOnBehalfOfUser(newUser.getLogin());

            issue = createIssue(managerOnBehalfOfUser.getTransport(), projectId);
            assertThat(issue.getAuthorName()).isEqualTo(newUser.getFullName());
        } finally {
            newUser.delete();
            deleteIssueIfNotNull(issue);
        }
    }

    @Test
    public void issuesCanBeFoundByFreeFormSearch() throws RedmineException {
        // create some random issues in the project
        createIssues(transport, projectId, 3);

        String subject = "test for free_form_search.";
        Issue issue = null;
        try {
            issue = new Issue(transport, projectId).setSubject( subject)
                    .create();

            Map<String, String> params = new HashMap<>();
            params.put("project_id", Integer.toString(projectId));
            params.put("subject", "~free_form_search");
            List<Issue> issues = issueManager.getIssues(params).getResults();
            assertThat(issues.size()).isEqualTo(1);
            final Issue loaded = issues.get(0);
            assertThat(loaded.getSubject()).isEqualTo(subject);
        } finally {
            deleteIssueIfNotNull(issue);
        }
    }

    /**
     * regression test for https://github.com/taskadapter/redmine-java-api/issues/12 and
     * https://github.com/taskadapter/redmine-java-api/issues/215
     */
    @Test
    public void issuesCanBeFoundByMultiQuerySearch() throws RedmineException {
        new Issue(transport, projectId).setSubject( "summary 1 here")
                .create();
        new Issue(transport, projectId).setSubject( "summary 2 here")
                .create();

        // have some random subject to avoid collisions with other tests
        String subject = "another" + new Random().nextInt();
        new Issue(transport, projectId).setSubject( subject)
                .create();

        final User currentUser = userManager.getCurrentUser();
        Params params = new Params()
                .add("set_filter", "1")
                .add("f[]", "subject")
                .add("op[subject]", "~")
                .add("v[subject][]", subject)
                .add("f[]", "author_id")
                .add("op[author_id]", "=")
                .add("v[author_id][]", currentUser.getId()+"");
        final ResultsWrapper<Issue> list = issueManager.getIssues(params);
        // only 1 issue must be found
        assertThat(list.getResults()).hasSize(1);
    }

    private void deleteIssueIfNotNull(Issue issue) throws RedmineException {
        if (issue != null) {
            issue.delete();
        }
    }
}
