// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests.profileTests

import software.aws.toolkits.jetbrains.uitests.testScriptPrefix

// language=JS
val testActiveToolWindowPage = """
$testScriptPrefix

async function testProfileSelector() {
  const { JSDOM } = require('jsdom');
  const dom = new JSDOM('<!DOCTYPE html><html><body></body></html>');
  global.window = dom.window;
  global.document = dom.window.document;
  global.self = dom.window;

  const MynahUITestIds = require("@aws/mynah-ui").MynahUITestIds;
  const browser = await puppeteer.connect({
    browserURL: "http://localhost:9222"
  })
  try {
    const pages = await browser.pages()
    
    // Use the last page (most recently active)
    const activePage = pages[pages.length - 1]
    
    if(!activePage) {
      console.log("No pages found")
      return
    }

    // Check the active page for chat input or profile selector
    const chatInput = await activePage.${'$'}(`[${'$'}{MynahUITestIds.selector}="${'$'}{MynahUITestIds.prompt.input}"]`)
    if(chatInput) {
      console.log("Chat is shown")
      return
    }

    const profileSelector = await activePage.${'$'}('div#profile-page')
    if(profileSelector) {
      console.log("Profile selector is shown")
      return
    }

    console.log("Neither profile selector nor chat found on active page")
  } finally {
    await browser.close()
  }
}
testProfileSelector().catch(console.error);
""".trimIndent()
