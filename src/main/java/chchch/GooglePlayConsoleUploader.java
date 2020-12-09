package chchch;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.AndroidPublisherScopes;
import com.google.api.services.androidpublisher.model.AppEdit;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

public class GooglePlayConsoleUploader
{
    public static String ContentType = "application/octet-stream";

    public static String DeobfuscationFileType_NativeCode = "nativeCode";
    public static String DeobfuscationFileType_Proguard = "proguard";
    public static String DeobfuscationFileType_Unspecified = "deobfuscationFileTypeUnspecified";

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
            String serviceAccountKeyFile = getArgValue(args, "-serviceAccountKeyFile", false, "");
            int httpTimeout = Integer.parseInt(getArgValue(args, "-httpTimeout", true, "120"));
            String packageName = getArgValue(args, "-packageName", false, "");
            String aab = getArgValue(args, "-aab", false, "");
            Boolean ackBundleInstallationWarning = hasArg(args, "-ackBundleInstallationWarning");
            String deobfuscationFile = getArgValue(args, "-deobfuscationFile", true, null);
            int versionCode = 0;
            String deobfuscationFileType = DeobfuscationFileType_Unspecified;
            if (deobfuscationFile != null) {
                versionCode = Integer.parseInt(getArgValue(args, "-versionCode", false, ""));
                deobfuscationFileType = getArgValue(args, "-deobfuscationFileType", true, DeobfuscationFileType_Unspecified);

                ArrayList<String> list = new ArrayList<>();
                list.add(DeobfuscationFileType_NativeCode);
                list.add(DeobfuscationFileType_Proguard);
                list.add(DeobfuscationFileType_Unspecified);
                if (!list.contains(deobfuscationFileType)) {
                    throw new Exception("Unknown deobfuscationFileType value \"" + deobfuscationFileType + "\". Available values: " + String.join(", ", list));
                }
            }
            System.out.println("Done.");

            System.out.println();
            System.out.println("Settings: ");
            System.out.println(" - serviceAccountKeyFile: *****");
            System.out.println(" - httpTimeout: " + httpTimeout);
            System.out.println(" - packageName: " + packageName);
            System.out.println(" - aab: " + aab);
            System.out.println(" - ackBundleInstallationWarning: " + ackBundleInstallationWarning);
            System.out.println(" - deobfuscationFile: " + deobfuscationFile);
            System.out.println(" - deobfuscationFileType: " + deobfuscationFileType);
            System.out.println(" - versionCode: " + versionCode);
            System.out.println();

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

            System.out.print("Creating app input stream...");
            AbstractInputStreamContent aabContent = new FileContent(ContentType, new File(aab));
            System.out.println("Done.");

            System.out.print("Uploading app to Google Play Console...");
            AndroidPublisher.Edits.Bundles.Upload upload = publisher.edits().bundles().upload(packageName, appEdit.getId(), aabContent);
            upload.setAckBundleInstallationWarning(ackBundleInstallationWarning);
            upload.execute();
            System.out.println("Done.");

            if (deobfuscationFile != null) {
                System.out.print("Uploading deobfuscation file to Google Play Console...");
                AbstractInputStreamContent nativeSymbolsContent = new FileContent(ContentType, new File(deobfuscationFile));
                AndroidPublisher.Edits.Deobfuscationfiles.Upload nativeSymbolsUpload = publisher.edits().deobfuscationfiles().upload(packageName, appEdit.getId(), versionCode, deobfuscationFileType, nativeSymbolsContent);
                nativeSymbolsUpload.execute();
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