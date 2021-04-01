package com.mirantis.mk

/**
 *
 * KaaS Component Testing Utilities
 *
 */

/**
 * Check KaaS Core CICD feature flags
 * such triggers can be used in case of switching between pipelines,
 * conditions inside pipelines to reduce dependency on jenkins job builder and jenkins job templates itself
 *
 * @return      (map)[
 *                    ffNameEnabled: (bool) True/False
 *                   ]
 */
def checkCoreCIFeatureFlags() {
    def common = new com.mirantis.mk.Common()
    def ff = [
        build_artifacts_upgrade: false,
    ]

    def commitMsg = env.GERRIT_CHANGE_COMMIT_MESSAGE ? new String(env.GERRIT_CHANGE_COMMIT_MESSAGE.decodeBase64()) : ''
    if (commitMsg ==~ /(?s).*\[ci-build-artifacts-upgrade\].*/) {
        ff['build_artifacts_upgrade'] = true
    }

    common.infoMsg("Core ci feature flags status: ${ff}")
    return ff
}

/**
 * Determine scope of test suite against per-commit KaaS deployment based on keywords
 * Keyword list: https://gerrit.mcp.mirantis.com/plugins/gitiles/kaas/core/+/refs/heads/master/hack/ci-gerrit-keywords.md
 *
 * Used for components team to combine test-suites and forward desired parameters to kaas/core deployment jobs
 * Example scheme:
 * New CR pushed in kubernetes/lcm-ansible -> parsing it'cs commit body and combine test-suite -> trigger deployment jobs from kaas/core
 * manage test-suite through Jenkins Job Parameters
 *
 * @return      (map)[
 *                    deployChildEnabled: (bool) True if need to deploy child cluster during demo-run
 *                    runUie2eEnabled:    (bool) True if need to run ui-e2e cluster during demo-run
 *                   ]
 */
def checkDeploymentTestSuite() {
    def common = new com.mirantis.mk.Common()

    // Available triggers and its sane defaults
    def seedMacOs = env.SEED_MACOS ? env.SEED_MACOS.toBoolean() : false
    def deployChild = env.DEPLOY_CHILD_CLUSTER ? env.DEPLOY_CHILD_CLUSTER.toBoolean() : false
    def upgradeChild = env.UPGRADE_CHILD_CLUSTER ? env.UPGRADE_CHILD_CLUSTER.toBoolean() : false
    def attachBYO = env.ATTACH_BYO ? env.ATTACH_BYO.toBoolean() : false
    def upgradeBYO = env.UPGRADE_BYO ? env.UPGRADE_BYO.toBoolean() : false
    def upgradeMgmt = env.UPGRADE_MGMT_CLUSTER ? env.UPGRADE_MGMT_CLUSTER.toBoolean() : false
    def runUie2e = env.RUN_UI_E2E ? env.RUN_UI_E2E.toBoolean() : false
    def runMgmtConformance = env.RUN_MGMT_CFM ? env.RUN_MGMT_CFM.toBoolean() : false
    def runChildConformance = env.RUN_CHILD_CFM ? env.RUN_CHILD_CFM.toBoolean() : false
    def fetchServiceBinaries = env.FETCH_BINARIES_FROM_UPSTREAM ? env.FETCH_BINARIES_FROM_UPSTREAM.toBoolean() : false
    // multiregion configuration from env variable: comma-separated string in form $mgmt_provider,$regional_provider
    def multiregionalMappings = env.MULTIREGION_SETUP ? multiregionWorkflowParser(env.MULTIREGION_SETUP) : [
        enabled: false,
        managementLocation: '',
        regionLocation: '',
    ]

    // proxy customization
    def proxyConfig = [
        mgmtOffline: env.OFFLINE_MGMT_CLUSTER ? env.OFFLINE_MGMT_CLUSTER.toBoolean() : false,
        childOffline: env.OFFLINE_CHILD_CLUSTER ? env.OFFLINE_CHILD_CLUSTER.toBoolean() : false,
    ]

    // optional demo deployment customization
    def awsOnDemandDemo = env.ALLOW_AWS_ON_DEMAND ? env.ALLOW_AWS_ON_DEMAND.toBoolean() : false
    def vsphereOnDemandDemo = env.ALLOW_VSPHERE_ON_DEMAND ? env.ALLOW_VSPHERE_ON_DEMAND.toBoolean() : false
    def equinixOnAwsDemo = false
    def enableOSDemo = true
    def enableBMDemo = true

    def commitMsg = env.GERRIT_CHANGE_COMMIT_MESSAGE ? new String(env.GERRIT_CHANGE_COMMIT_MESSAGE.decodeBase64()) : ''
    if (commitMsg ==~ /(?s).*\[mgmt-proxy\].*/ || env.GERRIT_EVENT_COMMENT_TEXT ==~ /(?s).*mgmt-proxy.*/) {
        proxyConfig['mgmtOffline'] = true
        common.warningMsg('Forced running offline mgmt deployment, all provider CDN regions for mgmt deployment will be set to *public-ci* to verify proxy configuration')
    }
    if (commitMsg ==~ /(?s).*\[seed-macos\].*/ || env.GERRIT_EVENT_COMMENT_TEXT ==~ /(?s).*seed-macos.*/) {
        seedMacOs = true
    }
    if (commitMsg ==~ /(?s).*\[child-deploy\].*/ || env.GERRIT_EVENT_COMMENT_TEXT ==~ /(?s).*child-deploy.*/ || upgradeChild || runChildConformance) {
        deployChild = true
    }
    if (commitMsg ==~ /(?s).*\[child-upgrade\].*/ || env.GERRIT_EVENT_COMMENT_TEXT ==~ /(?s).*child-upgrade.*/) {
        deployChild = true
        upgradeChild = true
    }
    if (commitMsg ==~ /(?s).*\[byo-attach\].*/ || env.GERRIT_EVENT_COMMENT_TEXT ==~ /(?s).*byo-attach.*/) {
        attachBYO = true
    }
    if (commitMsg ==~ /(?s).*\[byo-upgrade\].*/ || env.GERRIT_EVENT_COMMENT_TEXT ==~ /(?s).*byo-upgrade.*/) {
        attachBYO = true
        upgradeBYO = true
    }
    if (commitMsg ==~ /(?s).*\[mgmt-upgrade\].*/ || env.GERRIT_EVENT_COMMENT_TEXT ==~ /(?s).*mgmt-upgrade.*/) {
        upgradeMgmt = true
    }
    if (commitMsg ==~ /(?s).*\[ui-e2e\].*/ || env.GERRIT_EVENT_COMMENT_TEXT ==~ /(?s).*ui-e2e.*/) {
        runUie2e = true
    }
    if (commitMsg ==~ /(?s).*\[mgmt-cfm\].*/ || env.GERRIT_EVENT_COMMENT_TEXT ==~ /(?s).*mgmt-cfm.*/) {
        runMgmtConformance = true
    }
    if (commitMsg ==~ /(?s).*\[child-cfm\].*/ || env.GERRIT_EVENT_COMMENT_TEXT ==~ /(?s).*child-cfm.*/) {
        runChildConformance = true
        deployChild = true
    }
    if (commitMsg ==~ /(?s).*\[child-offline\].*/ || env.GERRIT_EVENT_COMMENT_TEXT ==~ /(?s).*child-offline.*/) {
        proxyConfig['childOffline'] = true
        deployChild = true
    }
    if (commitMsg ==~ /(?s).*\[fetch.*binaries\].*/ || env.GERRIT_EVENT_COMMENT_TEXT ==~ /(?s).*fetch.*binaries.*/) {
        fetchServiceBinaries = true
    }
    if (commitMsg ==~ /(?s).*\[equinix-demo\].*/ || env.GERRIT_EVENT_COMMENT_TEXT ==~ /(?s).*equinix-demo.*/) {
        equinixOnAwsDemo = true
        common.warningMsg('Forced running child cluster deployment on EQUINIX METAL provider based on AWS management cluster, triggered on patchset using custom keyword: \'[equinix-demo]\' ')
    }
    if (commitMsg ==~ /(?s).*\[aws-demo\].*/ || env.GERRIT_EVENT_COMMENT_TEXT ==~ /(?s).*aws-demo.*/ || attachBYO || upgradeBYO || seedMacOs || equinixOnAwsDemo) {
        awsOnDemandDemo = true
        if (attachBYO || upgradeBYO || seedMacOs || equinixOnAwsDemo) {
            common.warningMsg('Forced running additional kaas deployment with AWS provider, due applied trigger cross dependencies, follow docs to clarify info')
        }
    }
    if (commitMsg ==~ /(?s).*\[vsphere-demo\].*/ || env.GERRIT_EVENT_COMMENT_TEXT ==~ /(?s).*vsphere-demo.*/) {
        vsphereOnDemandDemo = true
        common.warningMsg('Forced running additional kaas deployment with VSPHERE provider, triggered on patchset using custom keyword: \'[vsphere-demo]\' ')
    }
    if (commitMsg ==~ /(?s).*\[disable-os-demo\].*/ || env.GERRIT_EVENT_COMMENT_TEXT ==~ /(?s).*disable-os-demo\.*/) {
        enableOSDemo = false
        common.errorMsg('Openstack demo deployment will be aborted, VF -1 will be set')
    }

    if (commitMsg ==~ /(?s).*\[disable-bm-demo\].*/ || env.GERRIT_EVENT_COMMENT_TEXT ==~ /(?s).*disable-bm-demo\.*/) {
        enableBMDemo = false
        common.errorMsg('BM demo deployment will be aborted, VF -1 will be set')
    }

    // TODO (vnaumov) remove below condition after moving all releases to UCP
    def ucpChildMatches = (commitMsg =~ /(\[child-ucp\s*ucp-.*?\])/)
    if (ucpChildMatches.size() > 0) {
        deployChild = true
        common.warningMsg('Forced UCP based child deployment triggered on patchset using custom keyword: \'[child-ucp ucp-5-1-0-3-3-0-example]\' ')

        // TODO(vnaumov) delete after ucp upgrades support
        common.errorMsg('Child upgrade test will be skipped, UCP upgrades temporally disabled')
        upgradeChild = false
    }

    // multiregional tests
    def multiRegionalMatches = (commitMsg =~ /(\[multiregion\s*.*?\])/)
    if (multiRegionalMatches.size() > 0) {
        multiregionalMappings = multiregionWorkflowParser(multiRegionalMatches)
    }
    switch (multiregionalMappings['managementLocation']) {
        case 'aws':
            common.warningMsg('Forced running additional kaas deployment with AWS provider according multiregional demo request')
            awsOnDemandDemo = true

            if (multiregionalMappings['regionLocation'] != 'aws' && seedMacOs) { // macstadium seed node has access only to *public* providers
                error('incompatible triggers: [seed-macos] and multiregional deployment based on *private* regional provider cannot be applied simultaneously')
            }
            break
        case 'os':
            if (enableOSDemo == false) {
                error('incompatible triggers: [disable-os-demo] and multiregional deployment based on OSt management region cannot be applied simultaneously')
            }
            break
    }

    // CDN configuration
    def cdnConfig = [
        mgmt: [
            openstack:  (proxyConfig['mgmtOffline'] == true) ? 'public-ci' : 'internal-ci',
            vsphere:  (proxyConfig['mgmtOffline'] == true) ? 'public-ci' : 'internal-ci',
            aws: 'public-ci',
        ],
    ]

    // calculate weight of current demo run to manage lockable resources
    def demoWeight = (deployChild) ? 2 : 1 // management = 1, child = 1

    common.infoMsg("""
        CDN deployment configuration: ${cdnConfig}
        MCC offline deployment configuration: ${proxyConfig}
        Use MacOS node as seed: ${seedMacOs}
        Child cluster deployment scheduled: ${deployChild}
        Child cluster release upgrade scheduled: ${upgradeChild}
        Child conformance testing scheduled: ${runChildConformance}
        BYO cluster attachment scheduled: ${attachBYO}
        Attached BYO cluster upgrade test scheduled: ${upgradeBYO}
        Mgmt cluster release upgrade scheduled: ${upgradeMgmt}
        Mgmt conformance testing scheduled: ${runMgmtConformance}
        Mgmt UI e2e testing scheduled: ${runUie2e}
        AWS provider deployment scheduled: ${awsOnDemandDemo}
        VSPHERE provider deployment scheduled: ${vsphereOnDemandDemo}
        EQUINIX child cluster deployment scheduled: ${equinixOnAwsDemo}
        OS provider deployment scheduled: ${enableOSDemo}
        BM provider deployment scheduled: ${enableBMDemo}
        Multiregional configuration: ${multiregionalMappings}
        Service binaries fetching scheduled: ${fetchServiceBinaries}
        Current weight of the demo run: ${demoWeight} (Used to manage lockable resources)
        Triggers: https://gerrit.mcp.mirantis.com/plugins/gitiles/kaas/core/+/refs/heads/master/hack/ci-gerrit-keywords.md""")
    return [
        cdnConfig                  : cdnConfig,
        proxyConfig                : proxyConfig,
        useMacOsSeedNode           : seedMacOs,
        deployChildEnabled         : deployChild,
        upgradeChildEnabled        : upgradeChild,
        runChildConformanceEnabled : runChildConformance,
        attachBYOEnabled           : attachBYO,
        upgradeBYOEnabled          : upgradeBYO,
        upgradeMgmtEnabled         : upgradeMgmt,
        runUie2eEnabled            : runUie2e,
        runMgmtConformanceEnabled  : runMgmtConformance,
        fetchServiceBinariesEnabled: fetchServiceBinaries,
        awsOnDemandDemoEnabled     : awsOnDemandDemo,
        vsphereOnDemandDemoEnabled : vsphereOnDemandDemo,
        equinixOnAwsDemoEnabled    : equinixOnAwsDemo,
        bmDemoEnabled              : enableBMDemo,
        osDemoEnabled              : enableOSDemo,
        multiregionalConfiguration : multiregionalMappings,
        demoWeight                 : demoWeight]
}

/**
 * Determine management and regional setup for demo workflow scenario
 *
 *
 * @param:        keyword (string) string , represents keyworkd trigger, specified in gerrit commit body, like `[multiregion aws,os]`
                                   or Jenkins environment string variable in form like 'aws,os'
 * @return        (map)[
                          enabled: (bool),
 *                        managementLocation: (string), //aws,os
 *                        regionLocation: (string), //aws,os
 *                     ]
 */
def multiregionWorkflowParser(keyword) {
    def common = new com.mirantis.mk.Common()
    def supportedManagementProviders = ['os', 'aws', 'vsphere']
    def supportedRegionalProviders = ['os', 'vsphere']

    def clusterTypes = ''
    if (keyword.toString().contains('multiregion')) {
        common.infoMsg('Multiregion definition configured via gerrit keyword trigger')
        clusterTypes = keyword[0][0].split('multiregion')[1].replaceAll('[\\[\\]]', '').trim().split(',')
    } else {
        common.infoMsg('Multiregion definition configured via environment variable')
        clusterTypes = keyword.trim().split(',')
    }

    if (clusterTypes.size() != 2) {
        error("Incorrect regions definiton, valid scheme: [multiregion ${management}, ${region}], got: ${clusterTypes}")
    }

    def desiredManagementProvider = clusterTypes[0].trim()
    def desiredRegionalProvider = clusterTypes[1].trim()
    if (! supportedManagementProviders.contains(desiredManagementProvider) || ! supportedRegionalProviders.contains(desiredRegionalProvider)) {
        error("""unsupported management <-> regional bundle, available options:
              management providers list - ${supportedManagementProviders}
              regional providers list - ${supportedRegionalProviders}""")
    }

    return [
        enabled: true,
        managementLocation: desiredManagementProvider,
        regionLocation: desiredRegionalProvider,
    ]
}

/**
 * Determine if custom si tests/pipelines refspec forwarded from gerrit change request

 * Keyword list: https://gerrit.mcp.mirantis.com/plugins/gitiles/kaas/core/+/refs/heads/master/hack/ci-gerrit-keywords.md
 * Used for components team to test component changes w/ custom SI refspecs using kaas/core deployment jobs
 * Example scheme:
 * New CR pushed in kubernetes/lcm-ansible -> parsing it's commit body and get custom test refspecs -> trigger deployment jobs from kaas/core
 * manage refspecs through Jenkins Job Parameters
 *
 * @return (map)[*                    siTests: (string) final refspec for si-tests
 *                    siPipelines: (string) final refspec for si-pipelines
 *                   ]
 */
def checkCustomSIRefspec() {
    def common = new com.mirantis.mk.Common()

    // Available triggers and its sane defaults
    def siTestsRefspec = env.SI_TESTS_REFSPEC ?: 'master'
    def siPipelinesRefspec = env.SI_PIPELINES_REFSPEC ?: 'master'
    def siTestsDockerImage = env.SI_TESTS_DOCKER_IMAGE ?: 'docker-dev-kaas-local.docker.mirantis.net/mirantis/kaas/si-test'
    def siTestsDockerImageTag = env.SI_TESTS_DOCKER_IMAGE_TAG ?: 'master'
    def commitMsg = env.GERRIT_CHANGE_COMMIT_MESSAGE ? new String(env.GERRIT_CHANGE_COMMIT_MESSAGE.decodeBase64()) : ''

    def siTestMatches = (commitMsg =~ /(\[si-tests-ref\s*refs\/changes\/.*?\])/)
    def siPipelinesMatches = (commitMsg =~ /(\[si-pipelines-ref\s*refs\/changes\/.*?\])/)

    if (siTestMatches.size() > 0) {
        siTestsRefspec = siTestMatches[0][0].split('si-tests-ref')[1].replaceAll('[\\[\\]]', '').trim()
        siTestsDockerImage = "docker-review-local.docker.mirantis.net/review/kaas-si-test-${siTestsRefspec.split('/')[-2]}"
        siTestsDockerImageTag = siTestsRefspec.split('/')[-1]
    }
    if (siPipelinesMatches.size() > 0) {
        siPipelinesRefspec = siPipelinesMatches[0][0].split('si-pipelines-ref')[1].replaceAll('[\\[\\]]', '').trim()
    }

    common.infoMsg("""
        kaas/si-pipelines will be fetched from: ${siPipelinesRefspec}
        kaas/si-tests will be fetched from: ${siTestsRefspec}
        kaas/si-tests as dockerImage will be fetched from: ${siTestsDockerImage}:${siTestsDockerImageTag}
        Keywords: https://gerrit.mcp.mirantis.com/plugins/gitiles/kaas/core/+/refs/heads/master/hack/ci-gerrit-keywords.md""")
    return [siTests: siTestsRefspec, siPipelines: siPipelinesRefspec, siTestsDockerImage: siTestsDockerImage, siTestsDockerImageTag: siTestsDockerImageTag]
}

/**
 * Parse additional configuration for kaas component CICD repo
 * @param configurationFile    (str) path to configuration file in yaml format
 *
 * @return                     (map)[     siTestsFeatureFlags (string) dedicated feature flags that will be used in SI tests,
 *                                  ]
 */
def parseKaaSComponentCIParameters(configurationFile){
   def common = new com.mirantis.mk.Common()
   def ciConfig = readYaml file: configurationFile
   def ciSpec = [
   siTestsFeatureFlags: env.SI_TESTS_FEATURE_FLAGS ?: '',
   ]

   if (ciConfig.containsKey('si-tests-feature-flags')) {
       common.infoMsg("""SI tests feature flags customization detected,
           results will be merged with existing flags: [${ciSpec['siTestsFeatureFlags']}] identification...""")

       def ffMeta = ciSpec['siTestsFeatureFlags'].tokenize(',').collect { it.trim() }
       ffMeta.addAll(ciConfig['si-tests-feature-flags'])

       ciSpec['siTestsFeatureFlags'] = ffMeta.unique().join(',')
       common.infoMsg("SI tests custom feature flags: ${ciSpec['siTestsFeatureFlags']}")
   }

   common.infoMsg("""Additional ci configuration parsed successfully:
       siTestsFeatureFlags: ${ciSpec['siTestsFeatureFlags']}""")
   return ciSpec
}

/**
 * Determine if custom kaas core/pipelines refspec forwarded from gerrit change request

 * Keyword list: https://gerrit.mcp.mirantis.com/plugins/gitiles/kaas/core/+/refs/heads/master/hack/ci-gerrit-keywords.md
 * Used for components team to test component changes w/ custom Core refspecs using kaas/core deployment jobs
 * Example scheme:
 * New CR pushed in kubernetes/lcm-ansible -> parsing it's commit body and get custom test refspecs -> trigger deployment jobs from kaas/core
 * manage refspecs through Jenkins Job Parameters
 *
 * @return          (map)[     core: (string) final refspec for kaas/core
 *                             corePipelines: (string) final refspec for pipelines in kaas/core
 *                       ]
 */
def checkCustomCoreRefspec() {
    def common = new com.mirantis.mk.Common()

    // Available triggers and its sane defaults
    def coreRefspec = env.KAAS_CORE_REFSPEC ?: 'master'
    // by default using value of GERRIT_REFSPEC parameter in *kaas/core jobs*
    def corePipelinesRefspec = env.KAAS_PIPELINE_REFSPEC ?: '\$GERRIT_REFSPEC'
    def commitMsg = env.GERRIT_CHANGE_COMMIT_MESSAGE ? new String(env.GERRIT_CHANGE_COMMIT_MESSAGE.decodeBase64()) : ''

    def coreMatches = (commitMsg =~ /(\[core-ref\s*refs\/changes\/.*?\])/)
    def corePipelinesMatches = (commitMsg =~ /(\[core-pipelines-ref\s*refs\/changes\/.*?\])/)

    if (coreMatches.size() > 0) {
        coreRefspec = coreMatches[0][0].split('core-ref')[1].replaceAll('[\\[\\]]', '').trim()
    }
    if (corePipelinesMatches.size() > 0) {
        corePipelinesRefspec = corePipelinesMatches[0][0].split('core-pipelines-ref')[1].replaceAll('[\\[\\]]', '').trim()
    }

    common.infoMsg("""
        kaas/core will be fetched from: ${coreRefspec}
        kaas/core pipelines will be fetched from: ${corePipelinesRefspec}
        Keywords: https://gerrit.mcp.mirantis.com/plugins/gitiles/kaas/core/+/refs/heads/master/hack/ci-gerrit-keywords.md""")
    return [core: coreRefspec, corePipelines: corePipelinesRefspec]
}


/**
 * generate Jenkins Parameter objects from from text parameter with additonal kaas core context
 * needed to forward inside kaas core set of jobs
 *
 * @param           context (string) Representation of the string enviroment variables needed for kaas core jobs in yaml format
 * @return          (list)[    string(name: '', value: ''),
 *                       ]
 */
def generateKaaSVarsFromContext(context) {
    def common = new com.mirantis.mk.Common()
    def parameters = []
    def config = readYaml text: context

    config.each { k,v ->
        common.infoMsg("Custom KaaS Core context parameter: ${k}=${v}")
        parameters.add(string(name: k, value: v))
    }

    return parameters
}

/**
 * Trigger KaaS demo jobs based on AWS/OS providers with customized test suite, parsed from external sources (gerrit commit/jj vars)
 * Keyword list: https://gerrit.mcp.mirantis.com/plugins/gitiles/kaas/core/+/refs/heads/master/hack/ci-gerrit-keywords.md
 * Used for components team to test component changes w/ customized SI tests/refspecs using kaas/core deployment jobs
 *
 * @param:        component (string) component name [iam, lcm, stacklight]
 * @param:        patchSpec (string) Patch for kaas/cluster releases in json format
 * @param:        configurationFile (string) Additional file for component repo CI config in yaml format
 */
def triggerPatchedComponentDemo(component, patchSpec = '', configurationFile = '.ci-parameters.yaml', coreContext = '') {
    def common = new com.mirantis.mk.Common()
    // Determine if custom trigger keywords forwarded from gerrit
    def triggers = checkDeploymentTestSuite()
    // Determine SI refspecs
    def siRefspec = checkCustomSIRefspec()
    // Determine Core refspecs
    def coreRefspec = checkCustomCoreRefspec()

    // Determine component repo ci configuration
    def ciSpec = [:]
    def componentFeatureFlags = env.SI_TESTS_FEATURE_FLAGS ?: ''
    if (fileExists(configurationFile)) {
        common.infoMsg('Component CI configuration file detected, parsing...')
        ciSpec = parseKaaSComponentCIParameters(configurationFile)
        componentFeatureFlags = ciSpec['siTestsFeatureFlags']
    } else {
        common.warningMsg('''Component CI configuration file is not exists,
            several code-management features may be unavailable,
            follow https://mirantis.jira.com/wiki/spaces/QA/pages/2310832276/SI-tests+feature+flags#%5BUpdated%5D-Using-a-feature-flag
            to create the configuration file''')
    }
    def jobs = [:]
    def parameters = [
        string(name: 'GERRIT_REFSPEC', value: coreRefspec.core),
        string(name: 'KAAS_PIPELINE_REFSPEC', value: coreRefspec.corePipelines),
        string(name: 'SI_TESTS_REFSPEC', value: siRefspec.siTests),
        string(name: 'SI_TESTS_FEATURE_FLAGS', value: componentFeatureFlags),
        string(name: 'SI_TESTS_DOCKER_IMAGE', value: siRefspec.siTestsDockerImage),
        string(name: 'SI_TESTS_DOCKER_IMAGE_TAG', value: siRefspec.siTestsDockerImageTag),
        string(name: 'SI_PIPELINES_REFSPEC', value: siRefspec.siPipelines),
        string(name: 'CUSTOM_RELEASE_PATCH_SPEC', value: patchSpec),
        booleanParam(name: 'OFFLINE_MGMT_CLUSTER', value: triggers.proxyConfig['mgmtOffline']),
        booleanParam(name: 'OFFLINE_CHILD_CLUSTER', value: triggers.proxyConfig['childOffline']),
        booleanParam(name: 'SEED_MACOS', value: triggers.useMacOsSeedNode),
        booleanParam(name: 'UPGRADE_MGMT_CLUSTER', value: triggers.upgradeMgmtEnabled),
        booleanParam(name: 'RUN_UI_E2E', value: triggers.runUie2eEnabled),
        booleanParam(name: 'RUN_MGMT_CFM', value: triggers.runMgmtConformanceEnabled),
        booleanParam(name: 'DEPLOY_CHILD_CLUSTER', value: triggers.deployChildEnabled),
        booleanParam(name: 'UPGRADE_CHILD_CLUSTER', value: triggers.upgradeChildEnabled),
        booleanParam(name: 'ATTACH_BYO', value: triggers.attachBYOEnabled),
        booleanParam(name: 'UPGRADE_BYO', value: triggers.upgradeBYOEnabled),
        booleanParam(name: 'RUN_CHILD_CFM', value: triggers.runChildConformanceEnabled),
        booleanParam(name: 'ALLOW_AWS_ON_DEMAND', value: triggers.awsOnDemandDemoEnabled),
        booleanParam(name: 'ALLOW_VSPHERE_ON_DEMAND', value: triggers.vsphereOnDemandDemoEnabled),
    ]

    // customize multiregional demo
    if (triggers.multiregionalConfiguration.enabled) {
        parameters.add(string(name: 'MULTIREGION_SETUP',
                              value: "${triggers.multiregionalConfiguration.managementLocation},${triggers.multiregionalConfiguration.regionLocation}"
                              ))
    }

    // Determine component team custom context
    if (coreContext != '') {
        common.infoMsg('Additional KaaS Core context detected, will be forwarded into kaas core cicd...')
        def additionalParameters = generateKaaSVarsFromContext(coreContext)
        parameters.addAll(additionalParameters)
    }

    def jobResults = []
    if (triggers.osDemoEnabled) {
        jobs["kaas-core-openstack-patched-${component}"] = {
            try {
                common.infoMsg('Deploy: patched KaaS demo with Openstack provider')
                os_job_info = build job: "kaas-testing-core-openstack-workflow-${component}", parameters: parameters, wait: true
                def build_description = os_job_info.getDescription()
                def build_result = os_job_info.getResult()
                jobResults.add(build_result)

                if (build_description) {
                    currentBuild.description += build_description
                }
            } finally {
                common.infoMsg('Patched KaaS demo with Openstack provider finished')
            }
        }
    }
    if (triggers.awsOnDemandDemoEnabled) {
        common.infoMsg('AWS demo triggered, need to sync artifacts in the public-ci cdn..')
        switch (component) {
            case 'iam':
                build job: 'cdn-binary-dev-replication-iam', propagate: true, wait: true
                break
            case 'lcm':
                build job: 'cdn-binary-dev-replication-lcm', propagate: true, wait: true
                break
        }

        jobs["kaas-core-aws-patched-${component}"] = {
            try {
                common.infoMsg('Deploy: patched KaaS demo with AWS provider')
                aws_job_info = build job: "kaas-testing-core-aws-workflow-${component}", parameters: parameters, wait: true
                def build_description = aws_job_info.getDescription()
                def build_result = aws_job_info.getResult()
                jobResults.add(build_result)

                if (build_description) {
                    currentBuild.description += build_description
                }
            } finally {
                common.infoMsg('Patched KaaS demo with AWS provider finished')
            }
        }
    }
    if (triggers.vsphereOnDemandDemoEnabled) {
        jobs["kaas-core-vsphere-patched-${component}"] = {
            try {
                common.infoMsg('Deploy: patched KaaS demo with VSPHERE provider')
                vsphere_job_info = build job: "kaas-testing-core-vsphere-workflow-${component}", parameters: parameters, wait: true
                def build_description = vsphere_job_info.getDescription()
                def build_result = vsphere_job_info.getResult()
                jobResults.add(build_result)

                if (build_description) {
                    currentBuild.description += build_description
                }
            } finally {
                common.infoMsg('Patched KaaS demo with VSPHERE provider finished')
            }
        }
    }

    common.infoMsg('Trigger KaaS demo deployments according to defined provider set')
    if (jobs.size() == 0) {
        error('No demo jobs matched with keywords, execution will be aborted, at least 1 provider should be enabled')
    }
    // Limit build concurency workaround examples: https://issues.jenkins-ci.org/browse/JENKINS-44085
    parallel jobs

    if (jobResults.contains('FAILURE')) {
        common.infoMsg('One of parallel downstream jobs is failed, mark executor job as failed')
        currentBuild.result = 'FAILURE'
    }
}


