/*
 * Gretty
 *
 * Copyright (C) 2013-2015 Andrey Hihlovskiy and contributors.
 *
 * See the file "LICENSE" for copying and usage permission.
 * See the file "CONTRIBUTORS" for complete list of contributors.
 */
package org.akhikhl.gretty

import groovy.json.JsonSlurper

/**
 *
 * @author akhikhl
 */
class GrettyStarter {

  private static final Set specialArgNames = ['httpPort', 'httpsPort', 'servicePort', 'statusPort', 'httpEnabled', 'httpsEnabled', 'httpIdleTimeout', 'httpsIdleTimeout'] as Set

  static void main(String[] args) {

    File basedir = new File(GrettyStarter.class.getProtectionDomain().getCodeSource().getLocation().getPath().toURI().getPath()).parentFile.parentFile

    def cli = new CliBuilder()
    cli.with {
      _ longOpt: 'runnerArg', args: 1, argName: 'runnerArg', 'arguments for Gretty Runner'
      raf longOpt: 'runnerArgFile', args: 1, argName: 'runnerArgFile', 'file with arguments for Gretty Runner'
      roa longOpt: 'runnerOverrideArgs', args: 0, argName: 'runnerOverrideArgs', 'override all arguments for Gretty Runner'
      rau longOpt: 'runnerArgURL', args: 1, argName: 'runnerArgURL', 'url with arguments for Gretty Runner (same file format)'
    }

    def options = cli.parse(args)

    String command = 'run'

    def cliArgs = options.arguments()
    if(cliArgs)
      command = cliArgs[0]

    Map config
    new File(basedir, 'conf/server.json').withReader {
      config = new JsonSlurper().parse(it)
    }

    ServerConfig sconfig = new ServerConfig()
    config.serverConfig.each { key, value ->
      sconfig[key] = value
    }

    if(options.runnerOverrideArgs)
      sconfig.jvmArgs = []
      
    List specialArgs = []

    if(options.runnerArgFile) {
      File f = new File(options.runnerArgFile)
      if(!f.isAbsolute())
        f = new File(basedir, options.runnerArgFile)
      if(!f.exists())
          throw new FileNotFoundException("File ${f.absolutePath} does not exist!")
      if(sconfig.jvmArgs == null)
        sconfig.jvmArgs = []
      for(String arg in f.text.split('\\s'))
        if(specialArgNames.find { arg.startsWith(it) })
          specialArgs.add(arg)
        else
          sconfig.jvmArgs.add(arg)
    }

    if(options.runnerArgURL) {
      URL urlSource = new URL(options.runnerArgURL)
      if(sconfig.jvmArgs == null)
        sconfig.jvmArgs = []
      for(String arg in urlSource.text.split('\\s'))
        if(specialArgNames.find { arg.startsWith(it) })
          specialArgs.add(arg)
        else
          sconfig.jvmArgs.add(arg)
    }

    if(options.runnerArgs) {
      if(sconfig.jvmArgs == null)
        sconfig.jvmArgs = []
      for(String arg in options.runnerArgs)
        if(specialArgNames.find { arg.startsWith(it) })
          specialArgs.add(arg)
        else
          sconfig.jvmArgs.add(arg)
    }

    ConfigUtils.complementProperties(sconfig, ServerConfig.getDefaultServerConfig(config.productName))
    
    for(String arg in specialArgs) {
      def (key, value) = arg.split('=')
      if(key.matches(~'.*Port') || key.matches(~'.*IdleTimeout'))
        sconfig[key] = value as int
      else if(key.matches(~'.*Enabled'))
        sconfig[key] = Boolean.valueOf(value)
    }

    def resolveFile = { f ->
      if(f) {
        File file = new File(f)
        if(!file.isAbsolute())
          file = new File(basedir, f)
        file.exists() ? file : null
      }
    }

    if(!(sconfig.sslKeyStorePath instanceof String) || !(sconfig.sslKeyStorePath.startsWith('classpath:')))
      sconfig.sslKeyStorePath = resolveFile(sconfig.sslKeyStorePath)
    if(!(sconfig.sslTrustStorePath instanceof String) || !(sconfig.sslTrustStorePath.startsWith('classpath:')))
      sconfig.sslTrustStorePath = resolveFile(sconfig.sslTrustStorePath)
    sconfig.realmConfigFile = resolveFile(sconfig.realmConfigFile)
    sconfig.serverConfigFile = resolveFile(sconfig.serverConfigFile)
    sconfig.logbackConfigFile = resolveFile(sconfig.logbackConfigFile)

    if(command == 'stop' || command == 'restart') {
      ServiceProtocol.send(sconfig.servicePort, command)
      return
    }

    List<WebAppConfig> wconfigs = []
    config.webApps.each { w ->
      WebAppConfig wconfig = new WebAppConfig()
      w.each { key, value ->
        wconfig[key] = value
      }
      wconfig.resourceBase = resolveFile(wconfig.resourceBase)
      wconfig.realmConfigFile = resolveFile(wconfig.realmConfigFile)
      wconfig.contextConfigFile = resolveFile(wconfig.contextConfigFile)
      if(wconfig.extraResourceBases)
        wconfig.extraResourceBases = wconfig.extraResourceBases.collect { resolveFile(it) }
      wconfigs.add(wconfig)
    }

    def launcherConfig = new LauncherConfig() {

      boolean getDebug() {
        false
      }

      int getDebugPort() {
        5005
      }

      boolean getDebugSuspend() {
        true
      }

      boolean getInteractive() {
        command != 'start'
      }

      boolean getManagedClassReload() {
        false
      }

      ServerConfig getServerConfig() {
        sconfig
      }

      String getStopCommand() {
        PlatformUtils.isWindows() ? 'stop.bat' : './stop.sh'
      }

      File getBaseDir() {
        basedir
      }

      boolean getProductMode() {
        true
      }

      WebAppClassPathResolver getWebAppClassPathResolver() {
      }

      Iterable<WebAppConfig> getWebAppConfigs() {
        wconfigs
      }
    }

    new StarterLauncher(basedir, config, launcherConfig).launch()
  }
}
