/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2021 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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

package org.wildfly.extras.transformer.tool.maven;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.MetadataUpload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicyProvider;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.wildfly.extras.transformer.ArchiveTransformer;
import org.wildfly.extras.transformer.TransformerBuilder;

/**
 * Recursively transform a dependency with {@code -$$jakarta9$$} appended to the version.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Component(role = RepositoryConnectorFactory.class, hint = "eclipse-transformer")
public class BataviaRepositoryConnectorFactory implements RepositoryConnectorFactory {
    private static final String SUFFIX = "-$$jakarta9$$";
    private final BasicRepositoryConnectorFactory factory = new BasicRepositoryConnectorFactory();

    @Requirement
    private Logger logger;

    @Inject
    @SuppressWarnings("CdiInjectionPointsInspection")
    BataviaRepositoryConnectorFactory(final TransporterProvider transporterProvider, final RepositoryLayoutProvider layoutProvider,
                                      final ChecksumPolicyProvider checksumPolicyProvider, final FileProcessor fileProcessor) {
        factory.setTransporterProvider(transporterProvider);
        factory.setRepositoryLayoutProvider(layoutProvider);
        factory.setChecksumPolicyProvider(checksumPolicyProvider);
        factory.setFileProcessor(fileProcessor);
    }

    @Override
    public RepositoryConnector newInstance(final RepositorySystemSession session, final RemoteRepository repository) throws NoRepositoryConnectorException {
        final RepositoryConnector delegate = factory.newInstance(session, repository);
        return new RepositoryConnector() {
            @Override
            public void get(final Collection<? extends ArtifactDownload> artifactDownloads, final Collection<? extends MetadataDownload> metadataDownloads) {
                if (artifactDownloads == null) {
                    delegate.get(null, metadataDownloads);
                    return;
                }
                final Map<ArtifactDownload, File> artifactsToTransform = new HashMap<>();
                final List<ArtifactDownload> newDownloads = new ArrayList<>();
                for (ArtifactDownload artifactDownload : artifactDownloads) {
                    final Artifact artifact = artifactDownload.getArtifact();
                    final String version = artifact.getVersion();
                    if (version != null && version.endsWith(SUFFIX)) {
                        artifactDownload.setArtifact(artifact.setVersion(version.replace(SUFFIX, "")));
                        final File requested = artifactDownload.getFile();
                        final File newFile = new File(requested.getAbsolutePath().replace(SUFFIX, ""));
                        artifactDownload.setFile(newFile);
                        artifactsToTransform.put(artifactDownload, requested);
                        if (!newFile.exists()) {
                            newDownloads.add(artifactDownload);
                        }
                    } else {
                        newDownloads.add(artifactDownload);
                    }
                }
                delegate.get(newDownloads, metadataDownloads);

                for (Map.Entry<ArtifactDownload, File> entry : artifactsToTransform.entrySet()) {
                    final ArtifactDownload artifactDownload = entry.getKey();
                    final File src = artifactDownload.getFile();
                    final File target = entry.getValue();

                    // If the source file does not exist, it may not have been downloaded from this repository, skip
                    // the processing and hope another repository was defined.
                    if (!src.exists()) {
                        continue;
                    }

                    final Artifact artifact = artifactDownload.getArtifact();

                    artifactDownload.setFile(target);
                    if (artifact.getVersion().endsWith(SUFFIX)) {
                        continue;
                    }
                    if (!target.exists() && target.getParent() != null) {
                        target.getParentFile().mkdirs();
                    }
                    try {
                        if (artifact.getExtension().equals("jar")) {
                            if (logger.isDebugEnabled()) {
                                logger.debug(String.format("Transforming %s", src.getName()));
                            }
                            transformArchive(src, target);
                            if (logger.isDebugEnabled()) {
                                logger.debug(String.format("Transformed %s to %s", src.getName(), target.getName()));
                            }
                        } else if (artifact.getExtension().equals("pom")) {
                            modifyPomFile(src, target);
                        } else {
                            Files.copy(src.toPath(), target.toPath());
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }

            @Override
            public void put(final Collection<? extends ArtifactUpload> artifactUploads, final Collection<? extends MetadataUpload> metadataUploads) {
                delegate.put(artifactUploads, metadataUploads);
            }

            @Override
            public void close() {
                delegate.close();
            }

            private void transformArchive(final File src, final File target) throws IOException {
                final TransformerBuilder builder = org.wildfly.extras.transformer.TransformerFactory.getInstance()
                        .newTransformer();
                builder.setVerbose(logger.isDebugEnabled());
                ArchiveTransformer transformer = builder.build();
                transformer.transform(src, target);
            }

            private void modifyPomFile(final File source, final File target) {
                try {
                    final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
                    final DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
                    final Document document = documentBuilder.parse(source);
                    final NodeList project = document.getElementsByTagName("project").item(0).getChildNodes();

                    for (int nc = 0; nc < project.getLength(); ++nc) {
                        final Node node = project.item(nc);
                        if (node instanceof Element) {
                            final Element element = (Element) node;
                            switch (node.getNodeName()) {
                                case "version":
                                    updateVersion(element);
                                    break;
                                case "parent":
                                    handleParent(element);
                                    break;
                                case "dependencies":
                                    handleDependencies(element);
                                    break;
                                case "plugin":
                                case "dependencyManagement":
                                    handleDependencies((Element) element.getElementsByTagName("dependencies").item(0));
                                    break;

                            }
                        }
                    }

                    // Write the DOM object to the file
                    final TransformerFactory transformerFactory = TransformerFactory.newInstance();
                    final Transformer transformer = transformerFactory.newTransformer();
                    final DOMSource domSource = new DOMSource(document);
                    final StreamResult streamResult = new StreamResult(target);
                    transformer.transform(domSource, streamResult);

                } catch (Exception pce) {
                    throw new RuntimeException(pce);
                }

            }

            private void handleDependencies(Element element) {
                final NodeList deps = element.getElementsByTagName("dependency");
                for (int i = 0; i < deps.getLength(); ++i) {
                    final Element dep = (Element) deps.item(i);
                    final NodeList versions = dep.getElementsByTagName("version");
                    updateVersion(versions.item(0));
                }
            }

            private void handleParent(Element node) {
                updateVersion(node.getElementsByTagName("version").item(0));
            }

            private void updateVersion(final Node version) {
                if (version != null && !version.getTextContent().endsWith(SUFFIX)) {
                    version.setTextContent(version.getTextContent() + SUFFIX);
                }
            }
        };

    }

    @Override
    public float getPriority() {
        return Float.MAX_VALUE;
    }
}
