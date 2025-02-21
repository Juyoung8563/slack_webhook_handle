import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Webhook {
    public static void main(String[] args) {
        String prompt = System.getenv("LLM_PROMPT");
        String llmResult = useLLM(prompt); // 환경변수화
        System.out.println("llmResult = " + llmResult);
        String template = System.getenv("LLM2_IMAGE_TEMPLATE");
        String imagePrompt = template.formatted(llmResult);
        System.out.println("imagePrompt = " + imagePrompt);
        String llmImageResult = useLLMForImage(imagePrompt);
        System.out.println("llmImageResult = " + llmImageResult); // 발송은 안함
        String title = System.getenv("SLACK_WEBHOOK_TITLE");
        sendSlackMessage(title, llmResult, llmImageResult);
    }

    public static String useLLMForImage(String prompt) {
    String apiUrl = System.getenv("LLM2_API_URL"); // 환경변수로 관리
    String apiKey = System.getenv("LLM2_API_KEY"); // 환경변수로 관리
    String model = System.getenv("LLM2_MODEL"); // 환경변수로 관리
    if(model == null || model.isBlank()){
        System.out.println("Warning: LLM2_MODEL is not set. Using default model 'black-forest-labs/FLUX.1-schnell-Free'.");
        model = "black-forest-labs/FLUX.1-schnell-Free";
    }
    String payload = """
            {
              "prompt": "%s",
              "model": "%s",
              "width": 1440,
              "height": 1440,
              "steps": 4,
              "n": 1
            }
            """.formatted(prompt, model);
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();
    String result = null;
    try {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("response.statusCode() = " + response.statusCode());
        System.out.println("response.body() = " + response.body());
        String responseBody = response.body();
        // 에러 응답이 포함된 경우 체크
        if(responseBody.contains("\"error\"")){
            throw new RuntimeException("LLM2 API error: " + responseBody);
        }
        if(!responseBody.contains("url\": \"")){
            throw new RuntimeException("Unexpected response format from LLM2 API: " + responseBody);
        }
        // 응답 파싱
        result = responseBody.split("url\": \"")[1].split("\",")[0];
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
    return result;
}


    public static String useLLM(String prompt) {

        // 이름 바꾸기 -> 해당 메서드 내부? 클래스를 기준하다면 그 내부만 바꿔줌
        String apiUrl = System.getenv("LLM_API_URL"); // 환경변수로 관리
        String apiKey = System.getenv("LLM_API_KEY"); // 환경변수로 관리
        String model = System.getenv("LLM_MODEL"); // 환경변수로 관리
//        String payload = "{\"text\": \"" + prompt + "\"}";
        String payload = """
                {
                  "messages": [
                    {
                      "role": "user",
                      "content": "%s"
                    }
                  ],
                  "model": "%s"
                }
                """.formatted(prompt, model);
        HttpClient client = HttpClient.newHttpClient(); // 새롭게 요청할 클라이언트 생성
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl)) // URL을 통해서 어디로 요청을 보내는지 결정
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build(); // 핵심
        String result = null; // return을 하려면 일단은 할당이 되긴 해야함
        // 그래서 null으로라도 초기화를 해놓습니다.
        try { // try
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            System.out.println("response.statusCode() = " + response.statusCode());
            System.out.println("response.body() = " + response.body());
            result = response.body()
                    .split("\"content\":\"")[1]
                    .split("\"},\"logprobs\"")[0]; // 글씨 패턴 -> 스페이스나 escape 처리... 오타... 있음.
            // 그걸 너무 무서워하지말고...
            // https://regexr.com/
        } catch (Exception e) { // catch exception e
            throw new RuntimeException(e);
        }
//        return null; // 메서드(함수)가 모두 처리되고 나서 이 값을 결과값으로 가져서 이걸 대입하거나 사용할 수 있다
        return result; // 앞뒤를 자르고 우리에게 필요한 내용만 리턴
    }

    //    public static void sendSlackMessage(String text) {
    public static void sendSlackMessage(String title, String text, String imageUrl) {
        // 다시 시작된 슬랙 침공
//        String slackUrl = "https://hooks.slack.com/services/";
        String slackUrl = System.getenv("SLACK_WEBHOOK_URL"); // 환경변수로 관리
//        String payload = "{\"text\": \"채널에 있는 한 줄의 텍스트입니다.\\n또 다른 한 줄의 텍스트입니다.\"}";
//        String payload = "{\"text\": \"" + text + "\"}";
        // slack webhook attachments -> 검색 혹은 LLM
        String payload = """
                    {"attachments": [{
                        "title": "%s",
                        "text": "%s",
                        "image_url": "%s"
                    }]}
                """.formatted(title, text, imageUrl);
        // 마치 브라우저나 유저인 척하는 것.
        HttpClient client = HttpClient.newHttpClient(); // 새롭게 요청할 클라이언트 생성
        // 요청을 만들어보자! (fetch)
        HttpRequest request = HttpRequest.newBuilder()
                // 어디로? URI(URL) -> Uniform Resource Identifier(Link)
                .uri(URI.create(slackUrl)) // URL을 통해서 어디로 요청을 보내는지 결정
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build(); // 핵심

        // 네트워크 과정에서 오류가 있을 수 있기에 선제적 예외처리
        try { // try
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            // 2는 뭔가 됨. 4,5 뭔가 잘못 됨. 1,3? 이런 건 없어요. 1은 볼 일이 없고요. 3은... 어...
            System.out.println("response.statusCode() = " + response.statusCode());
            System.out.println("response.body() = " + response.body());
        } catch (Exception e) { // catch exception e
            throw new RuntimeException(e);
        }
    }
}
