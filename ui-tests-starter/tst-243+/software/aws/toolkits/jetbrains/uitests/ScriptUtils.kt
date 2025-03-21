// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests

// language=JS
val findAndClickButtonScript = """
const findAndClickButton = async (
  page,
  buttonText,
  clickButton = false,
  timeout = 5000
) => {
  try {
    // Wait for any matching buttons to be present
    await page.waitForSelector('button.mynah-button', {
      visible: true,
      timeout,
    });

    // Find and verify the specific button
    const buttonHandle = await page.evaluateHandle(text => {
      const buttons = Array.from(
        document.querySelectorAll('button.mynah-button')
      );
      return buttons.find(button => {
        const label = button.querySelector('.mynah-button-label');
        return label && label.textContent.trim() === text;
      });
    }, buttonText);

    // Check if button was found
    const button = buttonHandle.asElement();
    if (!button) {
      console.log(buttonText);
      throw new Error(`Button with text not found`);
    }

    // Verify button is visible and enabled
    const isVisible = await page.evaluate(el => {
      const style = window.getComputedStyle(el);
      return (
        style.display !== 'none' &&
        style.visibility !== 'hidden' &&
        style.opacity !== '0'
      );
    }, button);

    if (!isVisible) {
      console.log(buttonText);
      throw new Error(`Button with text is not visible`);
    }

    if (clickButton) {
      // Click the button
      await button.click();

      // Optional wait after click
      await new Promise(resolve => setTimeout(resolve, 1000));

      console.log(`Successfully clicked button with text`);
      console.log(buttonText);
    } else {
      return button;
    }
  } catch (error) {
    console.error(`Error interacting with button:`, buttonText, error);
    throw error;
  }
};
""".trimIndent()
