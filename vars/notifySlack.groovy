// Function for slack notification with some modified
// Reference: https://danielschaaff.com/2018/02/09/better-jenkins-notifications-in-declarative-pipelines/
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.model.Actionable;

def call(String buildStatus = 'STARTED', String channel = '#senaobot', Boolean livedoc_enable = false) {

  // buildStatus of null means successfull
  buildStatus = buildStatus ?: 'SUCCESSFUL'
  channel = channel ?: '#senaobot'

  // Default values
  def colorName = 'RED'
  def colorCode = '#FF0000'
  def subject = "Result: ${buildStatus}(<${env.RUN_DISPLAY_URL}|Open>) (<${env.RUN_CHANGES_DISPLAY_URL}|  Changes>)"
  def title = "${env.JOB_NAME} Build: ${env.BUILD_NUMBER}"
  def title_link = "${env.RUN_DISPLAY_URL}"
  def branchName = "$branch"

  def commit = sh(returnStdout: true, script: 'git rev-parse HEAD')
  def author = sh(returnStdout: true, script: "git --no-pager show -s --format='%an'").trim()
  def message = sh(returnStdout: true, script: 'git log -1 --pretty=%B').trim()
  //def shortCommit = "$afterCommit".substring(0,6)
  //def commit = "$shortCommit"
  //def author = "$developer"
  //def message = "$message"
  def liveDocUrl = "http://snwl-falcon-test-report.s3-website-us-west-2.amazonaws.com/$branch-result.html"
  def slackMessagelist = []

  // Override default values based on build status
  if (buildStatus == 'STARTED') {
    color = 'YELLOW'
    colorCode = '#FFFF00'
  } else if (buildStatus == 'SUCCESSFUL') {
    color = 'GREEN'
    colorCode = 'good'
  } else if (buildStatus == 'UNSTABLE') {
    color = 'YELLOW'
    colorCode = 'warning'
  } else {
    color = 'RED'
    colorCode = 'danger'
  }

  // get test results for slack message
  @NonCPS
  def getTestSummary = { ->
    def testResultAction = currentBuild.rawBuild.getAction(AbstractTestResultAction.class)
    def summary = ""

    if (testResultAction != null) {
        def total = testResultAction.getTotalCount()
        def failed = testResultAction.getFailCount()
        def skipped = testResultAction.getSkipCount()

        summary = "Test results:\n\t"
        summary = summary + ("Passed: " + (total - failed - skipped))
        summary = summary + (", Failed: " + failed + " ${testResultAction.failureDiffString}")
        summary = summary + (", Skipped: " + skipped)
    } else {
        summary = "No tests found"
    }
    return summary
  }
  def testSummaryRaw = getTestSummary()
  // format test summary as a code block
  def testSummary = "```${testSummaryRaw}```"
  println testSummary.toString()

  JSONObject attachment = new JSONObject();
  attachment.put('author',"jenkins");
  attachment.put('author_link',"https://example.com");
  attachment.put('title', title.toString());
  attachment.put('title_link',title_link.toString());
  attachment.put('text', subject.toString());
  attachment.put('fallback', "fallback message");
  attachment.put('color', colorCode);
  attachment.put('mrkdwn_in', ["fields"])
  // JSONObject for branch
  JSONObject branch = new JSONObject();
  branch.put('title', 'Branch');
  branch.put('value', branchName.toString());
  branch.put('short', true);
  slackMessagelist << branch;
  // JSONObject for author
  JSONObject commitAuthor = new JSONObject();
  commitAuthor.put('title', 'Author');
  commitAuthor.put('value', author.toString());
  commitAuthor.put('short', true);
  slackMessagelist << commitAuthor;
  // JSONObject for branch
  JSONObject commitMessage = new JSONObject();
  commitMessage.put('title', 'Commit Message');
  commitMessage.put('value', message.toString());
  commitMessage.put('short', false);
  slackMessagelist << commitMessage;
  // JSONObject for test results
  JSONObject testResults = new JSONObject();
  testResults.put('title', 'Test Summary');
  testResults.put('value', testSummary.toString());
  testResults.put('short', false);
  slackMessagelist << testResults;

  // JSONObject for livedoc
  if (livedoc_enable){
    JSONObject livedoc = new JSONObject();
    livedoc.put('title', 'Live Documentation');
    livedoc.put('value', liveDocUrl.toString());
    livedoc.put('short', false);
    slackMessagelist << livedoc;
  }

  attachment.put('fields', slackMessagelist);
  JSONArray attachments = new JSONArray();
  attachments.add(attachment);
  println attachments.toString()

  // Send notifications 
  slackSend (color: colorCode, message: subject, attachments: attachments.toString(), channel: channel)
}