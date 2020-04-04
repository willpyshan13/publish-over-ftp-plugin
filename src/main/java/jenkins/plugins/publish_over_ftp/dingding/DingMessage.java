package jenkins.plugins.publish_over_ftp.dingding;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;
import jenkins.plugins.publish_over_ftp.BapFtpClient;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;

public class DingMessage {

    SimpleDateFormat smf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private static final Log logger = LogFactory.getLog(BapFtpClient.class);

    private static final String apiUrl = "https://oapi.dingtalk.com/robot/send?access_token=";

    private String api;


    private HttpClient getHttpClient() {
        HttpClient client = new HttpClient();
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null && jenkins.proxy != null) {
            ProxyConfiguration proxy = jenkins.proxy;
            if (proxy != null && client.getHostConfiguration() != null) {
                client.getHostConfiguration().setProxy(proxy.name, proxy.port);
                String username = proxy.getUserName();
                String password = proxy.getPassword();
                // Consider it to be passed if username specified. Sufficient?
                if (username != null && !"".equals(username.trim())) {
                    logger.info("Using proxy authentication (user=" + username + ")");
                    client.getState().setProxyCredentials(AuthScope.ANY,
                            new UsernamePasswordCredentials(username, password));
                }
            }
        }
        return client;
    }

    public void sendTextMessage(String msg, String token,String project) {
        api = apiUrl + token;
        HttpClient client = getHttpClient();
        PostMethod post = new PostMethod(api);

        JSONObject body = new JSONObject();
        body.put("msgtype", "text");
        body.put("isAtAll", false);

        JSONObject contentObject = new JSONObject();
        MessageMarkdown messageMarkdown = new MessageMarkdown();
        messageMarkdown.content = "properties[0]" + "\n" +
                "下载地址\nhttps://app.vvtechnology.cn:8024/dist/android/VVLife/html/" + msg + "\n"
                + "版本：" + "properties[1]" + "\n"
                + "时间：" + smf.format(System.currentTimeMillis()) + "\n" +
                "更新内容：" + project;

        contentObject.put("content", messageMarkdown.content);
        body.put("text", contentObject);

        JSONObject atObject = new JSONObject();
        atObject.put("atMobiles", "15980957597");
        body.put("at", atObject);

        try {
            post.setRequestEntity(new StringRequestEntity(body.toJSONString(), "application/json", "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            System.out.println("build request error      ========" + e);
            logger.error("build request error", e);
        }
        try {
            client.executeMethod(post);
            logger.info(post.getResponseBodyAsString());
        } catch (IOException e) {
            System.out.println("send msg error      ========" + e);
            logger.error("send msg error", e);
        }
        post.releaseConnection();
    }

    class MessageMarkdown{
        String title;
        String content;
    }

}
