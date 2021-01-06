# GooglePlayConsoleUploader

## About
**GooglePlayConsoleUploader** is a command-line tool primarily designed for automated uploading of APKs, AABs and deobfuscation files. In addition, it allows you to release uploaded version to internal track.

This tool is using *AndroidPublisher v3 API*. You can read more about it [here](https://developers.google.com/android-publisher).

Tool currently supports only service account authentication with a json service account key file.

Tool is transaction based so if anything fails all changes will be discarded.

## Setup service account
##### Create new service account
Go to [Service Accounts](https://console.cloud.google.com/identity/serviceaccounts) section in *Google Cloud Platform* and create new *Service Account*.
##### Generate new json service account key file
Select created service account and under Key section create new private json key file.
##### Assign role for a service account
Go to [IAM](https://console.cloud.google.com/access/iam) section in *Google Cloud Platform*, select service account and assign it *Project > Editor* role.
##### Assign permissions for a service account in Google Play Console
Go to [API Access](https://play.google.com/console/api-access) section in *Google Play Console*, select service account under *Service Accounts* section and assign permissions for each project you want to manage with this service account.

## GooglePlayConsoleUploader options

**GooglePlayConsoleUploader** has few option which can be combined and they are executed in following order:
- Print tool version to stdout and exit
- Upload APK or AAB
- Upload deobfuscation file for specified version
- Release specified version to internal track

##### Print tool version to stdout and exit
```bash
-version
```
###### Credentials and core arguments
```bash
-serviceAccountKeyFile "path/to/serviceAccountKeyFile.json"
-httpTimeout 300 #Http timeout in seconds (default: 120)
-packageName "com.test.TestApp" #Application id of your app
-versionCode 123 #Current application version code
```

###### Upload APK to Google Play Console
```bash
-apkFile "/path/to/app.apk"
```

###### Upload AAB to Google Play Console
```bash
-aabFile "/path/to/app.aab"
-ackBundleInstallationWarning #Must be set if the bundle installation may trigger a warning on user devices
```

###### Upload symbols and assign them to specified version
```bash
-deobfuscationFile "/path/to/app.symbols.zip"
-deobfuscationFileType "nativeCode" #One of [nativeCode | proguard | deobfuscationFileTypeUnspecified]
-skipDeobfuscationFileUploadWhenFileSizeLimitIsExceeded #Must be set if you want to skip deobfuscation file upload which will probably fail. Limit is set to 300MB
```

###### Release specified version to internal track
```bash
-releaseToInternalTrack
-releaseNotesFile "path/to/releaseNotes.txt" #Release notes for the release in 'en-US' localization (default: null)
```

## Releases
Latest release: [1.1.0](https://github.com/chorobochrontochor/GooglePlayConsoleUploader/releases)