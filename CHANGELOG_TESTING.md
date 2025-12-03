# Plugin-Specific Changelog Testing Guide

## Overview
This guide explains how to test the plugin-specific changelog system that separates Amazon Q and Toolkit changelogs.

## Testing Workflow

### 1. Create Test Changes

Create changes for each plugin using the interactive task:

```bash
# Create Amazon Q change
./gradlew newChange
# Select "1. amazonq"
# Choose change type (e.g., "2. Feature") 
# Enter description: "Test Amazon Q feature"

# Create Toolkit change  
./gradlew newChange
# Select "2. toolkit"
# Choose change type (e.g., "3. Bug Fix")
# Enter description: "Test Toolkit bug fix"
```

### 2. Generate Plugin-Specific Releases

Test the automation commands used in CI:

```bash
# Generate Amazon Q release and changelog
./gradlew :createAmazonQRelease :generateAmazonQChangeLog

# Generate Toolkit release and changelog  
./gradlew :createToolkitRelease :generateToolkitChangeLog
```

### 3. Verify Results

Check that files were created in the correct locations:

**Amazon Q:**
- `.changes/amazonq/3.99-SNAPSHOT.json` - Release file
- `CHANGELOG-AmazonQ.md` - Updated with new entry

**Toolkit:**
- `.changes/toolkit/3.99-SNAPSHOT.json` - Release file  
- `CHANGELOG-Toolkit.md` - Updated with new entry

**Separation:**
- Amazon Q changes should NOT appear in Toolkit files
- Toolkit changes should NOT appear in Amazon Q files
- Root `CHANGELOG.md` should redirect to plugin-specific files

### 4. Clean Up

Remove test files before committing:

```bash
rm .changes/amazonq/3.99-SNAPSHOT.json
rm .changes/toolkit/3.99-SNAPSHOT.json
git checkout CHANGELOG-AmazonQ.md CHANGELOG-Toolkit.md
```

## Expected Behavior

- Each plugin maintains separate changelog files
- Release files are created in plugin-specific directories
- No cross-contamination between plugins
- System ready for repository split