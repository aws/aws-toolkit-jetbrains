# _3.87_ (2025-07-30)
- **(Bug Fix)** change to use promptStickyCard to for image verification notification
- **(Bug Fix)** Suppress IDE error when current editor context is not valid for Amazon Q

# _3.86_ (2025-07-16)
- **(Bug Fix)** - Fixed "Insert to Cursor" button to correctly insert code blocks at the current cursor position in the active file

# _3.85_ (2025-07-10)
- **(Feature)** Amazon Q /test, /doc, and /dev capabilities integrated into Agentic coding.
- **(Feature)** Add image context support

# _3.84_ (2025-07-09)

# _3.83_ (2025-07-07)
- **(Bug Fix)** Fix auto-suggestions being shown when suggestions are paused

# _3.82_ (2025-07-03)
- **(Bug Fix)** Skip inline completion when deleting characters

# _3.81_ (2025-06-27)

# _3.80_ (2025-06-26)
- **(Feature)** Amazon Q inline: now display completions much more consistently at the user's current caret position
- **(Feature)** Amazon Q inline: now Q completions can co-exist with JetBrains' native IntelliSense completions, when both are showing, press Tab or your customized key shortcuts to accept Q completions and press Enter to accept IntelliSense completions.
- **(Feature)** Amazon Q inline: now shows in a JetBrains native UX of popup and inlay text style
- **(Feature)** Amazon Q inline: The new UX allows configurable shortcuts for accepting completions and navigating through completions. *Caveat: for users using the previous versions, if you have configured your custom key shortcuts for the Q inline before, you will have to re-configure them again in Amazon Q settings due to a change in the keymap actions.

# _3.79_ (2025-06-25)
- **(Feature)** /transform: run all builds client-side
- **(Feature)** Amazon Q Chat: Pin context items in chat and manage workspace rules

# _3.78_ (2025-06-18)
- **(Feature)** Add model selection feature

# _3.77_ (2025-06-16)

# _3.76_ (2025-06-12)
- **(Feature)** Add MCP support for Amazon Q chat

# _3.75_ (2025-06-11)
- **(Feature)** Support for Amazon Q Builder ID paid tier

# _3.74_ (2025-06-05)
- **(Feature)** Agentic coding experience: Amazon Q can now write code and run shell commands on your behalf
- **(Bug Fix)** Support full Unicode range in inline chat panel on Windows

# _3.73_ (2025-05-29)
- **(Bug Fix)** /transform: handle InvalidGrantException properly when polling job status

# _3.72_ (2025-05-22)
- **(Removal)** /transform: remove option to receive multiple diffs

# _3.71_ (2025-05-15)
- **(Feature)** Add inline completion support for abap language
- **(Bug Fix)** Fix UI freezes that may occur when interacting with large files in the editor

# _3.70_ (2025-05-08)
- **(Feature)** Amazon Q: Support selecting customizations across all Q profiles with automatic profile switching for enterprise users
- **(Bug Fix)** Do not always show 'Amazon Q Code Issues' tab when switching to the 'Problems' tool window
- **(Bug Fix)** /dev: Fix missing Amazon Q feature dev auto build setting.
- **(Bug Fix)** increase /review timeout
- **(Bug Fix)** Fix JavascriptLanguage not found on 2025.1+

# _3.69_ (2025-04-28)
- **(Bug Fix)** Amazon Q: Fix issue where context menu items are not available after re-opening projects or restarting the IDE
- **(Bug Fix)** Fix LinkageError while attempting to do Amazon Q inline suggestions in certain environments
- **(Bug Fix)** Fix issue where user can become stuck because Amazon Q Chat does not show authentication prompt
- **(Removal)** Removed support for 2024.1.x IDEs
- **(Removal)** Removed support for Gateway 2024.3

# _3.68_ (2025-04-23)
- **(Feature)** Amazon Q: Show visual indicator in status bar if profile selection is needed to continue with Q Inline / Q Chat
- **(Feature)** Amazon Q /test: Remove unsupported message for non-java python languages
- **(Bug Fix)** /dev: Fix prompt to enable devfile build not triggering when devfile is present.
- **(Bug Fix)** /review disable auto scan by default
- **(Bug Fix)** /review: disabled highlighter for ignored issues

# _3.67_ (2025-04-18)
- **(Bug Fix)** Amazon Q: Customization now resets with a warning if unavailable in the selected profile.
- **(Bug Fix)** Q panel will get stuck while signin if users have multiple windows
- **(Bug Fix)** Fix integer overflow when local context index input is larger than 2GB
- **(Bug Fix)** Fix workspace index process quits when hitting a race condition
- **(Bug Fix)** Fix infinite loop when workspace indexing server fails to initialize

# _3.66_ (2025-04-11)
- **(Feature)** The logs emitted by the Agent during user command execution will be accepted and written to `.amazonq/dev/run_command.log` file in the user's local repository.
- **(Bug Fix)** Unit test generation now completes successfully when using the `/test` command

# _3.64_ (2025-04-10)
- **(Bug Fix)** Fix issue where IDE freezes when logging into Amazon Q

# _3.65_ (2025-04-10)
- **(Bug Fix)** Fix issue where Amazon Q cannot process chunks from local `@workspace` context

# _3.63_ (2025-04-08)
- **(Feature)** Enterprise users can choose their preferred Amazon Q profile to improve personalization and workflow across different business regions
- **(Bug Fix)** Amazon Q /doc: close diff tab and open README file in preview mode after user accept changes

# _3.62_ (2025-04-03)
- **(Feature)** /review: automatically generate fix without clicking Generate Fix button
- **(Bug Fix)** /transform: prompt user to re-authenticate if credentials expire during transformation
- **(Bug Fix)** Gracefully handle additional fields in Amazon Q /dev code generation result without throwing errors
- **(Bug Fix)** /review: set programmingLanguage to Plaintext if language is unknown
- **(Bug Fix)** /review: Respect user option to allow code suggestions with references

# _3.61_ (2025-03-27)
- **(Feature)** Amazon Q: Moved "Include suggestions with code references" setting to General
- **(Feature)** Add support for 2025.1
- **(Bug Fix)** Amazon Q: Attempt to reduce thread pool contention locking IDE caused by `@workspace` making a large number of requests
- **(Deprecation)** An upcoming release will remove support for JetBrains Gateway version 2024.3 and for IDEs based on the 2024.1 platform

# _3.60_ (2025-03-20)
- **(Feature)** AmazonQ /test now displays a concise test plan summary to users.
- **(Bug Fix)** Fix inline completion failure due to context length exceeding the threshold
- **(Bug Fix)** Amazon Q: Fix cases where content may be incorrectly excluded from workspace.

# _3.59_ (2025-03-13)
- **(Feature)** AmazonQ /dev and /doc: Add support for complex workspaces.
- **(Bug Fix)** /review: normalize relative file path before unzipping
- **(Bug Fix)** fix Q chat request timeout

# _3.58_ (2025-03-06)
- **(Bug Fix)** Amazon Q: Fix data isolation between tabs to prevent interference when using /doc in multiple tabs
- **(Removal)** The Amazon Q inline suggestion popup goes back to being under the suggestions and is always showing.

# _3.57_ (2025-02-28)
- **(Bug Fix)** Fix suggestion not visible in remote for 2024.3
- **(Bug Fix)** /test: update capability card text
- **(Bug Fix)** Amazon Q /doc: update workspace too large error message
- **(Bug Fix)** Amazon Q /doc: Fix uploading file method throwing incorrect workspace too large error message
- **(Bug Fix)** /transform: skip running tests locally when user chooses to do so

# _3.56_ (2025-02-20)
- **(Feature)** Amazon Q /doc: support making changes to architecture diagrams

# _3.55_ (2025-02-13)
- **(Feature)** /transform: support transformations to Java 21
- **(Bug Fix)** Enable syntax highlighting when viewing diff for /test
- **(Bug Fix)** Amazon Q /test: Truncating user input to 4096 characters for unit test generation.
- **(Bug Fix)** Amazon Q /review: Unable to navigate to code location when selecting issues
- **(Bug Fix)** Amazon Q /test: Q identify active test file and infer source file for test generation.
- **(Removal)** Amazon Q: Revert prefetch logic to enable more stable inline completion.

# _3.54_ (2025-02-11)
- **(Bug Fix)** Amazon Q: Reverting the behavior of making JetBrains suggestions(IntelliSense) and Q suggestions co-exist

# _3.53_ (2025-02-07)
- **(Bug Fix)** Amazon Q: Fixed an issue where in a specific scenario when receiving multiple suggestions with JetBrains suggestions visible, users are not able to accept the suggestion.

# _3.52_ (2025-02-06)
- **(Feature)** Adds event listener for notifying UI that AB feature configurations have been resolved
- **(Feature)** Amazon Q /review: Code issues can now be grouped by severity or file location.
- **(Feature)** Inline suggestions: Pre-fetch recommendations to reduce suggestion latency.
- **(Bug Fix)** fix(amazonq): Citation links are not clickable as numbers, but appear as non-clickable texts
- **(Bug Fix)** Amazon Q: Prevent IndexOutOfBoundsException by adding boundary checks for invalid range markers ([#5187](https://github.com/aws/aws-toolkit-jetbrains/issues/5187))
- **(Bug Fix)** /test placeholder text aligned across IDEs

# _3.51_ (2025-01-29)
- **(Feature)** Amazon Q: Now the Amazon Q suggestions can co-exist with Jetbrains suggestions, with tab behavior configurable in the settings.
- **(Feature)** Amazon Q: Amazon Q inline now has configurable shortcuts for various actions including accept and browsing through suggestions.
- **(Feature)** Add setting to allow Q /dev to run code and test commands
- **(Feature)** Amazon Q: The suggestion popup will hide by default and will be displayed when the suggestion is being hovered over.
- **(Bug Fix)** Amazon Q /doc: fix open diff in a tab when another modal is open
- **(Bug Fix)** Amazon Q /test: Fixed an issue which incorrectly caused payload size exceeded exception when collecting project payload files
- **(Bug Fix)** fix(amazonq): For security reasons, disabled auto linkify for link texts coming in markdown other than [TEXT](URL) format
- **(Bug Fix)** Fix UI freeze caused by updating workspace index on non background context

# _3.50_ (2025-01-23)
- **(Feature)** Amazon Q: Updated `/help` command to include re:invent 2024 features
- **(Feature)** Amazon Q: UI improvements through more accurate code syntax highlighting
- **(Bug Fix)** Fixed an issue where Amazon Q settings did not persist across IDE restarts
- **(Bug Fix)** Amazon Q: Fix context menu displaying when typing `@`, even though input is disallowed
- **(Bug Fix)** Amazon Q: Fix up/down history navigation only triggering on first/last line of prompt input
- **(Bug Fix)** Amazon Q /doc: Ask for user prompt if error occurs while updating documentation
- **(Bug Fix)** Amazon Q: cursor no longer jumps after navigating prompt history
- **(Bug Fix)** Improve text description of workspace index settings
- **(Bug Fix)** Amazon Q /doc: fix for user prompt to change folder in chat
- **(Bug Fix)** Amazon Q Doc README diff will re-open when the README file is clicked after it has been closed
- **(Bug Fix)** Amazon Q /test: Fix for test generation payload creation to not filter out target file.
- **(Bug Fix)** Amazon Q: word duplication when pressing tab on context selector fixed

# _3.49_ (2025-01-17)
- **(Bug Fix)** /review: Improved success rate of code reviews for certain workspace configurations

# _3.48_ (2025-01-16)
- **(Feature)** Enhance Q inline completion context fetching for better suggestion quality
- **(Feature)** /doc: Add error message if updated README is too large
- **(Bug Fix)** /transform: always include button to start a new transformation at the end of a job
- **(Bug Fix)** Amazon Q can update mvn and gradle build files
- **(Bug Fix)** Fix doc generation for modules that are a part of the project
- **(Bug Fix)** Amazon Q /dev: Remove hard-coded limits and instead rely server-side data to communicate number of code generations remaining
- **(Bug Fix)** /transform: automatically open pre-build error logs when available
- **(Bug Fix)** /doc: Fix code generation error when cancelling a documentation task
- **(Bug Fix)** Amazon Q - update messaging for /doc agent

# _3.47_ (2025-01-09)
- **(Bug Fix)** Fix issue where users are unable to login to Amazon Q if they have previously authenticated ([#5214](https://github.com/aws/aws-toolkit-jetbrains/issues/5214))
- **(Bug Fix)** Fix incorrect text shown while updating documentation in /doc
- **(Bug Fix)** Amazon Q Code Transformation: retry initial project upload on failure
- **(Bug Fix)** /transform: use correct doc link in SQL conversion help message
- **(Bug Fix)** Amazon Q /dev: Fix issue when files are deleted while preparing context
- **(Bug Fix)** Amazon Q /test: Test generation fails for files outside the project
- **(Bug Fix)** Amazon Q Code Transformation: allow PostgreSQL as target DB for SQL conversions
- **(Bug Fix)** Fix incorrect accept and reject buttons shows up while hovering over the generated file
- **(Bug Fix)** Prevent customization override if user has manually selected a customization
- **(Bug Fix)** Align UX text of document generation flow with vs code version

# _3.46_ (2024-12-17)
- **(Feature)** /review: Code fix automatically scrolls into view after generation.
- **(Feature)** Chat: improve font size and line-height in footer (below prompt input field)
- **(Feature)** Adds capability to send new context commands to AB groups
- **(Bug Fix)** Chat: When writing a prompt without sending it, navigating via up/down arrows sometimes deletes the unsent prompt.
- **(Bug Fix)** Fix chat not retaining history when interaction is through onboarding tab type ([#5189](https://github.com/aws/aws-toolkit-jetbrains/issues/5189))
- **(Bug Fix)** Chat: When navigating to previous prompts, code attachments are sometimes displayed incorrectly
- **(Bug Fix)** Reduce frequency of system information query

# _3.45_ (2024-12-10)
- **(Feature)** Add acknowledgement button for Amazon Q Chat disclaimer
- **(Bug Fix)** Chosing cancel on sign out confirmation now cancels the sign out and does not delete profiles from ~/.aws/config ([#5167](https://github.com/aws/aws-toolkit-jetbrains/issues/5167))
- **(Bug Fix)** Fix `@workspace` missing from the Amazon Q Chat welcome tab
- **(Bug Fix)** Fix for /review LLM based code issues for file review on windows
- **(Bug Fix)** Fix for File Review payload and Regex error for payload generation
- **(Bug Fix)** Amazon Q Code Transformation: show build logs when server-side build fails

# _3.44_ (2024-12-04)
- **(Feature)** Amazon Q: UI improvements to chat: New splash loader animation, initial streaming card animation, improved button colours
- **(Feature)** Amazon Q: Navigate through prompt history by using the up/down arrows
- **(Bug Fix)** Fix issue where Amazon Q Code Transform is unable to start
- **(Bug Fix)** Fix DynamoDB viewer throwing 'ActionGroup should be registered using <group> tag' on IDE start ([#5012](https://github.com/aws/aws-toolkit-jetbrains/issues/5012)) ([#5120](https://github.com/aws/aws-toolkit-jetbrains/issues/5120))
- **(Bug Fix)** Amazon Q: Fix chat syntax highlighting when using several different themes

# _3.43_ (2024-12-03)
- **(Feature)** `/review` in Q chat to scan your code for vulnerabilities and quality issues, and generate fixes
- **(Feature)** `/test` in Q chat to generate unit tests for java and python
- **(Feature)** `/doc` in Q chat to generate and update documentation for your project
- **(Feature)** Added system notifications to inform users about critical plugin updates and potential issues with available workarounds

# _3.42_ (2024-11-27)
- **(Feature)** Amazon Q /dev: support `Dockerfile` files
- **(Feature)** Feature(Amazon Q Code Transformation): allow users to view results in 5 smaller diffs
- **(Feature)** Introduce @workspace command to enhance chat context fetching for Chat
- **(Bug Fix)** Correct search text for Amazon Q inline suggestion keybindings
- **(Bug Fix)** Fix(Amazon Q Code Transformation): always show user latest/correct transformation results
- **(Bug Fix)** Amazon Q /dev: Fix error when accepting changes if leading slash is present.

# _3.41_ (2024-11-22)
- **(Feature)** Amazon Q /dev: support `.gradle` files
- **(Feature)** Inline Auto trigger will now happen more consistently and will not conflict with JetBrains code completion.
- **(Feature)** Uses AB variation as the name for overriden customizations
- **(Feature)** Code Transform: Enable support for Java 17 projects.
- **(Feature)** The key shortcuts for Q inline suggestions are now configurable from keymap settings. Default key shortcuts for navigating through suggestions are changed from left/right arrow keys to option(alt) + [ and option(alt) + ], respectively.
- **(Feature)** The Q suggestion inline popup will now hide by default and will show when the user hovers over the suggestion text, the IDE code suggestion popup will also appear to be more transparent to unblock seeing the multi-line suggestions.
- **(Feature)** Feature(Amazon Q Code Transformation): support conversions of embedded SQL from Oracle to PostgreSQL
- **(Bug Fix)** Amazon Q chat: `@workspace` command shown in all tab types
- **(Bug Fix)** Amazon Q Feature Dev: display limit reached error message
- **(Bug Fix)** Amazon Q Chat: Changed default info color on dark themes to be blue, instead of gray
- **(Removal)** Removed support for Gateway 2024.2
- **(Removal)** Removed support for 2023.3.x IDEs

# _3.40_ (2024-11-14)
- **(Feature)** Amazon Q /dev: Add an action to accept individual files
- **(Bug Fix)** Fix a bug when Amazon Q responds with still indexing message even when `@workspace` index is done
- **(Bug Fix)** Fix issue where Amazon Q inline chat can be invoked from non-editor windows

# _3.39_ (2024-11-12)
- **(Bug Fix)** Fix poor inline suggestions from Amazon Q caused by improperly formatted supplemental context

# _3.38_ (2024-11-07)
- **(Bug Fix)** Improve the position that inline chat shortcut hint is shown in editor
- **(Bug Fix)** Improve `@workspace` index start stop strategy
- **(Bug Fix)** Fixed an issue where Q inline won't appear in JetBrains remote 2024.2+

# _3.37_ (2024-10-31)
- **(Bug Fix)** Amazon Q /dev: Fix the issue resulting in the first request per conversation to /dev failing
- **(Bug Fix)** Fix inline chat shortcut hint breaking text selection on remote editors

# _3.36_ (2024-10-30)
- **(Bug Fix)** Fix inline chat default key binding not working on windows and linux

# _3.35_ (2024-10-29)
- **(Feature)** Remove read-only mode on before diff of code changes generated by agent
- **(Feature)** Provide more frequent updates about code changes made by agent
- **(Feature)** Amazon Q /dev: Add stop generation action
- **(Feature)** Added inline chat support. Select some code and hit ⌘+I on Mac or Ctrl+I on Windows to start
- **(Bug Fix)** Fix pointless busy loop in Amazon Q wasting CPU cycles ([#5000](https://github.com/aws/aws-toolkit-jetbrains/issues/5000))
- **(Bug Fix)** Update `@workspace` index when adding or deleting a file
- **(Deprecation)** An upcoming release will remove support for JetBrains Gateway version 2024.2 and for IDEs based on the 2023.3 platform

# _3.34_ (2024-10-22)
- **(Bug Fix)** Fix issue where the plugin can't read SSO tokens from disk / always returns 'Unable to load client registration'

# _3.33_ (2024-10-17)
- **(Feature)** Add support for 2024.3
- **(Bug Fix)** `@workspace` cannot properly locate certain folders for certain project setup
- **(Bug Fix)** Fix an IDE deadlock that may occur while attempting to initialize Amazon Q UI elements ([#4966](https://github.com/aws/aws-toolkit-jetbrains/issues/4966))

# _3.32_ (2024-10-10)
- **(Feature)** Loosen inline completion support limitations for YAML/JSON
- **(Bug Fix)** Fix error occuring when Amazon Q attempts to show UI hints on manually triggerred inline suggestion ([#4929](https://github.com/aws/aws-toolkit-jetbrains/issues/4929))
- **(Bug Fix)** Amazon Q (/dev): provide error messaging when no code changes are required for the prompt
- **(Bug Fix)** Fix 'Slow operations are prohibited on EDT.' when Amazon Q is determining if a file supports inline suggestions ([#4823](https://github.com/aws/aws-toolkit-jetbrains/issues/4823))
- **(Bug Fix)** Amazon Q Feature Dev: Add error messages when the upload URL expires
- **(Bug Fix)** Fix toolkit connection dropdown getting hidden when panel width is small.
- **(Bug Fix)** Fix inability to sign out in reauth view in Q chat panel
- **(Bug Fix)** Raise max `@workspace` indexing size to 4GB
- **(Bug Fix)** Automatically pause and resume `@workspace` indexing when OS CPU load is high

# _3.31_ (2024-10-03)
- **(Feature)** Amazon Q Developer: Updated legal disclaimer text
- **(Feature)** Amazon Q Code Transformation: allow users to skip tests
- **(Bug Fix)** Fix issue where multiple SSO login attempts in a short time result in 404
- **(Bug Fix)** Fix issue where a user may get stuck while attempting to login to Builder ID

# _3.30_ (2024-09-27)
- **(Bug Fix)** Amazon Q Code Transformation: notify users when no JDK is set in Project Structure settings
- **(Bug Fix)** Automatically terminate orphaned process for `@workspace` helper

# _3.29_ (2024-09-19)
- **(Feature)** Support `@workspace` queries for specific files like "`@workspace` what does test.ts do?".
- **(Bug Fix)** Amazon Q Feature Dev: fix iteration count messaging during code insertion
- **(Bug Fix)** Fix UI slowdown when Amazon Q Inline Suggestions are enabled, but token cannot be refreshed ([#4868](https://github.com/aws/aws-toolkit-jetbrains/issues/4868))
- **(Bug Fix)** Fix "read access" error that may occur when Amazon Q Inline Suggestion is building context ([#4888](https://github.com/aws/aws-toolkit-jetbrains/issues/4888)) ([#4848](https://github.com/aws/aws-toolkit-jetbrains/issues/4848))

# _3.28_ (2024-09-11)
- **(Feature)** Improve workspace indexing by only index files that are changed since last indexing
- **(Bug Fix)** Amazon Q Chat: Fixed inline code blocks are not vertically aligned with texts
- **(Bug Fix)** Fix issue preventing login when running on 2024.2 remote environments
- **(Bug Fix)** Automatically start workspace indexing when new project is opened
- **(Removal)** Amazon Q Feature dev: Improve quality and UX by removing approach generation flow

# _3.27_ (2024-09-05)
- **(Feature)** Reduce `@workspace` indexing time by 50%
- **(Feature)** Amazon Q /dev: include in progress state agent in code generation

# _3.26_ (2024-08-30)
- **(Bug Fix)** Fix Runtime Exception when opening a tool window ([#4849](https://github.com/aws/aws-toolkit-jetbrains/issues/4849))

# _3.25_ (2024-08-29)
- **(Bug Fix)** Fix bug where text with inline code copied from Amazon Q Chat had new line breaks around the inline code text
- **(Bug Fix)** Fix bug when disabled commands does not get filtered in quick actions

# _3.24_ (2024-08-22)
- **(Feature)** Add notification for IdC users on extended session
- **(Bug Fix)** Amazon Q: update login logo styling
- **(Bug Fix)** Amazon Q Code Transformation: show an error notification when download diff fails
- **(Bug Fix)** Fix UI freeze that occurs when viewing an Amazon Q code security scanning finding
- **(Bug Fix)** Fix Q building supplemental context under EDT which might slow or block the UI

# _3.23_ (2024-08-15)
- **(Bug Fix)** Fix NPE in Rider AWS SAM project wizard ([#4768](https://github.com/aws/aws-toolkit-jetbrains/issues/4768))
- **(Bug Fix)** Amazon Q Chat: Fix Tab selection scrollbar visibility which causes tabs half visible if there are several tabs open
- **(Bug Fix)** Amazon Q `/dev`: update supported file extensions
- **(Bug Fix)** Amazon Q: Optimized the workspace file collection logic which makes the collection time now only 5-10% of what it was before.
- **(Bug Fix)** Amazon Q Chat: / command selector doesn't work if user pastes the command to prompt and submits
- **(Bug Fix)** Amazon Q Chat: Related link previews sometimes remain on screen and block the whole Chat UI
- **(Bug Fix)** Amazon Q Chat: @ context selector conflicts with some use cases where the user wants use @ character for a word in the prompt itself
- **(Bug Fix)** Amazon Q Chat: Fix Header items in card bodies don't wrap if they don't contain spaces
- **(Removal)** Removed support for 2023.2.x IDEs
- **(Removal)** Removed support for Gateway 2024.1

# _3.22_ (2024-08-08)
- **(Feature)** feat(Amazon Q Code Transformation): warn user if absolute path found in pom.xml
- **(Feature)** feat(Amazon Q Code Transformation): show pro tier users estimated cost of /transform on projects over 100K lines
- **(Bug Fix)** fix(Amazon Q Code Transformation): prevent empty chat bubble from appearing when starting or cancelling a transformation
- **(Bug Fix)** Amazon Q /dev: include a retry option for the same prompt after folder reselection
- **(Bug Fix)** Fix inability to open files on double click and open context menu on right click in the S3 bucket viewer
- **(Bug Fix)** fix(amazonq): Amazon Q chat `@workspace` uses more than 20% cpu
- **(Bug Fix)** Fix 'Cannot create extension' in AWS Toolkit

# _3.21_ (2024-08-01)
- **(Bug Fix)** Fix NullPointerException that may happen when re-authenticating to Amazon Q
- **(Bug Fix)** Amazon Q Chat: Fixing issue with the max tabs notification not being dismissible
- **(Bug Fix)** Fix Amazon Q chat context menu actions show up in all chat windows
- **(Bug Fix)** Amazon Q Chat: Fixing issue with an incorrect input cursor position in the prompt text box
- **(Bug Fix)** Amazon Q Chat: Showing/hiding the scrollbars is now controlled by the OS settings

# _3.20_ (2024-07-26)
- **(Bug Fix)** Fix Q chat not responding in 2023.2 and 2023.3

# _3.19_ (2024-07-25)
- **(Feature)** Q feature dev: Use common code extensions to filter relevant files
- **(Bug Fix)** Amazon Q Chat: Fixes a bug where multiline user input appears like a code block instead of a paragraph
- **(Bug Fix)** Amazon Q Chat: Fixes a bug when the prompt input exceeds the width of the chat box it's not always wrapped correctly.
- **(Bug Fix)** Fix 'ContainerDisposedException' when attempting to sign-in to Amazon Q
- **(Bug Fix)** Fix Q window reauthenticate button not functioning due to illegal function call outisde of EDT

# _3.18_ (2024-07-19)
- **(Feature)** Add support for 2024.2
- **(Bug Fix)** Fix bug when workspace index cache is not loaded
- **(Deprecation)** An upcoming release will remove support for JetBrains Gateway version 2024.1 and for IDEs based on the 2023.2 platform

# _3.17_ (2024-07-15)
- **(Feature)** Amazon Q/dev: proactively show code generation iterations
- **(Bug Fix)** Don't allow Q/Core to be installed in the unsupported thin client context ([#4658](https://github.com/aws/aws-toolkit-jetbrains/issues/4658))
- **(Bug Fix)** AmazonQ chat `@workspace` file indexing respects user's git-ignore
- **(Bug Fix)** Amazon Q /dev command: improve user error messages

# _3.16_ (2024-07-10)
- **(Feature)** Add support for [Amazon Q Chat Workspace Context](https://docs.aws.amazon.com/amazonq/latest/qdeveloper-ug/workspace-context.html). Customers can use `@workspace` to ask questions regarding local workspace.

# _3.15_ (2024-07-08)
- **(Bug Fix)** Amazon Q Chat: Fixed button texts are cropped too short
- **(Bug Fix)** Amazon Q Chat: Fixed button font sizes are too big
- **(Bug Fix)** Expose Amazon Q backend validation error message
- **(Bug Fix)** Amazon Q Security Scans: Fixed unnecessary yellow lines appearing in both auto scans and project scans.
- **(Bug Fix)** Amazon Q: Fix an issue where inline suggestion will not properly show in JetBrains remote env 2024.1+
- **(Bug Fix)** Amazon Q Chat: Fixed prompt input becomes invisible when multine text inserted with paste
- **(Bug Fix)** Fix Q Chat not respecting system trust store unless a proxy is configured
- **(Bug Fix)** Amazon Q Chat: Fixed prompt input and selected command horizontal alignment
- **(Bug Fix)** Amazon Q Chat: Fixed prompt input becomes invisible if an html special character is inserted
- **(Bug Fix)** Amazon Q Chat: Fixed buttons don't show borders inside a message

# _3.14_ (2024-06-27)
- **(Bug Fix)** Q Code Transform - Add troubleshooting document links to UI messages
- **(Bug Fix)** Security Scan: Improved telemetry error messages
- **(Bug Fix)** Feature Development: fix in progress UX during backend calls
- **(Bug Fix)** Rewrite integration with the New Solution dialog in Rider to use the new API.

# _3.13_ (2024-06-24)
- **(Bug Fix)** Fix refresh token failure due to null aws error details

# _3.12_ (2024-06-21)
- **(Bug Fix)** Fix an issue where worker threads are unable to properly resolve the calling plugin, resulting in invalid telemetry data
- **(Bug Fix)** Amazon Q Chat: Fixed broken code blocks with typewriter text in list items.
- **(Bug Fix)** Feature Development: update /dev welcome message
- **(Bug Fix)** Fix IDE auto completion settings potentially overwritten by Q inline suggestion
- **(Bug Fix)** Fix infinite restart loop on <=241 when an incompatible version of AWS Toolkit is installed alongside Amazon Q ([#4519](https://github.com/aws/aws-toolkit-jetbrains/issues/4519))

# _3.11_ (2024-06-13)
- **(Feature)** Amazon Q Code Transform: Allow user to view transformation build log
- **(Bug Fix)** Fix intermittent validation exception from CodeWhisperer service for improperly formed request
- **(Bug Fix)** fix(Amazon Q Code Transformation): allow module to be zipped successfully even if it contains broken symlinks

# _3.10_ (2024-06-07)
- **(Bug Fix)** Resolve a NullPointerException that could occur while handling editor creation event. ([#4554](https://github.com/aws/aws-toolkit-jetbrains/issues/4554))

# _3.9_ (2024-06-06)
- **(Feature)** feat(featureDev): generated plan being shown from top
- **(Feature)** Amazon Q Code Transform: Communicate download failure in transform chat, and improve download failure notification
- **(Feature)** CodeTransformation: increase project upload limit to 2GB
- **(Bug Fix)** Security Scan: Improved method of applying security fixes
- **(Bug Fix)** Security Scan: Improved accuracy when applying security fixes
- **(Bug Fix)** Security Scan: Fixes inconsistent behavior with how security issues are underlined in the editor.

# _3.8_ (2024-05-30)
- **(Bug Fix)** fix(featureDev): File Rejection stopped working
- **(Bug Fix)** Amazon Q Feature Development: Update error message when repo size larger than 200 megabytes
- **(Bug Fix)** Always show device code prompt when performing device code grant through a legacy SSO configuration

# _3.7_ (2024-05-29)
- **(Bug Fix)** (featureDev): Revert fix for file rejection. Reason: The plan disappears after clicking generate Code
- **(Bug Fix)** Amazon Q Code Transformation: show more specific error messages on failure cases

# _3.6_ (2024-05-28)
- **(Bug Fix)** Fix recurring popup "refreshing token" whne users're typing in the IDE and Q connection expires

# _3.5_ (2024-05-23)
- **(Bug Fix)** Amazon Q Code Transformation: show exact error messages in chat when job fails

# _3.4_ (2024-05-16)
- **(Bug Fix)** Amazon Q Chat: Prompt input field in Q Chat tabs doesn't stop after it reaches to the given maxLength
- **(Bug Fix)** Amazon Q Chat: When window gets focus, even though the autoFocus property is set to true, input field doesn't get focus
- **(Bug Fix)** Amazon Q Chat: Inside chat body, if there is a code block inside a list item it shows <br/> tags
- **(Bug Fix)** Security Scan: Improved error notifications

# _3.3_ (2024-05-14)
- **(Bug Fix)** Don't use `authorization_grant` when performing SSO login with legacy SSO or non-commercial AWS regions

# _3.2_ (2024-05-13)
- **(Feature)** Amazon Q: Updated status bar icons including an explicit icon for an unconnected state
- **(Feature)** Human in the loop - Adding human intervention to help update dependencies during the Amazon Q Transformation process
- **(Feature)** Improve the SSO login experience by switching to the Authorization Code with PKCE flow
- **(Bug Fix)** Fix AWS SSO connection when authenticating with RDS using an IAM Identity Center profile ([#4145](https://github.com/aws/aws-toolkit-jetbrains/issues/4145))
- **(Bug Fix)** Amazon Q: Reduce frequency of automated code scans and terminate superseded file scans.
- **(Bug Fix)** Amazon Q Code Transformation: ensure chat does not freeze with /transform on an invalid project
- **(Bug Fix)** Amazon Q: Fix issue where items listed by Amazon Q Code Scan were duplicated or missing
- **(Bug Fix)** Amazon Q Chat: Typewriter animator parts showing up in code fields inside listitems
- **(Bug Fix)** Removed install Q notification if Q is already installed
- **(Bug Fix)** Amazon Q: Avoid duplicate credential expired notifications during startup
- **(Bug Fix)** Amazon Q: Support disabling auto-scan for unsupported languages.

# _3.1_ (2024-04-30)
- **(Bug Fix)** Amazon Q Feature Development: Handle generated code parsing for rendering references correctly
- **(Bug Fix)** Amazon Q Chat: Copy to clipboard on code blocks doesn't work
- **(Bug Fix)** Amazon Q Chat: Fixed markdown is not getting parsed inside list items.
- **(Bug Fix)** Fix help icon in the AWS Explorer pointing to the wrong auth instructions page
- **(Bug Fix)** Amazon Q: Fix an issue where /dev usage would cause the UI to freeze and take an unusually long time to complete. ([#4269](https://github.com/aws/aws-toolkit-jetbrains/issues/4269))
- **(Bug Fix)** Fix for Code Scan Issue editor popup for Builder Id users.

# _3.0_ (2024-04-29)
- **(Feature)** Amazon Q: Security scans can now run automatically when file changes are made
- **(Feature)** Amazon Q: Send security issue to chat for explanation and fix
- **(Feature)** Amazon Q: Security scans can now run on all files in the project
- **(Feature)** Amazon Q Chat: Added additional parameters to onCopyCodeToClipboard and onCodeInsertToCursorPosition events
- **(Feature)** Amazon Q Code Transformation: include details about expected changes in transformation plan
- **(Feature)** Connection id is now shown beside CodeCatalyst dropdown
- **(Feature)** Amazon Q Chat: Updates quick action commands style and groupings
- **(Bug Fix)** Amazon Q Chat: Q panel doesn't fit to its parent
- **(Bug Fix)** Amazon Q Code Feature Development: Update welcome message and menu item description for /dev command
- **(Bug Fix)** Amazon Q Feature Development: Update error message for monthly conversation limit reach

# _2.19_ (2024-04-19)
- **(Feature)** Enable Amazon Q feature development and Amazon Q transform capabilities (/dev and /transform) for AWS Builder ID users.
- **(Bug Fix)** Amazon Q Code Transformation: ensure full error message shown in notifications
- **(Bug Fix)** Fix issue with competing SDK proxy configuration ([#4279](https://github.com/aws/aws-toolkit-jetbrains/issues/4279))

# _2.18_ (2024-04-12)
- **(Feature)** Add support for Lambda runtime Java 21
- **(Feature)** Add support for Lambda runtime Node.js 20
- **(Feature)** Add support for Lambda runtime Python 3.12
- **(Bug Fix)** CodeWhisperer: handle exception when code scan service returns out of bounds line numbers
- **(Bug Fix)** Amazon Q Code Feature Development: fix the welcome message for /dev command
- **(Removal)** Drop support for the Python 3.7 Lambda runtime
- **(Removal)** Drop support for the Node.js14 Lambda runtime
- **(Removal)** Drop support for the .NET 5.0 Lambda runtime
- **(Removal)** Removed support for Gateway 2023.3
- **(Removal)** Removed support for 2023.1.x IDEs
- **(Removal)** Drop support for the Java 8 (AL2012) Lambda runtime

# _2.17_ (2024-04-04)
- **(Bug Fix)** Fix "null" appearing in feedback dialog prompts
- **(Bug Fix)** Amazon Q Code Transformation - Omit Maven metadata files when uploading dependencies to fix certain build failures in backend.
- **(Bug Fix)** Amazon Q Code Transformation: use actual project JDK when transforming project

# _2.16_ (2024-03-29)
- **(Bug Fix)** Fix issue where Amazon Q Chat does not appear in IDEs other than IntelliJ IDEA ([#4218](https://github.com/aws/aws-toolkit-jetbrains/issues/4218))

# _2.15_ (2024-03-28)
- **(Feature)** CodeTransform: new experience with Amazon Q chat integration
- **(Bug Fix)** Move 'Send to Amazon Q' action group to the bottom of right click menu
- **(Bug Fix)** Fix scripts missing when connecting through JetBrains Gateway ([#4188](https://github.com/aws/aws-toolkit-jetbrains/issues/4188))

# _2.14_ (2024-03-21)
- **(Feature)** Amazon Q + CodeWhisperer: Most Amazon Q + CodeWhisperer actions are now migrated from the AWS Toolkit panel to the Amazon Q status bar menu.
- **(Bug Fix)** CodeCatalyst: Update status of connection in developer tools if the user connection is expired.
- **(Bug Fix)** Respect IDE HTTP proxy server settings when using Amazon Q
- **(Removal)** CodeTransformation: remove play button from Transformation Hub, instead use /transform in chat

# _2.13_ (2024-03-13)
- **(Feature)** CodeTransform: add button to submit feedback when job fails

# _2.12_ (2024-03-12)
- **(Feature)** Add configurable auto plugin update feature
- **(Feature)** Amazon Q: Support feature development (/dev)
- **(Bug Fix)** Show better error message on upload zip errors for Q Code Transform.
- **(Bug Fix)** CodeWhisperer: Include copied code in percentage code written metrics
- **(Deprecation)** An upcoming release will remove support for JetBrains Gateway version 2023.3 and for for IDEs based on the 2023.1 platform

# _2.11_ (2024-03-07)
- **(Bug Fix)** Move 'Send to Amazon Q' action group after the 'Show Context Actions' action
- **(Bug Fix)** fix(CodeTransform): Updating commands for copying dependencies
- **(Bug Fix)** Fix 'ActionUpdateThread.OLD_EDT' deprecation errors in 2024.1

# _2.10_ (2024-02-29)
- **(Feature)** Amazon Q CodeTransform: show link to docs in error notifications
- **(Feature)** Security issue hover telemetry includes additional metadata
- **(Feature)** CodeWhisperer: Add startUrl to security scan telemetry

# _2.9_ (2024-02-22)
- **(Feature)** Add startUrl in Amazon Q telemetry events
- **(Feature)** CodeTransformation: block upload if project > 1GB
- **(Bug Fix)** Amazon Q: Service exceptions are not suppressed and displayed to the user.

# _2.8_ (2024-02-15)
- **(Feature)** CodeTransform: smart select Java version of project
- **(Bug Fix)** Fix for AmazonQ on Linux input focus problem ([#4100](https://github.com/aws/aws-toolkit-jetbrains/issues/4100))

# _2.7_ (2024-02-07)
- **(Bug Fix)** CodeWhisperer: Improve CodePercentage telemetry reporting

# _2.6_ (2024-02-02)
- **(Feature)** Add support for IAM Identity Center connections for CodeCatalyst

# _2.5_ (2024-01-24)
- **(Bug Fix)** Fix issue where CodeWhisperer suggestions are sometimes trimmed

# _2.4_ (2024-01-10)
- **(Bug Fix)** Fix Code Transform Plan UI component alignment
- **(Bug Fix)** Fix Code Transform uploaded artifact file paths when submitting from Windows machine

# _2.3_ (2024-01-05)
- **(Feature)** Emit additional CodeTransform telemetry
- **(Feature)** Amazon Q Transform: Use the IDE Maven runner as a fallback
- **(Feature)** Add Job Id to Code Transform Job History
- **(Bug Fix)** Amazon Q Transform: Always execute IDE bundled maven whenever maven command fails
- **(Bug Fix)** Improved recursion when validating projects for Q Code Transform.
- **(Bug Fix)** CodeWhisperer: fix code scan UI of viewing scanned files not reflecting correct files

# _2.2_ (2023-12-13)
- **(Feature)** CodeWhisperer security scans support ruby files.
- **(Feature)** Use MDE endpoint set by environment variable
- **(Feature)** CodeWhisperer security scans now support Go files.
- **(Bug Fix)** Normalize telemetry logging metrics for AmazonQ Transform
- **(Bug Fix)** Fix telemetry logging for new Amazon Q Code Transform telemetry updates
- **(Bug Fix)** CodeWhisperer: Increase polling frequency for security scans.
- **(Bug Fix)** Fixed sign out button in the CodeWhisperer panel for Getting Started Page
- **(Bug Fix)** Fix issue where the CodeWhisperer status bar widget is visible in a remote development environment
- **(Bug Fix)** Amazon Q Transform: Fix to ensure backend gets necessary dependencies
- **(Removal)** Removed support for 2022.3.x IDEs
- **(Removal)** Removed support for Gateway 2023.2

# _2.1_ (2023-12-04)
- **(Feature)** CodeWhisperer: Simplify Learn More page
- **(Bug Fix)** CodeWhisperer: Security scans for Java no longer require build artifacts
- **(Bug Fix)** Amazon Q Transform: Fix an issue where the IDE may freeze after clicking "Transform"
- **(Bug Fix)** Fix JetBrains Gateway specific notifications being shown in non-Gateway IDEs

# _2.0_ (2023-11-28)
- **(Feature)** Support for Amazon Q, your generative AI–powered assistant designed for work that can be tailored to your business, code, data, and operations.

# _1.89_ (2023-11-26)
- **(Feature)** CodeWhisperer: Uses Generative AI and automated reasoning to rewrite lines of code flagged for security vulnerabilities during a security scan.
- **(Feature)** CodeWhisperer now supports new IaC languages: JSON, YAML and Terraform.
- **(Feature)** CodeWhisperer security scans support typescript, csharp, json, yaml, tf and hcl files.

# _1.88_ (2023-11-17)
- **(Bug Fix)** Fix issue where the toolkit calls the wrong CodeCatalyst service endpoint

# _1.87_ (2023-11-10)
- **(Bug Fix)** Fix issue where images in 'Authenticate' panel do not show up
- **(Deprecation)** An upcoming release will remove support for JetBrains Gateway version 2023.2 and for for IDEs based on the 2022.3 platform

# _1.86_ (2023-11-08)
- **(Feature)** Added the 'Setup Authentication for AWS Toolkit' page
- **(Feature)** Added 2023.3 support
- **(Feature)** auth: support `sso_session` for profiles in AWS shared ini files
- **(Bug Fix)** CodeWhisperer: Fix an issue where an IndexOutOfBoundException could be thrown when using CodeWhisperer

# _1.85_ (2023-10-27)
- **(Feature)** CodeWhisperer: reduce auto-suggestions when there is immediate right context

# _1.84_ (2023-10-17)
- **(Feature)** Public preview for CodeWhisperer Enterprise: Enterprise customers can now customize CodeWhisperer to adopt and suggest code based on organization specific codebases.

# _1.83_ (2023-10-13)
- **(Feature)** CodeWhisperer: improve auto-suggestions for additional languages

# _1.82_ (2023-10-06)
- **(Bug Fix)** CodeWhisperer: Fixed an issue where the "Learn CodeWhisperer" page is shown for Gateway host

# _1.80_ (2023-09-29)
- **(Feature)** Authentication: When signing in to AWS Builder Id or IAM Identity Center (SSO), verify the device code matches instead of copy-pasting it
- **(Feature)** CodeWhisperer: Improve the onboarding experience by showing a new onboarding tutorial to first-time users.
- **(Bug Fix)** Fix issue displaying SSO code on new UI in Windows

# _1.81_ (2023-09-29)

# _1.79_ (2023-09-15)

# _1.78_ (2023-09-08)
- **(Bug Fix)** Fix 'not recognzied as an ... command' when connecting to CodeCatalyst Dev Environments on Windows

# _1.77_ (2023-08-29)
- **(Removal)** Removed support for 2022.2.x IDEs
- **(Removal)** Removed support for Gateway 2023.1

# _1.76_ (2023-08-15)
- **(Feature)** CodeWhisperer: Improve file context fetching for Python Typescript Javascript source files
- **(Feature)** CodeWhisperer: Improve file context fetching for Java test files

# _1.75_ (2023-08-03)
- **(Feature)** Add support for Lambda runtime Python 3.11
- **(Bug Fix)** codewhisperer: file context fetching not considering file extension correctly
- **(Deprecation)** An upcoming release will remove support for JetBrains Gateway version 2023.1 and for for IDEs based on the 2022.2 platform

# _1.74_ (2023-07-25)
- **(Feature)** Explorer is automatically refreshed with new credentials when they are added to credential file.
- **(Feature)** Added 2023.2 support
- **(Bug Fix)** Fix 'No display name is specified for configurable' in 2023.2

# _1.73_ (2023-07-19)
- **(Feature)** CodeWhisperer: Improve Java suggestion quality with enhanced file context fetching
- **(Bug Fix)** CodeWhisperer: Run read operation in the background thread without runReadAction
- **(Bug Fix)** CodeWhisperer: Fix an issue where CodeWhisperer would stuck in the invocation state indefinitely

# _1.72_ (2023-07-11)
- **(Feature)** CodeWhisperer: Improve suggestion quality with enhanced file context fetching
- **(Bug Fix)** Fix AWS Lambda configuration window resize ([#3657](https://github.com/aws/aws-toolkit-jetbrains/issues/3657))

# _1.71_ (2023-07-06)
- **(Bug Fix)** Fix inproper request format when sending empty supplemental context

# _1.70_ (2023-06-27)
- **(Feature)** CodeWhisperer improves auto-suggestions for tsx and jsx
- **(Bug Fix)** Show re-authenticate prompt when invoking CodeWhisperer APIs while connection expired

# _1.69_ (2023-06-13)
- **(Feature)** CodeWhisperer improves auto-suggestions for python csharp typescript and javascript
- **(Feature)** Removed 10 secs delay when connecting to Dev environments of Small Instance Size
- **(Feature)** CodeWhisperer: Improve file context fetching logic
- **(Bug Fix)** Inlay not supported exception in injected editor
- **(Bug Fix)** fix right context merging not accounting userinput, which result in cases CodeWhisperer still show recommendation where user already type the content of recommendation out thus no character is being inserted by CodeWhisperer
- **(Bug Fix)** Add error notification to upgrade SAM CLI v1.85-1.86.1 if on windows
- **(Bug Fix)** Always use AWS smile logo to reduce confusion if users are on the 'New UI' ([#3636](https://github.com/aws/aws-toolkit-jetbrains/issues/3636))
- **(Removal)** Remove support for Gateway 2022.2 and 2022.3.

# _1.68_ (2023-05-30)
- **(Feature)** CodeWhisperer supports application wide connections
- **(Feature)** CodeWhisperer improves auto-suggestions for java
- **(Bug Fix)** Fix threading issue preventing SAM Applications from opening in Rider 2023.1
- **(Bug Fix)** Fix issue reconnecting to CodeWhisperer using an Identity Center directory outside of us-east-1 ([#3662](https://github.com/aws/aws-toolkit-jetbrains/issues/3662))
- **(Bug Fix)** Fix 'null' is not a connection when authenticating to CodeWhisperer
- **(Bug Fix)** CodeWhisperer: user is sometimes required to re-login before token expiration
- **(Bug Fix)** Fix issue where the "Do not ask again" option is not respected when switching connections on CodeWhisperer/CodeCatalyst
- **(Deprecation)** An upcoming release will remove support for JetBrains Gateway version 2022.2 and version 2022.3
- **(Removal)** Remove support for Aurora MySQL v1 ([#3356](https://github.com/aws/aws-toolkit-jetbrains/issues/3356))
- **(Removal)** Removed support for 2022.1.x IDEs

# _1.67_ (2023-04-27)
- **(Feature)** Using the least permissive set of scopes for features during BuilderID/SSO login. Using the same connection for multiple features will request additional scopes to be used.
- **(Feature)** Add support for Lambda Runtime Java17
- **(Bug Fix)** Fix the Add Connection Dialog box references to the correct documentation pages
- **(Bug Fix)** Fix thread access during validation of SAM templates
- **(Bug Fix)** [CodeWhisperer]: login session length should increase to it's expected length. Users will now see less frequent expired sessions.
- **(Bug Fix)** Improve handling of disk errors related to SSO and align folder permissions with AWS CLI

# _1.66_ (2023-04-19)
- **(Feature)** Display current space and project name on status bar while working in a CodeCatalyst Dev Environment
- **(Feature)** Add support for Lambda runtime Python 3.10
- **(Bug Fix)** Fix `java.lang.Throwable: Invalid html: tag <html> inserted automatically and shouldn't be used` ([#3608](https://github.com/aws/aws-toolkit-jetbrains/issues/3608))
- **(Bug Fix)** Fix issue where nothing happens when trying to create an empty Dev Environment

# _1.65_ (2023-04-13)
- **(Feature)** [CodeWhisperer]: Introducing "Stop code scan" feature where users will be able to stop the ongoing code scan and immediately start a new one.
- **(Feature)** [CodeWhisperer]: Automatic import recommendations
- **(Feature)** [CodeWhisperer]: Now supports cross region calls.
- **(Feature)** Attempt to download IDE thin client earlier in the CodeCatalyst Dev Environment connection process
- **(Feature)** [CodeWhisperer]: New supported programming languages: C, C++, Go, Kotlin, Php, Ruby, Rust, Scala, Shell, Sql.
- **(Bug Fix)** Include more information in the Dev Environment status tooltip
- **(Bug Fix)** Provide consistent UX in all Dev Environment wizard variants
- **(Bug Fix)** Fix 'MissingResourceException: Registry key is not defined'
- **(Bug Fix)** [CodeWhisperer]: Multiple bug fixes to improve user experience
- **(Removal)** Drop support for the Node.js 12.x Lambda runtime
- **(Removal)** Drop support for the .NET Core 3.1 Lambda runtime

# _1.64_ (2023-03-29)
- **(Breaking Change)** Required SAM CLI upgrade to v1.78.0 to for using Sync Serverless Application option.
- **(Feature)** Support for RDS MariaDB instances ([#3530](https://github.com/aws/aws-toolkit-jetbrains/issues/3530))
- **(Feature)** Added 2023.1 support
- **(Deprecation)** An upcoming release will remove support for IDEs based on the 2022.1 platform

# _1.63_ (2023-03-24)
- **(Bug Fix)** Fix issue where multiple Builder ID entries show up in connection list
- **(Bug Fix)** Fix temporary deadlock when user fails to complete reauthentication request
- **(Bug Fix)** Only allow cloning a repository from CodeCatalyst if it's hosted on CodeCatalyst

# _1.62_ (2023-03-20)
- **(Bug Fix)** Show friendlier application name when signing in using SSO
- **(Bug Fix)** Fix confusing experience when attempting to sign in to multiple Builder IDs

# _1.61_ (2023-02-17)
- **(Bug Fix)** Authenticating through the browser now requires users to manually enter a user verification code for SSO/AWS Builder ID
- **(Bug Fix)** Fix NPE that may occur when installing the toolkit for the first time ([#3433](https://github.com/aws/aws-toolkit-jetbrains/issues/3433))
- **(Bug Fix)** Fix network calls cant be made inside read/write action exception thrown from CodeWhisperer ([#3423](https://github.com/aws/aws-toolkit-jetbrains/issues/3423))

# _1.60_ (2023-02-01)
- **(Bug Fix)** Fix Small Dev Environment instance sizes not connecting to the thin clients

# _1.59_ (2023-01-27)
- **(Feature)** Added an option to submit feedback for the AWS Toolkit in JetBrains Gateway

# _1.58_ (2023-01-12)
- **(Feature)** CodeWhisperer: more responsive Auto-Suggestions
- **(Feature)** Added Nodejs18.x Lambda runtime support
- **(Bug Fix)** Fix regression in requirements.txt detection ([#3041](https://github.com/aws/aws-toolkit-jetbrains/issues/3041))
- **(Bug Fix)** Fix `com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException when choosing an input template in Lambda Run Configurations` ([#3359](https://github.com/aws/aws-toolkit-jetbrains/issues/3359))
- **(Bug Fix)** Fix Lambda Python console encoding issue ([#2802](https://github.com/aws/aws-toolkit-jetbrains/issues/2802))

# _1.57_ (2022-12-15)
- **(Feature)** Change reauthentication prompt to be non-distruptive notification.
- **(Bug Fix)** Add do not show again button for CodeWhisperer accountless usage notification
- **(Bug Fix)** Fix CodeWhisperer status widget is shown even when users are disconnected

# _1.56_ (2022-12-08)
- **(Bug Fix)** Remove redundant calls in certain Gateway UI panels
- **(Bug Fix)** Fix threading issue while attempting to login to CodeCatalyst
- **(Bug Fix)** Only list dev environments under projects that users are a member of
- **(Bug Fix)** Fix 'Learn more' link in Gateway 2022.2
- **(Bug Fix)** Fix connection issue with CodeCatalyst when user is already logged into CodeWhisperer

# _1.55_ (2022-12-01)
- **(Feature)** Amazon CodeCatalyst: Connect JetBrains to your remote Dev Environments.
- **(Feature)** Amazon CodeCatalyst: Clone your repositories to your local machine.
- **(Feature)** Amazon CodeCatalyst: Connect using your AWS Builder ID.

# _1.54_ (2022-11-28)
- **(Feature)** Amazon CodeWhisperer now supports JavaScript for Security Scan to catch security vulnerabilities.
- **(Feature)** Amazon CodeWhisperer recommendations are more context aware. We are removing the overlaps from CodeWhisperer suggestions specifically when the cursor is inside a code block.
- **(Feature)** Amazon CodeWhisperer now supports TypeScript and C# programming languages.
- **(Feature)** Amazon CodeWhisperer is now available as a supported feature and no longer an experimental feature.
- **(Feature)** Amazon CodeWhisperer now adds new access methods with AWS Builder ID and AWS IAM Identity Center to enable and get started.

# _1.53_ (2022-11-23)
- **(Feature)** Sync Serverless Application(SAM Accelerate)
- **(Feature)** New experiment to allow injection of AWS Connection details (region/credentials) into Golang Run Configurations
- **(Removal)** Removed support for 2021.3.x IDEs

# _1.52_ (2022-10-19)
- **(Feature)** Added 2022.3 support
- **(Bug Fix)** Fix `credential_process` retrieval when command contains quoted arguments on Windows ([#3322](https://github.com/aws/aws-toolkit-jetbrains/issues/3322))
- **(Deprecation)** An upcoming release will remove support for IDEs based on the 2021.3 platform
- **(Bug Fix)** Fix `java.lang.IllegalStateException: Region provider data is missing default data` ([#3264](https://github.com/aws/aws-toolkit-jetbrains/issues/3264))

# _1.51_ (2022-09-22)
- **(Feature)** Resources (in AWS Explorer) can list more resource types for EC2, IoT, RDS, Redshift, NetworkManager, and other services
- **(Feature)** CodeWhisperer now supports .jsx files
- **(Bug Fix)** CodeWhisperer fixes

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

