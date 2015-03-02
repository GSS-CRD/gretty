/*
 * Gretty
 *
 * Copyright (C) 2013-2015 Andrey Hihlovskiy and contributors.
 *
 * See the file "LICENSE" for copying and usage permission.
 * See the file "CONTRIBUTORS" for complete list of contributors.
 */
package org.akhikhl.gretty

import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 *
 * @author akhikhl
 */
class FarmAfterIntegrationTestTask extends FarmStopTask {

  private static final Logger log = LoggerFactory.getLogger(FarmBeforeIntegrationTestTask)

  private String integrationTestTask_
  private boolean integrationTestTaskAssigned

  protected Map webAppRefs = [:]

  @TaskAction
  void action() {
    super.action()
    if(project.ext.has('grettyLaunchThread') && project.ext.grettyLaunchThread != null) {
      project.ext.grettyLaunchThread.join()
      project.ext.grettyLaunchThread = null
    }
    System.out.println 'Server stopped.'
  }

  String getIntegrationTestTask() {
    integrationTestTask_ ?: new FarmConfigurer(project).getProjectFarm(farmName).integrationTestTask
  }

  boolean getIntegrationTestTaskAssigned() {
    integrationTestTaskAssigned
  }

  Iterable<Project> getWebAppProjects() {
    FarmConfigurer configurer = new FarmConfigurer(project)
    Map wrefs = [:]
    FarmConfigurer.mergeWebAppRefMaps(wrefs, webAppRefs)
    FarmConfigurer.mergeWebAppRefMaps(wrefs, configurer.getProjectFarm(farmName).webAppRefs)
    if(!wrefs && !configurer.getProjectFarm(farmName).includes)
      wrefs = configurer.getDefaultWebAppRefMap()
    configurer.getWebAppProjects(wrefs)
  }

  void integrationTestTask(String integrationTestTask) {
    if(integrationTestTaskAssigned) {
      log.warn '{}.integrationTestTask is already set to "{}", so "{}" is ignored', name, getIntegrationTestTask(), integrationTestTask
      return
    }
    integrationTestTask_ = integrationTestTask
    def thisTask = this
    getWebAppProjects().each { proj ->
      proj.tasks.all { t ->
        if(t.name == thisTask.integrationTestTask)
          thisTask.mustRunAfter t
        else if(GradleUtils.instanceOf(t, 'org.akhikhl.gretty.AppAfterIntegrationTestTask') && t.integrationTestTask == thisTask.integrationTestTask)
          thisTask.mustRunAfter t
      }
    }
    integrationTestTaskAssigned = true
  }

  void webapp(Map options = [:], w) {
    if(w instanceof Project)
      w = w.path
    webAppRefs[w] = options
  }
}
