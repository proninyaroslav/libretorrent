### Version 3.4 (2022-01-30)

* Added:
    - Validate HTTPS trackers option
    - Downloading first and last pieces first
* Bugfixes:
    - "Operation not supported on transport endpoint" message
    - Forced encryption
    - Minor fixes
* libtorrent4j upgraded to 2.0.5-24
* New translations:
    - Bulgarian
* Updated current translations

### Version 3.3 (2021-12-12)

* Android 12 support
* Minimum supported version of Android raised to 7.0
* Bugfixes:
    - Accessing the network interface in Android 11
    - Displaying trackers status
    - Sorting by ETA
    - Minor fixes
* Enabled announcing all trackers by default
* libtorrent4j upgraded to 2.0.4-22-RC1
* New translations:
    - Malay
* Updated current translations

### Version 3.2 (2021-09-16)

* Added:
    - Filtering and sorting in the foreground notification
    - Combo pause button in foreground notification
    - Retaining the selected options in the add dialog
    - Opening torrent files list by finish notification clicking
    - Inverted pause button color
* Bugfixes:
    - Wrapping settings titles
* New translations:
    - Finnish
* Updated current translations

### Version 3.1.1 (2021-08-15)

* Bugfixes:
    - Foreground notification stuck
    - Android 8.0 text field hint bug
    - Removing original torrent trackers
    - Sequential download for magnet links
    - Сhanging the random port after applying the settings
* Updated current translations
* Upgrade to libtorrent4j 2.0.4-21

### Version 3.1 (2021-07-04)

* Added:
    - Default trackers list
    - Option to delete a file in a watched directory
    - Allowed to change watch directory before enabling this option
* Bugfixes:
    - Streaming
    - Proxy applying
    - Random port generation
    - Correct handling of "Do not ask again" for permissions
    - Minor fixes
* New translations:
    - Sinhala (partially)
    - Vietnamese
* Updated current translations
* Upgrade to libtorrent4j 2.0.4-20

### Version 3.0.1 (2021-02-13)

* Bugfixes:
    - Crash on 32-bit CPUs
    - Crash when the "Watch directory" option is enabled
    - Correct path to SD card
    - Opening a torrent file from third-party apps
    - Enabling finish notification in the settings
    - Minor fixes
* Indicator light and vibration enabled for finish notification(reinstallation required)
* New translations:
    - Dutch
* Updated current translations

### Version 3.0 (2021-02-05)

* Removed experimental system file manager support. This can break torrentsthat added in this way
* Android 10 support
* Dropped support for Android 4.x. 5.0 is the minimum supported version
* Added:
    - BitTorrent 2.0 support
    - Tags
    - Auto fetching when paste from clipboard
    - Adaptive shortcuts icons
* Bugfixes
* New translations (partially translated):
    - Arabic
    - Bengali
    - Indonesian
    - Persian
    - Santali
* Updated current translations
* libtorrent4j updated to 2.x version

### Version 2.1 (2020-04-21)

* Added:
    - Multiple filters support in the add feed dialog
    - Displaying folders first in the file manager
* Using the parent folder as the download folder for the created torrent
* Improved dialog for opening downloaded files
* Bugfixing:
    - Crash on Android 4.x
    - Adding trackers
    - Cache settings, that affect download speed (may need to reinstall)
    - Some ANR's
* New translations (partially translated):
    - Basque
    - Catalan
    - Chinese Traditional
    - Swedish
    - Ukrainian
* Updated current translations

### Version 2.0 (2020-04-10)

* Added:
    - Full support of external storage (SD cards, USB devices, etc.)
      This is an experimental feature, bugs are possible.
      For example, deleting downloaded files when deleting a torrent doesn't work
    - Material Design 2.0
    - Due to the change in data storage, already added torrents will be
      imported automatically. In case of failure, backup of the torrent files
      will be located in the "LibreTorrent_backup" folder of your home directory.
    - Session journal
    - "Remove duplicates" option in RSS settings
    - "Ignore free space size" option in the add dialog
    - "Anonymous mode" and "Outgoing connections for seeds" options in
      Network settings
    - Autosave changes in the torrent details window
* Bugfixing and increasing stability
* Improved support for torrents with a large number of files
* New translations:
    - Japanese
    - Turkish
    - Azerbaijani
    - Serbian
    - Hungarian
    - Korean
    - Italian
* Update to libtorrent4j 1.2.3.0

### Version 1.9.1 (2018-12-06)

* Added:
    - Swipe refresh to filemanager
* Bugfixing:
    - Blank detail window on tablets
    - Multi-window support
    - Chrome OS and Android notebooks support
* Update of Chinese translation
* Update to libtorrent4j 1.2.0.20-RC3

### Version 1.9 (2018-11-23)

* Added:
    - Streaming support (currently without DLNA).
      Streaming allows you to download and share individual files
      from a torrent (e.g VLC or browser).
      Just make a long press on the file and copy stream url
    - Support for torrents with a large files number
    - BEP53 support. More about it:
      http://www.bittorrent.org/beps/bep_0053.html
    - Show errors for each torrent
    - "Do not download immediately option" for feeds
    - Own BitTorrent fingerprint ("Lr")
    - Battery usage warning for DHT option
* Bugfixing:
    - Settings and permission dialog themes
    - Magnet adding
    - Export big feed list
    - Comments and trackers list in create torrent dialog
    - Torrent name fetching from magnet
    - Support ampersand (&) in feeds
    - Minor bugfixes
* Big update of Chinese translation
* Java 8 support
* Migrate to libtorrent4j (fork of frostwire-jlibtorrent) https://github.com/aldenml/libtorrent4j

### Version 1.8 (2018-08-02)

* Added:
    - RSS manager
    - Ability to create torrents
    - Scheduling
    - Android TV support
    - Android P support
    - More improve magnet naming
    - Expand proxy port range to 65535
    - Allow resume torrents manually if enabled power/Wi-Fi settings
    - Minor changes
* Bugfixing:
    - Peer cache list limit
    - Displayed total peers number
    - Crash after double shutdown
    - Check torrent size after change priority
    - Minor bugfixes
* Moving source code to GitLab
* Reduce size by splitting APK for each architecture (F-Droid is not yet supported)
* Update to jlibtorrent 1.2.0.18-RC10

### Version 1.7 (2018-04-16)

* Bugfixing
* Update to jlibtorrent 1.2.0.17

### Version 1.6 (2018-02-01)

* Bugfixing:
   - downloading magnet links
   - connectivity check
   - option to download and upload only while charging
   - limitation options
   - autostart on Android 8+
   - saving active and seeding time
   - minor fixes
* Added:
   - black theme (e.g. for OLED devices)
   - custom battery percentage for battery control option
   - keep alive option
   - availability info for each torrent and file
   - add infohash directly to the Add Link field
   - peers info in main window
* Update to jlibtorrent 1.2.0.16

### Version 1.5 (2017-11-28)

* Bugfixing:
   - opening torrent files from notify in some browsers
   - pause magnets with some trackers
   - laucher shortcut
   - download on the URLs with square brackets
   - port randomize
   - restore settings if data dir is deleted
   - minor fixes
* Added:
   - Android 8.1 support (NOTE: at this time, starting with the version of Android 8.0, setting notifications from the app preferences is not working, you can change them only in the settings of Android 8.0.)
   - ability to save torrent file automatically
   - Wi-Fi only option
   - new limitations options
   - possible to select between add and pause button in foreground notify
   - watch directory option
   - notify if blocklist is loaded correctly
* Update to jlibtorrent 1.2.0.15-RC2

### Version 1.4 (2017-07-08)

* Bugfixing
* Added:
   - New fetching magnet mechanism (ability to add magnet link in wait list)
   - Torrent sorting by date added
* Added new translations:
   - Czech
   - Norwegian Bokmål
   - Romanian
* Update to jlibtorrent 1.2.0.10

### Version 1.3 (2017-03-21)

* Bugfixing
* Added:
   - External storage support
   - Launcher shortcuts (for Android 7.1)
   - Allow move app to adoptable storage
* Added new translations:
   - Brazilian Portuguese
   - French
   - Greek
* Update to jlibtorrent 1.2.0.6

### Version 1.2 (2016-11-30)

* Bugfixing
* Added:
   - Torrent sorting
   - Dark theme
   - Ability to use random port
   - Forced encryption
   - Ability to add a torrent without starting
   - Redirect HTTP link support
* Added new translations:
   - Chinese
   - Spanish
   - Polish
* Update to jlibtorrent 1.2.0.4

### Version 1.1 (2016-10-23)

* Bugfixing
* Removed Tor (by popular demand)
* Added new translations:
   - German
   - Lithuanian
* Set 65534 as maximum port

### Version 1.0 (2016-10-18)

First release.
