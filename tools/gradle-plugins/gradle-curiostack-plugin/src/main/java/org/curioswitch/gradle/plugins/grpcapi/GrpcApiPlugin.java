/*
 * MIT License
 *
 * Copyright (c) 2017 Choko (choko@curioswitch.org)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.curioswitch.gradle.plugins.grpcapi;

import com.google.protobuf.gradle.ExecutableLocator;
import com.google.protobuf.gradle.ProtobufConfigurator;
import com.google.protobuf.gradle.ProtobufConfigurator.JavaGenerateProtoTaskCollection;
import com.google.protobuf.gradle.ProtobufConvention;
import com.google.protobuf.gradle.ProtobufPlugin;
import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.codehaus.groovy.runtime.GStringImpl;
import org.curioswitch.gradle.common.LambdaClosure;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.BasePluginConvention;
import org.gradle.api.plugins.JavaLibraryPlugin;

/**
 * A simple gradle plugin that configures the protobuf-gradle-plugin with appropriate defaults for a
 * GRPC API definition.
 *
 * <p>The project will be configured as a Java library with the GRPC dependencies, and the protobuf
 * compiler will generate both Java code and a descriptor set with source code\ info for using in
 * documentation services.
 */
public class GrpcApiPlugin implements Plugin<Project> {

  private static final List<String> GRPC_DEPENDENCIES =
      Collections.unmodifiableList(Arrays.asList("grpc-core", "grpc-protobuf", "grpc-stub"));

  @Override
  public void apply(Project project) {
    project.getPluginManager().apply(JavaLibraryPlugin.class);

    GRPC_DEPENDENCIES.forEach(dep -> project.getDependencies().add("api", "io.grpc:" + dep));

    project.afterEvaluate(
        p -> {
          Map<String, String> managedVersions =
              project
                  .getExtensions()
                  .getByType(DependencyManagementExtension.class)
                  .getManagedVersions();

          ProtobufConfigurator protobuf =
              project.getConvention().getPlugin(ProtobufConvention.class).getProtobuf();
          // We generate into the apt directory since the gradle-apt-plugin provides good integration
          // with IntelliJ and no need to reinvent the wheel.
          protobuf.generatedFilesBaseDir = project.getBuildDir() + "/generated/source/apt";
          protobuf.protoc(
              LambdaClosure.of(
                  (ExecutableLocator locator) ->
                      locator.setArtifact(
                          "com.google.protobuf:protoc:"
                              + managedVersions.get("com.google.protobuf:protoc"))));

          protobuf.plugins(
              LambdaClosure.of(
                  (NamedDomainObjectContainer<ExecutableLocator> locators) ->
                      locators
                          .create("grpc")
                          .setArtifact(
                              "io.grpc:protoc-gen-grpc-java:"
                                  + managedVersions.get("io.grpc:grpc-core"))));

          String archivesBaseName =
              project.getConvention().getPlugin(BasePluginConvention.class).getArchivesBaseName();
          String descriptorSetOutputPath =
              project.getBuildDir()
                  + "/resources/main/META-INF/armeria/grpc/"
                  + project.getGroup()
                  + "."
                  + archivesBaseName
                  + ".dsc";
          protobuf.generateProtoTasks(
              LambdaClosure.of(
                  (JavaGenerateProtoTaskCollection tasks) -> {
                    tasks
                        .all()
                        .forEach(
                            task -> {
                              task.getBuiltins().getByName("java").setOutputSubDir("");
                              task.getPlugins().create("grpc").setOutputSubDir("");
                            });
                    tasks
                        .ofSourceSet("main")
                        .forEach(
                            task -> {
                              task.getOutputs().file(descriptorSetOutputPath);
                              task.generateDescriptorSet = true;
                              task.descriptorSetOptions.includeSourceInfo = true;
                              task.descriptorSetOptions.includeImports = true;
                              task.descriptorSetOptions.path =
                                  new GStringImpl(
                                      new Object[] {}, new String[] {descriptorSetOutputPath});
                            });
                  }));
        });

    // Add the protobuf plugin last to make sure our afterEvaluate runs before it.
    project.getPluginManager().apply(ProtobufPlugin.class);
  }
}