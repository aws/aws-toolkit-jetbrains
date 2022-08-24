# _1.50_ (2022-08-23)
- **(Bug Fix)** Fix opening toolwindow tabs in incorrect thread in Cloudwatch Logs
- **(Bug Fix)** Fix hitting enter inside braces will produce an extra newline ([#3270](https://github.com/aws/aws-toolkit-jetbrains/issues/3270))
- **(Deprecation)** Remove support for deprecated Lambda runtime Python 3.6
- **(Removal)** Removed support for 2021.2.x IDEs

# _1.49_ (2022-08-11)
- **(Bug Fix)** Fix IllegalCallableAccessException thrown in several UI panels ([#3228](https://github.com/aws/aws-toolkit-jetbrains/issues/3228))
- **(Bug Fix)** Fix to stop showing CodeWhisperer's welcome page every time on project start
- **(Deprecation)** An upcoming release will remove support for IDEs based on the 2021.2 platform

# _1.48_ (2022-07-26)
- **(Bug Fix)** Fix to display appropriate error messaging for filtering Cloudwatch Streams using search patterns failures

# _1.47_ (2022-07-08)
- **(Removal)** Remove Cloud Debugging of ECS Services (beta)

# _1.46_ (2022-06-28)
- **(Feature)** Nodejs16.x Lambda runtime support
- **(Bug Fix)** Fix broken user UI due to 'Enter' handler override ([#3193](https://github.com/aws/aws-toolkit-jetbrains/issues/3193))
- **(Bug Fix)** Fix SSM plugin install on deb/rpm systems ([#3130](https://github.com/aws/aws-toolkit-jetbrains/issues/3130))

# _1.45_ (2022-06-23)
- **(Feature)** [CodeWhisperer](https://aws.amazon.com/codewhisperer) uses machine learning to generate code suggestions from the existing code and comments in your IDE. Supported languages include: Java, Python, and JavaScript.
- **(Feature)** Added 2022.2 support
- **(Bug Fix)** Fix .NET Lambda debugging regression in 2022.1.1
- **(Removal)** Removed support for 2021.1.x IDEs

# _1.44_ (2022-06-01)
- **(Feature)** Add warning to indicate time delay in SQS queue deletion
- **(Bug Fix)** Fixed issue with uncaught exception in resource cache ([#3098](https://github.com/aws/aws-toolkit-jetbrains/issues/3098))
- **(Bug Fix)** Don't attempt to setup run configurations for test code ([#3075](https://github.com/aws/aws-toolkit-jetbrains/issues/3075))
- **(Bug Fix)** Fix toolWindow not running in EDT
- **(Bug Fix)** Handle Lambda pending states while updating function ([#2984](https://github.com/aws/aws-toolkit-jetbrains/issues/2984))
- **(Bug Fix)** Fix modality issue when opening a CloudWatch log stream in editor ([#2991](https://github.com/aws/aws-toolkit-jetbrains/issues/2991))
- **(Bug Fix)** Workaround regression with ARN console navigation in JSON files
- **(Bug Fix)** Fix 'The project directory does not exist!' when creating SAM/Gradle projects when the Android plugin is also installed
- **(Deprecation)** An upcoming release will remove support for IDEs based on the 2021.1 platform

# _1.43_ (2022-04-14)
- **(Bug Fix)** Fix regression in DataGrip 2022.1 caused by new APIs in the platform ([#3125](https://github.com/aws/aws-toolkit-jetbrains/issues/3125))

# _1.42_ (2022-04-13)
- **(Feature)** Add support for 2022.1

# _1.41_ (2022-03-25)
- **(Feature)** Adding Go (Golang) as a supported language for code binding generation through the EventBridge Schemas service

# _1.40_ (2022-03-07)
- **(Bug Fix)** Fix logged error due to ARN contributor taking too long ([#3085](https://github.com/aws/aws-toolkit-jetbrains/issues/3085))

# _1.39_ (2022-03-03)
- **(Feature)** Added in 1.37: The toolkit will now offer to open ARNs present in your code editor in your browser
- **(Feature)** Added support for .NET 6 runtime for creating and debugging SAM functions
- **(Bug Fix)** Fix issue where console federation with long-term credentails results in session with no permissions

# _1.38_ (2022-02-17)
- **(Bug Fix)** Fix StringIndexOutOfBoundsException ([#3025](https://github.com/aws/aws-toolkit-jetbrains/issues/3025))
- **(Bug Fix)** Fix regression preventing ECR repository creation
- **(Bug Fix)** Fix Lambda run configuration exception while setting handler architecture
- **(Bug Fix)** Fix image-based Lambda debugging for Python 3.6
- **(Removal)** Removed support for 2020.3.x IDEs

# _1.37_ (2022-01-06)
- **(Feature)** Add SAM Lambda ARM support
- **(Bug Fix)** Fix plugin deprecation warning in DynamoDB viewer ([#2987](https://github.com/aws/aws-toolkit-jetbrains/issues/2987))
- **(Deprecation)** An upcoming release will remove support for IDEs based on the 2020.3 platform

# _1.36_ (2021-11-23)

# _1.35_ (2021-11-18)
- **(Feature)** Respect the `duration_seconds` property when assuming a role if set on the profile
- **(Feature)** Added 2021.3 support
- **(Feature)** Added support for AWS profiles that use the `credential_source` key
- **(Bug Fix)** Fix Python Lambda gutter icons not generating handler paths relative to the requirements.txt file ([#2853](https://github.com/aws/aws-toolkit-jetbrains/issues/2853))
- **(Bug Fix)** Fix file changes not being saved before running Local Lambda run configurations ([#2889](https://github.com/aws/aws-toolkit-jetbrains/issues/2889))
- **(Bug Fix)** Fix incorrect behavior with RDS Secrets Manager Auth when SSH tunneling is enabled ([#2781](https://github.com/aws/aws-toolkit-jetbrains/issues/2781))
- **(Bug Fix)** Fix copying out of the DynamoDB table viewer copying the in-memory representation instead of displayed value
- **(Bug Fix)** Fix error about write actions when opening files from the S3 browser ([#2913](https://github.com/aws/aws-toolkit-jetbrains/issues/2913))
- **(Bug Fix)** Fix NullPointerException on combobox browse components ([#2866](https://github.com/aws/aws-toolkit-jetbrains/issues/2866))
- **(Removal)** Dropped support for the no longer supported Lambda runtime .NET Core 2.1

# _1.34_ (2021-10-21)
- **(Bug Fix)** Fix issue in Resources where some S3 Buckets fail to open
- **(Bug Fix)** Fix null exception when view documentation action executed for types with missing doc urls
- **(Bug Fix)** Fix uncaught exception when a resource does not support LIST in a certain region.

# _1.33_ (2021-10-14)
- **(Feature)** Surface read-only support for hundreds of resources under the Resources node in the AWS Explorer
- **(Feature)** Amazon DynamoDB table viewer
- **(Bug Fix)** Changed error message 'Command did not exist successfully' to 'Command did not exit successfully'
- **(Bug Fix)** Fixed spelling and grammar in MessagesBundle.properties
- **(Bug Fix)** Fix not being able to start Rider debugger against a Lambda running on a host ARM machine
- **(Bug Fix)** Fix SSO login not being triggered when the auth code is invalid ([#2796](https://github.com/aws/aws-toolkit-jetbrains/issues/2796))
- **(Removal)** Removed support for 2020.2.x IDEs
- **(Removal)** Dropped support for the no longer supported Lambda runtime Python 2.7
- **(Removal)** Dropped support for the no longer supported Lambda runtime Node.js 10.x

# _1.32_ (2021-09-07)
- **(Bug Fix)** Fix IDE error about context.module being null ([#2776](https://github.com/aws/aws-toolkit-jetbrains/issues/2776))
- **(Bug Fix)** Fix NullPointerException calling isInTestSourceContent ([#2752](https://github.com/aws/aws-toolkit-jetbrains/issues/2752))

# _1.31_ (2021-08-17)
- **(Feature)** Add support for Python 3.9 Lambdas
- **(Bug Fix)** Fix regression in SAM run configurations using file-based input ([#2762](https://github.com/aws/aws-toolkit-jetbrains/issues/2762))
- **(Bug Fix)** Fix CloudWatch sorting ([#2737](https://github.com/aws/aws-toolkit-jetbrains/issues/2737))

# _1.30_ (2021-08-05)
- **(Feature)** Add ability to view bucket by entering bucket name/URI
- **(Bug Fix)** Fix CWL last event sorting ([#2737](https://github.com/aws/aws-toolkit-jetbrains/issues/2737))
- **(Bug Fix)** Fix Go Lambda handler resolving into Go standard library ([#2730](https://github.com/aws/aws-toolkit-jetbrains/issues/2730))
- **(Bug Fix)** Fix `ActionPlaces.isPopupPlace` error after opening the AWS connection settings menu ([#2736](https://github.com/aws/aws-toolkit-jetbrains/issues/2736))
- **(Bug Fix)** Fix some warnings due to slow operations on EDT ([#2735](https://github.com/aws/aws-toolkit-jetbrains/issues/2735))
- **(Bug Fix)** Fix Java Lambda run marker issues and disable runmarker processing in tests and language-injected text fragments

# _1.29_ (2021-07-20)
- **(Feature)** When uploading a file to S3, the content type is now set accoriding to the files extension
- **(Bug Fix)** Fix being unable to update Lambda configuration if the Image packaging type

# _1.28_ (2021-07-12)
- **(Breaking Change)** Python 2.7 Lambda template removed from New Project Wizard
- **(Feature)** Adding the ability to inject credentials/region into existing IntelliJ IDEA and PyCharm Run Configurations (e.g Application, JUnit, Python, PyTest). This requires experiments `aws.feature.javaRunConfigurationExtension` / `aws.feature.pythonRunConfigurationExtension`, see [Enabling Experiments](https://github.com/aws/aws-toolkit-jetbrains/blob/master/README.md#experimental-features)
- **(Feature)** Add support for updating tags during SAM deployment
- **(Feature)** (Experimental) Adding ability to create a local terminal using the currently selected AWS connection (experiment ID `aws.feature.connectedLocalTerminal`, see [Enabling Experiments](https://github.com/aws/aws-toolkit-jetbrains/blob/master/README.md#experimental-features)) [#2151](https://github.com/aws/aws-toolkit-jetbrains/issues/2151)
- **(Feature)** Add support for pulling images from ECR
- **(Bug Fix)** Fix missing text in the View S3 bucket with prefix dialog
- **(Bug Fix)** Improved performance of listing S3 buckets in certain situations
- **(Bug Fix)** Fix copying action in CloudWatch Logs Stream and Event Time providing epoch time instead of displayed value
- **(Bug Fix)** Fix using message bus after project has been closed (Fixes [#2615](https://github.com/aws/aws-toolkit-jetbrains/issues/2615))
- **(Bug Fix)** Fix S3 bucket viewer actions being triggered by short cuts even if it is not focused
- **(Bug Fix)** Don't show Lambda run configuration suggestions on Go test code
- **(Bug Fix)** Fix being unable to create Python 3.8 Image-based Lambdas in New Project wizard
- **(Bug Fix)** Fixed showing templates that were not for Image-based Lambdas when Image is selected in New Project wizard
- **(Deprecation)** An upcoming release will remove support for IDEs based on the 2020.2 platform

# _1.27_ (2021-05-24)
- **(Feature)** Add support for AppRunner. Create/delete/pause/resume/deploy and view logs for your AppRunner services.
- **(Feature)** Add support for building and pushing local images to ECR
- **(Feature)** Add support for running/debugging Typescript Lambdas
- **(Bug Fix)** Fix Rider locking up when right clicking a Lambda in the AWS Explorer with a dotnet runtime in 2021.1
- **(Bug Fix)** While debugging a Lambda function locally, make sure stopping the debugger will always stop the underlying SAM cli process ([#2564](https://github.com/aws/aws-toolkit-jetbrains/issues/2564))

# _1.26_ (2021-04-14)
- **(Feature)** Add support for creating/debugging Golang Lambdas ([#649](https://github.com/aws/aws-toolkit-jetbrains/issues/649))
- **(Bug Fix)** Fix breaking run configuration gutter icons when the IDE has no languages installed that support Lambda local runtime ([#2504](https://github.com/aws/aws-toolkit-jetbrains/issues/2504))
- **(Bug Fix)** Fix issue preventing deployment of CloudFormation templates with empty values ([#1498](https://github.com/aws/aws-toolkit-jetbrains/issues/1498))
- **(Bug Fix)** Fix cloudformation stack events failing to update after reaching a final state ([#2519](https://github.com/aws/aws-toolkit-jetbrains/issues/2519))
- **(Bug Fix)** Fix the Local Lambda run configuration always reseting the environemnt variables to defaults when using templates ([#2509](https://github.com/aws/aws-toolkit-jetbrains/issues/2509))
- **(Bug Fix)** Fix being able to interact with objects from deleted buckets ([#1601](https://github.com/aws/aws-toolkit-jetbrains/issues/1601))
- **(Removal)** Remove support for 2020.1
- **(Removal)** Lambda gutter icons no longer take deployed Lambdas into account due to accuracy and performance issues

# _1.25_ (2021-03-10)
- **(Breaking Change)** Minimum SAM CLI version is now 1.0.0
- **(Feature)** Debugging Python based Lambdas locally now have the Python interactive console enabled (Fixes [#1165](https://github.com/aws/aws-toolkit-jetbrains/issues/1165))
- **(Feature)** Add a setting for how the AWS profiles notification is shown ([#2408](https://github.com/aws/aws-toolkit-jetbrains/issues/2408))
- **(Feature)** Deleting resources now requires typing "delete me" instead of the resource name
- **(Feature)** Add support for 2021.1
- **(Feature)** Allow deploying SAM templates from the CloudFormaton node ([#2166](https://github.com/aws/aws-toolkit-jetbrains/issues/2166))
- **(Bug Fix)** Improve error messages when properties are not found in templates ([#2449](https://github.com/aws/aws-toolkit-jetbrains/issues/2449))
- **(Bug Fix)** Fix resource selectors assuming every region has every service ([#2435](https://github.com/aws/aws-toolkit-jetbrains/issues/2435))
- **(Bug Fix)** Docker is now validated before building the Lambda when running and debugging locally (Fixes [#2418](https://github.com/aws/aws-toolkit-jetbrains/issues/2418))
- **(Bug Fix)** Fixed several UI inconsistencies in the S3 bucket viewer actions
- **(Bug Fix)** Fix showing stack status notification on opening existing CloudFormation stack ([#2157](https://github.com/aws/aws-toolkit-jetbrains/issues/2157))
- **(Bug Fix)** Processes using the Step system (e.g. SAM build) can now be stopped ([#2418](https://github.com/aws/aws-toolkit-jetbrains/issues/2418))
- **(Bug Fix)** Fixed the Remote Lambda Run Configuration failing to load the list of functions if not in active region
- **(Deprecation)** 2020.1 support will be removed in the next release

# _1.24_ (2021-02-17)
- **(Feature)** RDS serverless databases are now visible in the RDS node in the explorer
- **(Bug Fix)** Fix transient 'Aborted!' message on successful SAM CLI local Lambda execution
- **(Bug Fix)** Fix being unable to open the file browser in the Schemas download panel
- **(Bug Fix)** Fix being unable to type/copy paste into the SAM local run config's template path textbox
- **(Bug Fix)** Fix Secrets Manager-based databse auth throwing NullPointer when editing settings in 2020.3.2 (Fixes [#2403](https://github.com/aws/aws-toolkit-jetbrains/issues/2403))
- **(Bug Fix)** Fix making an un-needed service call on IDE startup ([#2426](https://github.com/aws/aws-toolkit-jetbrains/issues/2426))

# _1.23_ (2021-02-04)
- **(Feature)** Add "Copy S3 URI" to S3 objects ([#2208](https://github.com/aws/aws-toolkit-jetbrains/issues/2208))
- **(Feature)** Add Dotnet5 Lambda support (Image only)
- **(Feature)** Add option to view past object versions in S3 file editor
- **(Feature)** Nodejs14.x Lambda support
- **(Feature)** Update Lambda max memory to 10240
- **(Bug Fix)** Re-add environment variable settings to SAM template based run configurations ([#2282](https://github.com/aws/aws-toolkit-jetbrains/issues/2282))
- **(Bug Fix)** Fix error thrown on profile refresh if removing a profile that uses source_profile ([#2309](https://github.com/aws/aws-toolkit-jetbrains/issues/2309))
- **(Bug Fix)** Fix NodeJS and Python breakpoints failing to hit sometimes
- **(Bug Fix)** Speed up loading CloudFormation resources
- **(Bug Fix)** Fix not invalidating credentials when a `source_profile` is updated
- **(Bug Fix)** Fix cell based copying in CloudWatch Logs ([#2333](https://github.com/aws/aws-toolkit-jetbrains/issues/2333))
- **(Bug Fix)** Fix certain S3 buckets being unable to be shown in the explorer ([#2342](https://github.com/aws/aws-toolkit-jetbrains/issues/2342))
- **(Bug Fix)** Fix exception thrown in the new project wizard when run immediately after the toolkit is installed
- **(Bug Fix)** Fixing issue with SSO refresh locking UI thread ([#2224](https://github.com/aws/aws-toolkit-jetbrains/issues/2224))

# _1.22_ (2020-12-01)
- **(Feature)** Container Image Support in Lambda
- **(Bug Fix)** Fix update Lambda code for compiled languages ([#2231](https://github.com/aws/aws-toolkit-jetbrains/issues/2231))

# _1.21_ (2020-11-24)
- **(Breaking Change)** Remove support for 2019.3, 2020.1 is the new minimum version
- **(Feature)** Add copy Logical/Physical ID actions to Stack View [#2165](https://github.com/aws/aws-toolkit-jetbrains/issues/2165)
- **(Feature)** Add SQS AWS Explorer node and the ability to send/poll for messages
- **(Feature)** Add the ability to search CloudWatch Logs using CloudWatch Logs Insights
- **(Feature)** Add copy actions to CloudFormation outputs ([#2179](https://github.com/aws/aws-toolkit-jetbrains/issues/2179))
- **(Feature)** Support for the 2020.3 family of IDEs
- **(Feature)** Add an AWS Explorer ECR node
- **(Bug Fix)** Significantly speed up loading the list of S3 buckets ([#2174](https://github.com/aws/aws-toolkit-jetbrains/issues/2174))

# _1.20_ (2020-10-22)
- **(Feature)** Add support for `+` in AWS profile names
- **(Bug Fix)** Fix being unable to use a SSO profile in a credential chain
- **(Bug Fix)** Fix Aurora MySQL 5.7 not showing up in the AWS Explorer
- **(Bug Fix)** Improve IAM RDS connection: Fix Aurora MySQL, detect more error cases, fix database configuration validation throwing when there is no DB name
- **(Deprecation)** 2019.3 support will be removed in the next release

# _1.19_ (2020-10-07)
- **(Feature)** Add the ability to copy the URL to an S3 object
- **(Feature)** Add support for debugging dotnet 3.1 local lambdas (requires minimum SAM CLI version of 1.4.0)

# _1.18_ (2020-09-21)
- **(Feature)** Add support for AWS SSO based credential profiles
- **(Feature)** Support colons (`:`) in credential profile names
- **(Feature)** Add support for Lambda runtime java8.al2
- **(Feature)** Allow connecting to RDS/Redshift databases with temporary IAM AWS credentials or a SecretsManager secret
- **(Feature)** Several enhancements to the UX around connecting to AWS including:
  - Making connection settings more visible (now visible in the AWS Explorer)
  - Automatically selecting 'default' profile if it exists
  - Better visibility of connection validation workflow (more information when unable to connect)
  - Handling of default regions on credential profile
  - Better UX around partitions
  - Adding ability to refresh connection from the UI
- **(Feature)** Save update Lambda code settings
- **(Bug Fix)** Fix several cases where features not supported by the host IDE are shown ([#1980](https://github.com/aws/aws-toolkit-jetbrains/issues/1980))
- **(Bug Fix)** Start generating SAM project before the IDE is done indexing
- **(Bug Fix)** Fix several uncaught exceptions caused by plugins being installed but not enabled
- **(Bug Fix)** Fix removing a source_profile leading to an IDE error on profile file refresh
- **(Bug Fix)** Fix issue where templates > 51200 bytes would not deploy with "Deploy Serverless Application" ([#1973](https://github.com/aws/aws-toolkit-jetbrains/issues/1973))
- **(Bug Fix)** Fix the function selection panel not reloading when changing SAM templates ([#955](https://github.com/aws/aws-toolkit-jetbrains/issues/955))
- **(Bug Fix)** Fix remote terminal start issue on 2020.2
- **(Bug Fix)** Fix Rider building Lambda into incorrect folders
- **(Bug Fix)** Improved rendering speed of wrapped text in CloudWatch logs and CloudFormation events tables
- **(Bug Fix)** Fix the CloudWatch Logs table breaking when the service returns an exception during loading more entries ([#1951](https://github.com/aws/aws-toolkit-jetbrains/issues/1951))
- **(Bug Fix)** Improve watching of the AWS profile files to incorporate changes made to the files outisde of the IDE
- **(Bug Fix)** Fix SAM Gradle Hello World syncing twice ([#2003](https://github.com/aws/aws-toolkit-jetbrains/issues/2003))
- **(Bug Fix)** Quote template parameters when deploying a cloudformation template

# _1.17_ (2020-07-16)
- **(Feature)** Wrap logstream entries when they are selected ([#1863](https://github.com/aws/aws-toolkit-jetbrains/issues/1863))
- **(Feature)** Adding 'Outputs' tab to the CloudFormation Stack Viewer
- **(Feature)** Support for SAM CLI version 1.x
- **(Feature)** Add support for 2020.2
- **(Feature)** Add word wrap to CloudFormation status reasons on selection ([#1858](https://github.com/aws/aws-toolkit-jetbrains/issues/1858))
- **(Bug Fix)** Fix CloudWatch Logs logstream scrolling up automatically in certain circumstances
- **(Bug Fix)** Change the way we stop SAM CLI processes when debugging to allow docker container to shut down
- **(Bug Fix)** Fix double clicking Cloud Formation node not opening the stack viewer
- **(Bug Fix)** Fix Cloud Formation event viewer not expanding as the window expands
- **(Bug Fix)** The project SDK is now passed as JAVA_HOME to SAM when building Java functions when not using the build in container option

# _1.16_ (2020-05-27)
- **(Breaking Change)** The toolkit now requires 2019.3 or newer
- **(Feature)** Add support for GoLand, CLion, RubyMine, and PhpStorm

# _1.15_ (2020-05-21)
- **(Feature)** Add the ability to build in container when updating function code ([#1740](https://github.com/aws/aws-toolkit-jetbrains/issues/1740))
- **(Feature)** Add refresh to S3 browser
- **(Removal)** Dropped support for run/debug of deprecated Lambda runtimes

# _1.14_ (2020-05-04)
- **(Feature)** Add support for selecting regions in other partitions

# _1.13_ (2020-04-16)
- **(Feature)** On refresh, AWS explorer tree nodes will no longer be collapsed
- **(Feature)** Add capabilities check boxes to serverless deploy (issue [#1394](https://github.com/aws/aws-toolkit-jetbrains/issues/1394))
- **(Bug Fix)** Fix duplicate entries in SAM Init panel (issue [#1695](https://github.com/aws/aws-toolkit-jetbrains/issues/1695))

# _1.12_ (2020-04-07)
- **(Breaking Change)** Minimum SAM CLI version has been increased to 0.47.0
- **(Feature)** Support for CloudWatch Logs. View, filter, and stream log streams as well as quickly view logs from Lambda or ECS Containers.
- **(Feature)** Add support for creating and running Lambdas with dotnet core 3.1. Debug support will come in a future release
- **(Feature)** Add mechanism for users to submit feedback from within the toolkit
- **(Feature)** Support for the 2020.1 family of IDEs
- **(Bug Fix)** Fix issue [#1011](https://github.com/aws/aws-toolkit-jetbrains/issues/1011), python test files will no longer be recognized as lambda handlers
- **(Bug Fix)** Fix a situation where a valid SAM executable would not be recognized as valid
- **(Bug Fix)** Fix several issues with updating the SAM cli while the IDE is open
- **(Bug Fix)** Close the S3 bucket viewer when you delete the bucket
- **(Bug Fix)** Correct the max file size that can be opened from the S3 browser to idea.max.content.load.filesize instead of a constant 5MB
- **(Bug Fix)** Fix stack overflow when a profile has a `role_arn` but not a `source_profile`
- **(Bug Fix)** Fix SpeedSearch not working in S3 Bucket viewer
- **(Removal)** Removed the ability to create a new SAM project for dotnet core 2.0 since it is a deprecated runtime

# _1.11_ (2020-02-25)
- **(Breaking Change)** Remove NodeJS 8.10 from the new project wizard since the runtime is deprecated
- **(Feature)** IDE trust manager is now used to connect to AWS allowing configuration of untrusted certificates through the UI
- **(Bug Fix)** Fix being unable to use `--parameter-overrides` with SAM build
- **(Bug Fix)** Fixed not being able to view EventService Schemas on Windows 10

# _1.10_ (2020-01-07)
- **(Breaking Change)** Minimum SAM CLI version has been increased to 0.38.0
- **(Breaking Change)** Remove the Lambda nodes underneath of the CloudFromation stack in the explorer
- **(Feature)** Add S3 node and S3 Browser:
  - Browse files and folders in a tree view
  - Drag and drop upload
  - Double click to open files directly in the IDE
- **(Feature)** Add support for NodeJS 12 SAM/Lambdas
- **(Feature)** Add support for Java 11 SAM/Lambda
- **(Feature)** Add support for Java 11 SAM/Lambdas
- **(Bug Fix)** Profile name restrictions has been relaxed to allow `.`, `%`, `@`. amd `/`

# _1.9_ (2019-12-02)
- **(Feature)** Added support for Amazon EventBridge schema registry, making it easy to discover and write code for events in EventBridge.

# _1.8-192_ (2019-11-25)
- **(Breaking Change)** Now requires a minimum version of 2019.2 to run
- **(Feature)** Enable Cloud Debugging of ECS Services (beta)
- **(Feature)** Respect the default region in config file on first start of the IDE
- **(Feature)** Allow credential_process commands (in aws/config) to produce up to 64KB, permitting longer session tokens
- **(Feature)** Adding support for WebStorm
- **(Feature)** Enabled pasting of key value pairs into the environment variable table of local AWS Lambda run configurations
- **(Feature)** Adding support for Rider
- **(Bug Fix)** Fix an IDE error showing up during "SAM local debug" caused by running "docker ps" on the wrong thread
- **(Bug Fix)** Browsing for files in the Lambda run configuration is now rooted at the project directory
- **(Bug Fix)** Add an error on empty CloudFormation template or template that lacks a "Resources" section
- **(Bug Fix)** Rider: Fix unsupported Node runtime showing up in the "Create Serverless Applications" menu
- **(Bug Fix)** Fix the IDE showing an error sometimes when the SAM template file is invalid
- **(Bug Fix)** Resolve initialization errors on 2019.3 EAP
- **(Bug Fix)** Fix getting SAM version timing out in some circumstances which caused SAM related commands to fail
- **(Bug Fix)** Fix being able to run "SAM local run" configurations without Docker running
- **(Bug Fix)** Fix IDE error caused by editor text field being requested at the wrong scope level
- **(Bug Fix)** Rider: Fix the "Deploy Serverless" menu not appearing when right clicking on the project view

# _1.7_ (2019-10-17)
- **(Feature)** A notification is shown on startup indicating that JetBrains 2019.2 or greater will be required in an upcoming AWS Toolkit release
- **(Feature)** Add --no-interactive to SAM init when running a version of SAM >= 0.30.0
- **(Feature)** Bump minimum SAM CLI version from 0.14.1 to 0.16.0
- **(Feature)** Adding support for JetBrains Platform version 2019.3.
- **(Bug Fix)** Fix error thrown adding Lambda gutter icons and not having any active credentials
- **(Bug Fix)** Fix validating a Lambda handler not under a ReadAction

# _1.6_ (2019-09-23)
- **(Feature)** Open Stack Status UI on CloudFormation stack deletion.
- **(Feature)** Removed requirement of having to double-click to load more resources in AWS Explorer if there is more than one page returned
- **(Feature)** Added a Copy Arn action to AWS Explorer
- **(Feature)** Move AWS Connection details into a common Run Configuration tab for remote and local Lambda execution.
- **(Feature)** Enable caching of describe calls to avoid repeated network calls for already known resources.
- **(Feature)** Support timeout and memory size settings in run configuration
- **(Feature)** Porting resource selector to use resource-cache so network won't be hit on each dialog load.
- **(Feature)** Add support to link Gradle project.
- **(Feature)** Additional SAM build and SAM local invocation args configurable from Run/Debug Configuration settings
- **(Bug Fix)** Fix the bug that PyCharm pipenv doesn't create the project location folder
- **(Bug Fix)** Fix the CloudFormation explorer node not showing Lambdas that belong to the stack
- **(Bug Fix)** Log errors to idea.log when we fail to swtich the active AWS credential profile
- **(Bug Fix)** Handle the "me-" region prefix Treat the "me-" region prefix as Middle East
- **(Bug Fix)** Fixing issue where explorer does not load even with credentials/region selected.
- **(Bug Fix)** Fixing random AssertionError exception caused by Guava cache.
- **(Bug Fix)** Fix the bug that underscores in profile names are not shown in AWS settings panel
- **(Bug Fix)** Fixed bug in Pycharm's New Project pane where VirtualEnv path is not changed as project path is changed after switching Runtime
- **(Bug Fix)** Handle non-cloudformation yaml files gracefully
- **(Bug Fix)** Fix thread issue in PyCharm new project wizard
- **(Bug Fix)** Fix the bug that toolkit throws unhandled exception on startup when active credential is not configured

# _1.5_ (2019-07-29)
- **(Feature)** Support Globals configuration in SAM template for serverless functions.
- **(Feature)** Enable searching for `requirements.txt` when determining if a python method is a handler to match SAM build
- **(Feature)** Enable toolkit in 2019.2 EAP
- **(Feature)** Support building only the requested function when sam cli version is newer than 0.16
- **(Bug Fix)** Upgraded AWS Java SDK to pull in latest model changes ([#1099](https://github.com/aws/aws-toolkit-jetbrains/issues/1099))
- **(Bug Fix)** Fix DynamoDB template for Python does not create correctly.
- **(Bug Fix)** Fix DaemonCodeAnalyzer restart not happening in a read action ([#1012](https://github.com/aws/aws-toolkit-jetbrains/issues/1012))
- **(Bug Fix)** Fix the bug when project is in different drive than the temp folder drive for Windows. [#950](https://github.com/aws/aws-toolkit-jetbrains/issues/950)
- **(Bug Fix)** Fix invalid credentials file reporting an IDE error
- **(Bug Fix)** Fix issue where modifying a cloned run config results in mutation of the original
- **(Bug Fix)** Fix runtime exceptions on project startup and run configuration validation
- **(Bug Fix)** Fix read/write action issues when invoking a Lambda using SAM ([#1081](https://github.com/aws/aws-toolkit-jetbrains/issues/1081))
- **(Bug Fix)** Make sure all STS assume role calls are not on the UI thread ([#1024](https://github.com/aws/aws-toolkit-jetbrains/issues/1024))

# _1.4_ (2019-06-10)
- **(Feature)** Usability enhancements to the CloudFormation UI
  - color coding status similar to the AWS Console
  - preventing multiple tabs opening for the same stack ([#798](https://github.com/aws/aws-toolkit-jetbrains/issues/798))
  - opening from AWS Explorer with right-click instead of double click ([#799](https://github.com/aws/aws-toolkit-jetbrains/issues/799))
  - adding status reason to event view
- **(Feature)** Open README.md file after creating a project
- **(Feature)** Auto-create run configurations when using the New Project wizard
- **(Feature)** Enable toolkit in 2019.2 EAP
- **(Bug Fix)** Fix unable to map paths that have `.` or `..` in them
- **(Bug Fix)** Do not load proxy settings from Java system properties since it conflicts with IDE setting
- **(Bug Fix)** Make sure we commit all open documents if using a file-based event input ([#910](https://github.com/aws/aws-toolkit-jetbrains/issues/910))
- **(Bug Fix)** Fix being unable to open an empty credentials/config file for editing

# _1.3_ (2019-04-25)
- **(Feature)** Respect IDE HTTP proxy settings when making calls to AWS services. Fixes [#685](https://github.com/aws/aws-toolkit-jetbrains/issues/685).
- **(Feature)** Add Tooltips to the UI components
- **(Feature)** Java 8 Maven projects created through the Project Wizard templates will auto-import
- **(Feature)** Optimize plugin start up and responsiveness by making sure AWS calls happen on background threads
- **(Feature)** Added plugin icon
- **(Feature)** Documentation link added to AWS Explorer's gear menu
- **(Feature)** Add more help links from Toolkit's UI components into tech docs
- **(Feature)** Support credential_process in profile file.
- **(Bug Fix)** Fix being unable to add breakpoints to Python Lambdas on Windows, Fixes [#908](https://github.com/aws/aws-toolkit-jetbrains/issues/908)
- **(Bug Fix)** Fix gutter icon not shown in Project whoses runtime is not supported by Lambda but runtime group is supported
- **(Bug Fix)** Fix building of a Java Lambda handler failing due to unable to locate build.gradle/pom.xml Fixes [#868](https://github.com/aws/aws-toolkit-jetbrains/issues/868), [#857](https://github.com/aws/aws-toolkit-jetbrains/issues/857)
- **(Bug Fix)** Fix template not found after creating a project, fixes [#856](https://github.com/aws/aws-toolkit-jetbrains/issues/856)

# _1.2_ (2019-03-26)
- **(Breaking Change)** Minimum SAM CLI version has been increased to 0.14.1
- **(Feature)** You can now specify a docker network when locally running a Lambda
- **(Feature)** You can now specify if SAM should skip checking for newer docker images when invoking local Lambda functions
- **(Feature)** Add Gradle based SAM project template
- **(Feature)** Java8 functions using `sam build` can now be deployed
- **(Feature)** Building of Python based Lambda functions has been migrated to using `sam build`. This adds the option to use a container-based build during local run/debug of Lambda functions.
- **(Feature)** The AWS CLI config and credential files are now monitored for changes. Changes automatically take effect.
- **(Feature)** Enable support for IntelliJ/Pycharm 2019.1
- **(Feature)** Add option to use a container-based build during serverless application deployment
- **(Feature)** Enable support for running, debugging, and deploying Python 3.7 lambdas
- **(Feature)** Building of Java 8 based Lambda functions has been migrated to using `sam build` (Maven and Gradle are supported).
- **(Bug Fix)** Fix sort order for CloudFormation nodes in the AWS Explorer
- **(Bug Fix)** Clarify validation error when SAM CLI is too old
- **(Bug Fix)** Fix issue where 'Edit Credentials' action didn't check for both 'config' and 'credentials'
- **(Bug Fix)** Fix issue where the cancel button in the Serverless Deploy progress dialog did nothing
- **(Bug Fix)** Improve 'Invalid AWS Credentials' messaging to include error details
- **(Bug Fix)** Unable to edit AWS credential file via pycharm ([#759](https://github.com/aws/aws-toolkit-jetbrains/issues/759))
- **(Bug Fix)** Fix issue where invalid AWS Credentials prevent plugin startup
- **(Bug Fix)** Require SAM run configurations to have an associated credential profile ([#526](https://github.com/aws/aws-toolkit-jetbrains/issues/526))

# _1.1_ (2019-01-08)
- **(Feature)** Additional information provided when AWS Explorer isn't able to load data - [#634](https://github.com/aws/aws-toolkit-jetbrains/issues/634) [#578](https://github.com/aws/aws-toolkit-jetbrains/issues/578)
- **(Feature)** Able to view CloudFormation stack details by double clicking it in the Explorer
- **(Feature)** Added AWS Credential validation when changing profiles
- **(Bug Fix)** Fix case where packaging Java code was not releasing file locks [#694](https://github.com/aws/aws-toolkit-jetbrains/issues/694)
- **(Bug Fix)** Suppress FileNotFoundException that can be thrown if the endpoints file fails to download
- **(Bug Fix)** Fixed issue where accounts without Lambda access were unable to open CloudFormation stack nodes
- **(Bug Fix)** Use us-east-1 instead of global endpoint for STS
- **(Bug Fix)** Ignore .DS_Store files when building Lambda zip ([#725](https://github.com/aws/aws-toolkit-jetbrains/issues/725))
- **(Bug Fix)** Fix IllegalStateException: context.module must not be null ([#643](https://github.com/aws/aws-toolkit-jetbrains/issues/643))
- **(Bug Fix)** Fixed issue on OS X where the SAM CLI is unable to use an UTF-8 locale.
- **(Bug Fix)** Fix the status message for certain states during CloudFormation stack updates ([#702](https://github.com/aws/aws-toolkit-jetbrains/issues/702))

