import java.io.DataOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import static java.lang.System.getenv;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;

public class Main {
  public static void main(String[] args){
    var url_env = getenv("ROLLUP_HTTP_SERVER_URL");

    System.out.println("Hello Cartesi!");

    while(true) {
      HttpURLConnection con = null;
      var json = "{\"status\": \"accept\"}";

      try {
        URL url = new URL(url_env + "/finish");
        con = (HttpURLConnection) url.openConnection();
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestMethod("POST");

        // Enable input/output streams for this connection
        con.setDoInput(true);
        con.setDoOutput(true);

        OutputStream os = con.getOutputStream();
        OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");

        // Write the payload to the connection's output stream
        try (var outputStream = new DataOutputStream(con.getOutputStream())) {
          outputStream.writeBytes(json);
          outputStream.flush();
        }

        // Get the response code
        int responseCode = con.getResponseCode();
        System.out.println("Response Code: " + responseCode);

        if(responseCode == 202) {
          System.out.println("No pending rollup request, trying again");
          con.disconnect();
          continue;
        }

        if(responseCode != 200) {
          System.out.println("Error: " + responseCode);
          con.disconnect();
          continue;
        }

        // Read the response from the server
        try (var reader = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
          var response = new StringBuilder();
          String line;
          while ((line = reader.readLine()) != null) {
            response.append(line);
          }
          System.out.println("Response from server: " + response.toString());

          var jsonString = response.toString();
          var map = jsonStringToHashMap(jsonString);
          var requestType = map.get("request_type");
          switch (requestType) {
            case "advance_state":
              var advanceResponse = handleAdvance(map.get("data"));
              json = "{\"status\": \"" + advanceResponse + "\"}";
              break;
            case "inspect_state":
              var inspectResponse = handleInspect(map.get("data"));
              json = "{\"status\": \"" + inspectResponse + "\"}";
              break;
            default:
              System.out.println("Unknown request type: " + requestType);
              break;
          }
          con.disconnect();
        }
      } catch (Exception e) {
          if (con != null) {
              con.disconnect();
          }

          System.out.println("Error: " + e.getMessage());
      }
    }
  }

  private static String handleAdvance(String data) {
    System.out.println("Handling advance request");
    return "accept";
  }

  private static String handleInspect(String data) {
    System.out.println("Handling inspect request");
    return "accept";
  }

  private static HashMap<String, String> jsonStringToHashMap(String jsonString) {
    // Remove leading and trailing curly braces
    jsonString = jsonString.substring(1, jsonString.length() - 1);

    // Split the JSON string into key-value pairs
    String[] keyValuePairs = jsonString.split(",");

    // Create a new HashMap
    var map = new HashMap<String, String>();

    // Process each key-value pair
    for(String pair : keyValuePairs) {
        // Split the key-value pair into key and value
        String[] entry = pair.split(":");
        String key = entry[0].trim();
        String value = entry[1].trim();

        // Remove leading and trailing double quotes from the key
        key = key.substring(1, key.length() - 1);

        // Remove leading and trailing double quotes from the value
        value = value.substring(1, value.length() - 1);

        // Add the key and value to the map
        map.put(key, value);
    }

    return map;
  }
}
