package chchch;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.AndroidPublisherScopes;
import com.google.api.services.androidpublisher.model.AppEdit;
import com.google.api.services.androidpublisher.model.LocalizedText;
import com.google.api.services.androidpublisher.model.Track;
import com.google.api.services.androidpublisher.model.TrackRelease;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class GooglePlayConsoleUploader
{
    public static String ContentType = "application/octet-stream";

    public static String DeobfuscationFileType_NativeCode = "nativeCode";
    public static String DeobfuscationFileType_Proguard = "proguard";
    public static String DeobfuscationFileType_Unspecified = "deobfuscationFileTypeUnspecified";

    public static long DeobfuscationFile_SizeLimit = 314572800;

    public static String Track_Internal = "internal";
    public static String TrackReleaseStatus_Completed = "completed";
    public static String LocalizedText_enUS = "en-US";

    private static String getAppName()
    {
        return GooglePlayConsoleUploader.class.getPackage().getImplementationTitle();
    }
    private static String getAppVersion()
    {
        return GooglePlayConsoleUploader.class.getPackage().getImplementationVersion();
    }
    private static boolean hasArg(String[] args, String flag)
    {
        for (String arg : args) {
            if (arg.equals(flag)) {
                return true;
            }
        }
        return false;
    }
    private static String getArgValue(String[] args, String flag, boolean optional, String defaultValue) throws Exception
    {
        boolean nextIsValue = false;
        for (String arg : args) {
            if (nextIsValue) {
                return arg;
            }
            if (arg.equals(flag)) {
                nextIsValue = true;
            }
        }
        if (optional) return defaultValue;

        if (nextIsValue) {
            throw new Exception("Value for mandatory argument \"" + flag + "\" is missing!");
        } else {
            throw new Exception("Mandatory argument \"" + flag + "\" is missing!");
        }
    }
    private static HttpRequestInitializer setHttpTimeout(final HttpRequestInitializer requestInitializer, int timeoutSecs)
    {
        return httpRequest -> {
            requestInitializer.initialize(httpRequest);
            httpRequest.setConnectTimeout(timeoutSecs * 1000);
            httpRequest.setReadTimeout(timeoutSecs * 1000);
        };
    }
    public static void main(String[] args)
    {
        try
        {
            System.out.println(getAppName() + ": v" + getAppVersion());
            if (hasArg(args, "-version")) {
                System.exit(0);
            }

            System.out.print("Processing command line arguments...");
            String serviceAccountKeyFile = getArgValue(args, "-serviceAccountKeyFile", false, null);
            int httpTimeout = Integer.parseInt(getArgValue(args, "-httpTimeout", true, "120"));
            String packageName = getArgValue(args, "-packageName", false, null);
            Long versionCode = Long.parseLong(getArgValue(args, "-versionCode", false, ""));
            String aabFile = getArgValue(args, "-aabFile", true, null);
            String apkFile = getArgValue(args, "-apkFile", true, null);
            boolean ackBundleInstallationWarning = hasArg(args, "-ackBundleInstallationWarning");
            String nativeDebugSymbolsFile = getArgValue(args, "-nativeDebugSymbolsFile", true, null);
            String reTraceMappingFile = getArgValue(args, "-reTraceMappingFile", true, null);
            boolean skipDeobfuscationFileUploadWhenFileSizeLimitIsExceeded = false;
            if (nativeDebugSymbolsFile != null || reTraceMappingFile != null) {
                skipDeobfuscationFileUploadWhenFileSizeLimitIsExceeded = hasArg(args, "-skipDeobfuscationFileUploadWhenFileSizeLimitIsExceeded");
            }
            boolean releaseToInternalTrack = hasArg(args, "-releaseToInternalTrack");
            String releaseNotesFile = null;
            if (releaseToInternalTrack) {
                releaseNotesFile = getArgValue(args, "-releaseNotesFile", true, null);
            }
            System.out.println("Done.");

            System.out.println();
            System.out.println("Settings: ");
            System.out.println(" - serviceAccountKeyFile: *****");
            System.out.println(" - httpTimeout: " + httpTimeout);
            System.out.println(" - packageName: " + packageName);
            System.out.println(" - versionCode: " + versionCode);
            System.out.println(" - aabFile: " + aabFile);
            System.out.println(" - apkFile: " + apkFile);
            System.out.println(" - ackBundleInstallationWarning: " + ackBundleInstallationWarning);
            System.out.println(" - nativeDebugSymbolsFile: " + nativeDebugSymbolsFile);
            System.out.println(" - reTraceMappingFile: " + reTraceMappingFile);
            if (nativeDebugSymbolsFile != null || reTraceMappingFile != null) {
                System.out.println(" - skipDeobfuscationFileUploadWhenFileSizeLimitIsExceeded: " + skipDeobfuscationFileUploadWhenFileSizeLimitIsExceeded);
            }
            System.out.println(" - releaseToInternalTrack: " + releaseToInternalTrack);
            if (releaseToInternalTrack) {
                System.out.println(" - releaseNotesFile: " + releaseNotesFile);
            }
            System.out.println();

            if (aabFile != null && apkFile != null) {
                throw new Exception("Uploading aab and apk would result in conflict. Provide only one of following arguments: aab, apk");
            }
            if (aabFile == null && apkFile == null && nativeDebugSymbolsFile == null && reTraceMappingFile == null) {
                throw new Exception("Nothing to upload. Provide at least one of following arguments: aab, apk, nativeDebugSymbolsFile, reTraceMappingFile");
            }

            System.out.print("Loading service account credentials...");
            GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(serviceAccountKeyFile)).createScoped(AndroidPublisherScopes.ANDROIDPUBLISHER);
            System.out.println("Done.");

            System.out.print("Initializing android publisher...");
            AndroidPublisher publisher = new AndroidPublisher.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JacksonFactory.getDefaultInstance(),
                setHttpTimeout(new HttpCredentialsAdapter(credentials), httpTimeout)
            ).setApplicationName(getAppName()).build();
            System.out.println("Done.");

            System.out.print("Creating app edit...");
            AppEdit appEdit = publisher.edits().insert(packageName, null).execute();
            System.out.println("Done. AppEdit: " + appEdit.getId());

            if (aabFile != null) {
                System.out.print("Initializing aab input stream...");
                AbstractInputStreamContent aabContent = new FileContent(ContentType, new File(aabFile));
                System.out.println("Done.");

                System.out.print("Uploading aab to Google Play Console...");
                AndroidPublisher.Edits.Bundles.Upload upload = publisher.edits().bundles().upload(packageName, appEdit.getId(), aabContent);
                upload.setAckBundleInstallationWarning(ackBundleInstallationWarning);
                upload.execute();
                System.out.println("Done.");
            }

            if (apkFile != null) {
                System.out.print("Initializing apk input stream...");
                AbstractInputStreamContent apkContent = new FileContent(ContentType, new File(apkFile));
                System.out.println("Done.");

                System.out.print("Uploading apk to Google Play Console...");
                AndroidPublisher.Edits.Apks.Upload upload = publisher.edits().apks().upload(packageName, appEdit.getId(), apkContent);
                upload.execute();
                System.out.println("Done.");
            }

            if (nativeDebugSymbolsFile != null) {
                System.out.print("Checking native debug symbols file size limit...");
                File nativeDebugSymbolsFileFile = new File(nativeDebugSymbolsFile);
                boolean fileSizeLimitIsExceeded = nativeDebugSymbolsFileFile.length() >= DeobfuscationFile_SizeLimit;
                if (fileSizeLimitIsExceeded) {
                    System.out.print("Native debug symbols file size limit is exceeded! ");
                    System.err.print("Native debug symbols file size limit is exceeded! ");

                    if (skipDeobfuscationFileUploadWhenFileSizeLimitIsExceeded) {
                        System.out.print("Skipping...! ");
                    }
                }
                System.out.println("Done.");

                if (!fileSizeLimitIsExceeded || !skipDeobfuscationFileUploadWhenFileSizeLimitIsExceeded) {
                    System.out.print("Initializing native debug symbols file input stream...");
                    AbstractInputStreamContent nativeDebugSymbolsFileContent = new FileContent(ContentType, nativeDebugSymbolsFileFile);
                    System.out.println("Done.");
                    System.out.print("Uploading native debug symbols file to Google Play Console...");
                    AndroidPublisher.Edits.Deobfuscationfiles.Upload nativeSymbolsUpload = publisher.edits().deobfuscationfiles().upload(packageName, appEdit.getId(), (int) (long) versionCode, DeobfuscationFileType_NativeCode, nativeDebugSymbolsFileContent);
                    nativeSymbolsUpload.execute();
                    System.out.println("Done.");
                }
            }

            if (reTraceMappingFile != null) {
                System.out.print("Checking ReTrace mapping file size limit...");
                File reTraceMappingFileFile = new File(reTraceMappingFile);
                boolean fileSizeLimitIsExceeded = reTraceMappingFileFile.length() >= DeobfuscationFile_SizeLimit;
                if (fileSizeLimitIsExceeded) {
                    System.out.print("Native ReTrace mapping file size limit is exceeded! ");
                    System.err.print("Native ReTrace mapping file size limit is exceeded! ");

                    if (skipDeobfuscationFileUploadWhenFileSizeLimitIsExceeded) {
                        System.out.print("Skipping...! ");
                    }
                }
                System.out.println("Done.");

                if (!fileSizeLimitIsExceeded || !skipDeobfuscationFileUploadWhenFileSizeLimitIsExceeded) {
                    System.out.print("Initializing ReTrace mapping file input stream...");
                    AbstractInputStreamContent reTraceMappingFileContent = new FileContent(ContentType, reTraceMappingFileFile);
                    System.out.println("Done.");
                    System.out.print("Uploading ReTrace mapping file to Google Play Console...");
                    AndroidPublisher.Edits.Deobfuscationfiles.Upload reTraceMappingUpload = publisher.edits().deobfuscationfiles().upload(packageName, appEdit.getId(), (int) (long) versionCode, DeobfuscationFileType_Proguard, reTraceMappingFileContent);
                    reTraceMappingUpload.execute();
                    System.out.println("Done.");
                }
            }

            if (releaseToInternalTrack) {
                System.out.print("Initializing internal track...");
                Track track = new Track();
                TrackRelease trackRelease = new TrackRelease();
                if (releaseNotesFile != null) {
                    File file = new File(releaseNotesFile);
                    FileInputStream fis = new FileInputStream(file);
                    byte[] data = new byte[(int) file.length()];
                    fis.read(data);
                    fis.close();

                    String releaseNotesText = new String(data, StandardCharsets.UTF_8);
                    LocalizedText localizedText = new LocalizedText();
                    localizedText.setLanguage(LocalizedText_enUS);
                    localizedText.setText(releaseNotesText);
                    trackRelease.setReleaseNotes(new ArrayList<LocalizedText>() {{
                        add(localizedText);
                    }});
                }
                trackRelease.setVersionCodes(new ArrayList<Long>() {{
                    add(versionCode);
                }});
                trackRelease.setStatus(TrackReleaseStatus_Completed);
                track.setReleases(new ArrayList<TrackRelease>() {{
                   add(trackRelease);
                }});
                System.out.println("Done.");
                System.out.print("Updating internal track...");
                publisher.edits().tracks().update(packageName, appEdit.getId(), Track_Internal, track).execute();
                System.out.println("Done.");
            }

            System.out.print("Validating edit...");
            publisher.edits().validate(packageName, appEdit.getId()).execute();
            System.out.println("Done.");

            System.out.print("Committing edit...");
            publisher.edits().commit(packageName, appEdit.getId()).execute();
            System.out.println("Done.");

            System.exit(0);
        }
        catch(Exception exception)
        {
            System.out.println("Failed.");
            System.err.println("Error: " + exception.getMessage());
            System.exit(1);
        }
    }
}