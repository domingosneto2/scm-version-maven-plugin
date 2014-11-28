package com.codeinstructions.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFileStatus;
import org.apache.maven.scm.ScmResult;
import org.apache.maven.scm.log.ScmLogDispatcher;
import org.apache.maven.scm.log.ScmLogger;
import org.apache.maven.scm.provider.hg.HgUtils;
import org.apache.maven.scm.provider.hg.command.HgConsumer;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 *
 */
@Mojo(name = "version")
public class MercurialVersionPluginMojo extends AbstractMojo {
    @Parameter(property = "version.base", defaultValue = "${project.name}")
    private String baseName;

    @Parameter(property = "version.version", defaultValue = "${project.version}")
    private String projectVersion;

    /**
     * Local directory to be used to issue SCM actions
     *
     * @since 1.0
     */
    @Parameter(property = "version.scmDirectory", defaultValue = "${basedir}")
    private File scmDirectory;

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(property = "version.timezone", defaultValue = "GMT")
    private String timezone;

    private ScmLogDispatcher logger = new ScmLogDispatcher();

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            String branch = getBranch();
            String changeSet = getChangeSet();
            String date = getTimestamp();
            getLog().info("Date: " + date);
            long unixDate = Long.parseLong(date.split("\\.")[0]);
            Instant instant = Instant.ofEpochSecond(unixDate);
            getLog().info("Instant: " + instant);
            ZonedDateTime zdt = ZonedDateTime.ofInstant(instant, ZoneId.of(timezone));
            String version = baseName + "-" + projectVersion + "-" + branch + "-" + zdt + "-" + changeSet;
            getLog().info("Version: " + version);
            project.getProperties().setProperty("version.version", version);
        } catch (ScmException e) {
            throw new MojoExecutionException("SCMException: " + e.getMessage(), e);
        }
    }

    private String getChangeSet() throws ScmException, MojoExecutionException {
        HgOutputConsumer consumer = new HgOutputConsumer(logger);
        ScmResult result = HgUtils.execute(consumer, logger, scmDirectory, new String[]{"id", "-i"});
        checkResult(result);
        return consumer.getOutput();
    }

    private String getBranch() throws ScmException, MojoExecutionException {
        HgOutputConsumer consumer = new HgOutputConsumer(logger);
        ScmResult result = HgUtils.execute(consumer, logger, scmDirectory, new String[]{"branch"});
        checkResult(result);
        return consumer.getOutput();
    }

    private String getTimestamp() throws ScmException, MojoExecutionException {
        HgOutputConsumer consumer = new HgOutputConsumer(logger);
        ScmResult result = HgUtils.execute(consumer, logger, scmDirectory, new String[]{"log", "-l", "1", "--template", "{date}"});
        checkResult(result);
        return consumer.getOutput();
    }

    private void checkResult(ScmResult result)
            throws MojoExecutionException {
        if (!result.isSuccess()) {
            getLog().debug("Provider message:");
            getLog().debug(result.getProviderMessage() == null ? "" : result.getProviderMessage());
            getLog().debug("Command output:");
            getLog().debug(result.getCommandOutput() == null ? "" : result.getCommandOutput());
            throw new MojoExecutionException("Command failed."
                    + StringUtils.defaultString(result.getProviderMessage()));
        }
    }

    private static class HgOutputConsumer
            extends HgConsumer {

        private String output;

        private HgOutputConsumer(ScmLogger logger) {
            super(logger);
        }

        public void doConsume(ScmFileStatus status, String line) {
            output = line;
        }

        public String getOutput() {
            return output;
        }
    }
}
