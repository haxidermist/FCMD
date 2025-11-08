# Publishing FCMD to GitHub

This guide will walk you through publishing your FCMD project to GitHub.

## Prerequisites

1. **GitHub Account**: Create one at https://github.com if you don't have one
2. **Git Installed**: Verify with `git --version`
3. **GitHub CLI (optional)**: Install from https://cli.github.com for easier setup

## Step 1: Create GitHub Repository

### Option A: Using GitHub Website

1. Go to https://github.com/new
2. Fill in repository details:
   - **Repository name**: `FCMD` or `android-metal-detector`
   - **Description**: `Professional metal detector app for Android using multi-frequency IQ demodulation`
   - **Public/Private**: Choose based on your preference
   - **⚠️ IMPORTANT**: Do NOT initialize with README, .gitignore, or license (we already have these)
3. Click "Create repository"
4. Copy the repository URL (will look like `https://github.com/[your-username]/FCMD.git`)

### Option B: Using GitHub CLI

```bash
gh repo create FCMD --public --description "Professional metal detector app for Android using multi-frequency IQ demodulation"
```

## Step 2: Prepare Your Local Repository

### Review What Will Be Committed

```bash
# Check current status
git status

# Review changes
git diff HEAD
```

### Stage All Files

```bash
# Add all new files and modifications
git add .

# Verify what's staged
git status
```

You should see files like:
- ✅ README.md
- ✅ LICENSE
- ✅ CONTRIBUTING.md
- ✅ All .kt source files
- ✅ Documentation (*.md, *.html)
- ✅ Resource files (layouts, drawables)
- ❌ .claude/settings.local.json (excluded by .gitignore)
- ❌ build/ directories (excluded by .gitignore)

## Step 3: Create Initial Commit

```bash
# Commit with a descriptive message
git commit -m "Initial commit: FCMD - Professional Android metal detector

Features:
- Multi-frequency IQ demodulation (1-24 tones, 1-20 kHz)
- Real-time DSP processing at 44.1 kHz
- VDI target discrimination (0-99 scale)
- Ground balance (Manual, Auto-tracking, Manual+Tracking modes)
- Depth estimation (categorical)
- Audio feedback system
- Complete documentation and ebook

🤖 Generated with Claude Code
"
```

## Step 4: Connect to GitHub Remote

```bash
# Add GitHub as remote (replace [your-username] with your GitHub username)
git remote add origin https://github.com/[your-username]/FCMD.git

# Verify remote was added
git remote -v
```

## Step 5: Push to GitHub

```bash
# Push to master branch (or main, depending on your setup)
git push -u origin master
```

If you get an authentication error, you'll need to set up credentials:

### Authentication Options

**Option 1: Personal Access Token (Recommended)**
1. Go to https://github.com/settings/tokens
2. Click "Generate new token (classic)"
3. Select scopes: `repo` (full control of private repositories)
4. Generate and copy the token
5. Use token as password when pushing

**Option 2: SSH Key**
```bash
# Generate SSH key
ssh-keygen -t ed25519 -C "your-email@example.com"

# Add to ssh-agent
eval "$(ssh-agent -s)"
ssh-add ~/.ssh/id_ed25519

# Copy public key
cat ~/.ssh/id_ed25519.pub

# Add to GitHub: Settings → SSH and GPG keys → New SSH key

# Update remote to use SSH
git remote set-url origin git@github.com:[your-username]/FCMD.git
git push -u origin master
```

## Step 6: Verify on GitHub

1. Go to `https://github.com/[your-username]/FCMD`
2. Verify:
   - ✅ README.md displays on main page
   - ✅ All source files are present
   - ✅ Documentation is viewable
   - ✅ License is recognized
   - ✅ No sensitive files (.claude/settings.local.json, build artifacts)

## Step 7: Create Releases (Optional)

### Tag Current Version

```bash
# Create annotated tag
git tag -a v1.0.0 -m "Release v1.0.0 - Initial public release

Features:
- Multi-frequency IQ demodulation
- VDI discrimination
- Ground balance
- Depth estimation
- Complete documentation
"

# Push tags to GitHub
git push origin --tags
```

### Create GitHub Release

1. Go to `https://github.com/[your-username]/FCMD/releases`
2. Click "Draft a new release"
3. Select tag: `v1.0.0`
4. Release title: `v1.0.0 - Initial Public Release`
5. Description:
```markdown
## First Public Release 🎉

FCMD is now open source! This professional-grade metal detector app demonstrates advanced DSP on Android.

### Features
- **Multi-frequency IQ demodulation**: 1-24 tones, 1-20 kHz
- **VDI discrimination**: Classify ferrous/non-ferrous metals
- **Ground balance**: 4 modes including auto-tracking
- **Depth estimation**: Category-based depth indication
- **Real-time DSP**: 44.1 kHz processing, ~23 Hz update rate
- **Complete documentation**: 50+ page ebook included

### Installation
1. Download APK (coming soon)
2. Or build from source (see README.md)

### Documentation
- [Complete Ebook](Android_Metal_Detection_Ebook.html)
- [README](README.md)
- [Contributing Guide](CONTRIBUTING.md)

### Requirements
- Android 5.0+ device
- External transmit/receive coils (see docs)

**Full Changelog**: Initial release
```
6. Attach APK if you have a release build
7. Publish release

## Step 8: Set Up Repository Settings

### Add Topics (Tags)

1. Go to repository main page
2. Click "⚙️" next to About
3. Add topics:
   - `metal-detector`
   - `android`
   - `dsp`
   - `signal-processing`
   - `iq-demodulation`
   - `kotlin`
   - `audio-processing`
   - `open-source`
   - `maker`
   - `embedded`

### Enable GitHub Pages (for HTML documentation)

1. Go to Settings → Pages
2. Source: Deploy from branch
3. Branch: `master` or `main`, folder: `/ (root)`
4. Save

Your HTML docs will be available at:
`https://[your-username].github.io/FCMD/signal_flow_diagram.html`

### Set Repository Description

1. Go to repository main page
2. Click "⚙️" next to About
3. Description: `Professional metal detector for Android using multi-frequency IQ demodulation and real-time DSP`
4. Website: Your GitHub Pages URL (if enabled)
5. Check ✅ "Use topics"

## Step 9: Ongoing Maintenance

### Making Changes

```bash
# Make code changes
# ...

# Check what changed
git status
git diff

# Stage changes
git add [files]

# Commit
git commit -m "Add feature X

- Implement Y
- Fix Z

Fixes #123"

# Push to GitHub
git push origin master
```

### Creating Branches for Features

```bash
# Create and switch to feature branch
git checkout -b feature/improve-vdi-accuracy

# Make changes, commit
git add .
git commit -m "Improve VDI accuracy for gold detection"

# Push branch
git push -u origin feature/improve-vdi-accuracy

# Create Pull Request on GitHub
# Merge via GitHub UI
# Delete branch after merge
```

## Step 10: Community Engagement

### Enable Discussions

1. Go to Settings → General
2. Features → Check ✅ Discussions
3. Set up categories:
   - Q&A
   - Ideas
   - Show and Tell
   - Hardware Builds

### Create Issue Templates

Create `.github/ISSUE_TEMPLATE/bug_report.md`:

```markdown
---
name: Bug report
about: Create a report to help us improve
title: '[BUG] '
labels: bug
---

**Describe the bug**
A clear and concise description.

**To Reproduce**
Steps to reproduce:
1. Go to '...'
2. Click on '....'
3. See error

**Expected behavior**
What you expected to happen.

**Device:**
 - Device: [e.g. Samsung Galaxy S21]
 - Android Version: [e.g. Android 13]
 - App Version: [e.g. 1.0.0]

**Logs**
```
Paste logcat here
```

**Additional context**
Add any other context about the problem here.
```

### Add Code of Conduct

Create `CODE_OF_CONDUCT.md`:

```markdown
# Code of Conduct

Be respectful, constructive, and helpful.
Help make metal detection technology accessible to everyone.
```

## Troubleshooting

### Issue: "remote: Permission denied"
**Solution**: Check your authentication (PAT or SSH key)

### Issue: ".gitignore not working"
**Solution**: Clear cache and re-add
```bash
git rm -r --cached .
git add .
git commit -m "Fix .gitignore"
```

### Issue: "Large files rejected"
**Solution**: Use Git LFS for files >50MB
```bash
git lfs install
git lfs track "*.apk"
git add .gitattributes
```

### Issue: "Merge conflict"
**Solution**:
```bash
git pull origin master
# Resolve conflicts in files
git add [resolved-files]
git commit -m "Resolve merge conflict"
git push
```

## Quick Reference Commands

```bash
# Clone your repo (fresh start)
git clone https://github.com/[your-username]/FCMD.git

# Check status
git status

# Stage all changes
git add .

# Commit
git commit -m "Your message"

# Push to GitHub
git push

# Pull latest from GitHub
git pull

# View commit history
git log --oneline --graph

# Create branch
git checkout -b feature-name

# Switch branches
git checkout master

# Merge branch
git merge feature-name

# Tag release
git tag -a v1.0.1 -m "Release 1.0.1"
git push origin --tags
```

## Next Steps

1. ✅ Repository published
2. ⏭️ Share on social media, forums, maker communities
3. ⏭️ Monitor issues and pull requests
4. ⏭️ Build community around the project
5. ⏭️ Continue improving based on feedback

---

**Congratulations! Your FCMD project is now open source on GitHub!** 🎉

For questions: Open an issue or discussion on GitHub
