# Duplicated Code Check

This check only runs the Qodana DuplicatedCode check and should post duplicated code to [Github PRs](../../.github/workflows/qodana-check-duplicatedcode.yml).

This code currently incorporates a [baseline file](qodana.sarif.json), so existing duplicated code won't fail CI.
* As of July 2025, the GitHub Action will also trigger a failure if duplicates are _removed_ from the baseline, which should trigger a rebaselining.  

## Updating the Baseline File
* Locally: run the "Try Code Analysis with Qodana" action
* Temporarily overwrite the full repo's `qodana.yaml` by pasting in the contents of this job's [`qodana.yaml`](qodana.yaml) file
* After the job finishes, in the "Problems" panel:
  * Click "Server-Side Analysis" to view the Qodana output
  * Click the Globe icon to view the output in the browser
* In the browser:
  * Click the "Select All" checkbox over the problem table
  * Click "Move selected to baseline"
* In the IDE: 
  * Overwrite the `qodana.sarif.json` file in this directory with the generated `qodana.sarif.json`
  * `git checkout qodana.yaml` to restore the full `qodana.yaml` config
  * Commit this `qodana.sarif.json` file along with your updated code
