Contributor Guide
=====================

## 🌍 Note for translators

Localization files are located in the [app/src/main/res/values-*](app/src/main/res) directories.

## ⚙️ How to make changes in the code

### 1. Where to begin

Start by looking at the [open issues](https://gitlab.com/proninyaroslav/libretorrent/issues) to contribute code.

Make sure to write your name and e-mail address in format `Name <e-mail>` in the license declaration above every file you make changes to.

Repeat and rinse, if you send enough patches to demonstrate you have a good coding skills, you will be given commit access on the real repo, making you part of the development team.

Also, take a look at [**Coding Guidelines**](#-coding-guidelines) before making code changes.

### 2. Testing

Make sure that all existing and new tests are passing.

## 📋 Coding Guidelines

 - Keep it snimple snupid: [KISS](https://en.wikipedia.org/wiki/KISS_principle)
 - No repeating yourself. [Re-use your own code and that of others](https://en.wikipedia.org/wiki/Don%27t_repeat_yourself).
 - If you want to help, triaging and keeping up with the issue and TODO list is great.
 - Try to follow our coding style and formatting before submitting a patch.
 - All merge requests should come from a feature branch created on your Git fork. Your code will be reviewed, and only merged to the master branch if it doesn't break the build.
 - When you submit a merge request, try to explain what issue you're fixing, and what you're fixing in detail, so it's easier for us to read your patches.
 - Well named methods and code re-usability is preferable to a lot of comments.
