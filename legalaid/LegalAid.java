package emspishak.nypd.legalaid;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

public final class LegalAid {

  private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

  private static final Pattern CCRB_ID = Pattern.compile("(\\d{9})");

  public static void main(String[] args) throws CmdLineException, IOException {
    new LegalAid().doMain(args);
  }

  private void doMain(String[] args) throws CmdLineException, IOException {
    CmdLineParser parser = new CmdLineParser(this);
    parser.parseArgument(args);

    Scanner in = new Scanner(System.in);
    Map<String, String> urls = new LinkedHashMap<>();

    String url =
        "https://api.www.documentcloud.org/api/documents/search/?organization=2723&q=%20%22ccrb%20investigative%20recommendation%22%20%22case%20summary%22&version=2.0&format=json";
    while (url != null) {
      JSONObject json = fetchJson(url);
      JSONArray docs = json.getJSONArray("results");
      for (int i = 0; i < docs.length(); i++) {
        JSONObject doc = docs.getJSONObject(i);
        Matcher m = CCRB_ID.matcher(doc.getString("title"));

        String id;
        String docUrl = doc.getString("canonical_url");
        if (m.find()) {
          id = m.group(1);
        } else {
          System.out.printf("Enter ID for %s : ", docUrl);
          id = in.nextLine();
        }

        if (urls.containsKey(id)) {
          System.out.printf(
              "duplicate for %s: %s and %s, enter URL to use: ", id, urls.get(id), docUrl);
          docUrl = in.nextLine();
        }
        urls.put(id, docUrl);
      }

      if (json.isNull("next")) {
        url = null;
      } else {
        url = json.getString("next");
      }
    }

    for (Map.Entry<String, String> doc : urls.entrySet()) {
      System.out.println("    {");
      System.out.printf("      url: '%s',%n", doc.getValue());
      System.out.println("      title: 'Complaint Closing Report',");
      System.out.printf("      complaint: '%s'%n", doc.getKey());
      System.out.println("    },");
    }
  }

  private static JSONObject fetchJson(String url) throws IOException {
    GenericUrl gurl = new GenericUrl(url);
    HttpRequest request = HTTP_TRANSPORT.createRequestFactory().buildGetRequest(gurl);
    HttpResponse response = request.execute();

    return new JSONObject(response.parseAsString());
  }
}
