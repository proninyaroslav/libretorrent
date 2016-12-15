LibreTorrent
=====================

[<img alt="Get it on Google Play" height="80" src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png">](https://play.google.com/store/apps/details?id=org.proninyaroslav.libretorrent)
[<img alt="Get it on F-Droid" height="80" src="https://f-droid.org/badge/get-it-on.png">](https://f-droid.org/app/org.proninyaroslav.libretorrent)

**Mirror:** https://proninyaroslav.ru/ftp/libretorrent/

LibreTorrent is a Free as in Freedom torrent client for Android 4+, based on libtorrent (Java wrapper [frostwire-jlibtorrent](https://github.com/frostwire/frostwire-jlibtorrent)) lib.

Overview
---

Already implemented features (help is highly desired!):

 - DHT, PeX, encryption, LSD, UPnP, NAT-PMP, µTP
 - IP filtering (eMule dat and PeerGuardian)
 - Ability to fine tune (network settings, power management, battery control, UI settings, etc.)
 - Supports torrents with large number of files and big files
 - HTTP\S and magnet links support
 - Support proxy for trackers and peers
 - Ability to move files while downloading
 - Ability to automatic movement of files to another directory or to an external drive at the end of download
 - Ability to specify file and folder priorities
 - Ability to select which files to download
 - Ability to download sequentially
 - Material Design
 - Tablet optimized UI

Translations
---

Current languages:

 - English
 - Russian
 - German *(thanks vanitasvitae)*
 - Lithuanian *(thanks techwebpd)*
 - Polish *(thanks Azaradel)*
 - Spanish *(thanks ed10vi)*
 - Brazilian Portuguese *(thanks Wolfterro)*
 - French *(thanks Valentin Orient)*

Contributors
---

If you want to contribute code, start by looking at the open issues on github.com:

  https://github.com/proninyaroslav/libretorrent/issues.

Make sure to write your name and email id in format Name \<e-mail\> in the license declaration above every file you make change to.

Repeat and rinse, if you send enough patches to demonstrate you have a good coding skills, we'll just give you commit access on the real repo and you will be part of the development team.

Also, take a look at Coding Guidelines before making changes in code.

Coding Guidelines
---

 - Keep it simple, stupid. (KISS: https://en.wikipedia.org/wiki/KISS_principle)
 - Do not repeat yourself. (DRY: https://en.wikipedia.org/wiki/Don%27t_repeat_yourself) Re-use your own code and our code.
 - If you want to help, the Issue tracker and TODO list is a good place to take a look at.
 - Try to follow our coding style and formatting before submitting a patch.
 - All pull requests should come from a feature branch created on your git fork. We'll review your code and will only merge it to the master branch if it doesn't break the build.
 - When you submit a pull request try to explain what issue you're fixing in detail and how you're fixing in detail it so it's easier for us to read your patches.
 - We prefer well named methods and code re-usability than a lot of comments.

Screenshots
---

![phone](/doc/screenshots/phone.png) ![phone dark](/doc/screenshots/phone_dark.png) ![tablet](/doc/screenshots/tablet.png)

License
---

    Copyright (C) 2016 Yaroslav Pronin <proninyaroslav@mail.ru>
    This file is part of LibreTorrent.
    LibreTorrent is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
