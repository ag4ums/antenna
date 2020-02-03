/*
 * Copyright (c) Bosch Software Innovations GmbH 2016-2017.
 * Copyright (c) Bosch.IO GmbH 2020.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.sw360.antenna.workflow.generators;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.eclipse.sw360.antenna.api.Attachable;
import org.eclipse.sw360.antenna.api.IAttachable;
import org.eclipse.sw360.antenna.api.configuration.ToolConfiguration;
import org.eclipse.sw360.antenna.api.exceptions.ExecutionException;
import org.eclipse.sw360.antenna.api.workflow.AbstractGenerator;
import org.eclipse.sw360.antenna.model.artifact.Artifact;
import org.eclipse.sw360.antenna.model.license.License;
import org.eclipse.sw360.antenna.model.license.LicenseInformation;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates an HTML report of component license information.
 */
public class HTMLReportGenerator extends AbstractGenerator {
    private static final String IDENTIFIER = "attribution-doc";
    private static final String CLASSIFIER = "antenna-attribution-doc";
    private static final String TYPE = "html";

    private static final String LICENSE_REPORT_TEMPLATE_FILE = "licenseReport.vm";
    private static final String LICENSE_REPORT_FILE = "3rdparty-licenses.html";
    private Charset encoding;

    public HTMLReportGenerator() {
        this.workflowStepOrder = 600;
    }

    @Override
    public Map<String, IAttachable> produce(Collection<Artifact> artifacts){
        // Get path to disclosure documents
        ToolConfiguration toolConfig = context.getToolConfiguration();
        Path reportFilePath = toolConfig.getAntennaTargetDirectory().resolve(LICENSE_REPORT_FILE);

        Set<ArtifactForHTMLReport> artifactsForHTMLReport = extractRelevantArtifactInformation(artifacts);

        writeReportToFile(artifactsForHTMLReport, reportFilePath.toFile());

        return Collections.singletonMap(IDENTIFIER, new Attachable(TYPE, CLASSIFIER, reportFilePath.toFile()));
    }

    protected void writeReportToFile(Set<ArtifactForHTMLReport> artifactsForHTMLReport, File reportFile) {
        final VelocityEngine velocityEngine = setupVelocityEngine();
        final VelocityContext velocityContext = setupVelocityContext(artifactsForHTMLReport);

        // Write the template to the report file
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(reportFile), encoding)) {
            Template template = velocityEngine.getTemplate(LICENSE_REPORT_TEMPLATE_FILE, "utf-8");
            template.merge(velocityContext, writer);
        } catch (IOException e) {
            throw new ExecutionException("Cannot write HTML report file: " + e.getMessage());
        }
    }

    private VelocityEngine setupVelocityEngine() {
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS,"org.apache.velocity.runtime.log.NullLogSystem");
        velocityEngine.setProperty("resource.loader", "class");
        velocityEngine.setProperty("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");

        return velocityEngine;
    }

    private VelocityContext setupVelocityContext(Set<ArtifactForHTMLReport> artifactsForHTMLReport) {
        // Initialise Velocity and put the artifacts and licenses into context
        VelocityContext velocityContext = new VelocityContext();
        Set<License> licensesForHtmlReport = getAllLicenses(artifactsForHTMLReport);

        velocityContext.put("artifacts", artifactsForHTMLReport);
        velocityContext.put("licenses", licensesForHtmlReport);
        velocityContext.put("HTMLReportUtils", HTMLReportUtils.class);

        return velocityContext;
    }

    private Set<ArtifactForHTMLReport> extractRelevantArtifactInformation(Collection<Artifact> artifacts) {
        return artifacts.stream()
                .map(ArtifactForHTMLReport::new)
                .collect(Collectors.toSet());
    }

    private Set<License> getAllLicenses(Set<ArtifactForHTMLReport> artifacts) {
        return artifacts.stream()
                .map(ArtifactForHTMLReport::getLicense)
                .filter(Objects::nonNull)
                .map(LicenseInformation::getLicenses)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    @Override
    public void configure(Map<String, String> configMap) {
        this.encoding = context.getToolConfiguration().getEncoding();
    }
}
