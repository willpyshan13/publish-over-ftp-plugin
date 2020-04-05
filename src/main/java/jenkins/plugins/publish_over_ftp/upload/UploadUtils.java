package jenkins.plugins.publish_over_ftp.upload;

import com.alibaba.fastjson.JSONObject;
import jenkins.plugins.publish_over_ftp.BapFtpClient;
import jenkins.plugins.publish_over_ftp.utils.HttpUtils;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class UploadUtils {
    private static final Log logger = LogFactory.getLog(BapFtpClient.class);

    /**
     * 上传应用信息
     * @param uploadUrl
     * @param appName
     * @param version
     * @param clientId
     * @param logoUrl
     * @param channel
     * @param platform
     * @param downloadUrl
     * @param updateLog
     */
    public static String uploadInfo(String uploadUrl,String appName,String version,String clientId,String logoUrl,
    String channel,String platform,String downloadUrl,String updateLog){
        PostMethod post = new PostMethod(uploadUrl);
        UploadEntity entity = new UploadEntity(appName,version,clientId,logoUrl,channel,platform,downloadUrl,updateLog);
        String token = "";
        try {
            post.setRequestEntity(new StringRequestEntity(JSONObject.toJSON(entity).toString(), "application/json", "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            System.out.println("build request error      ========" + e);
            logger.error("build request error", e);
        }
        try {
            HttpUtils.getHttpClient().executeMethod(post);
            JSONObject jsonObject = JSONObject.parseObject(post.getResponseBodyAsString());
            logger.info(jsonObject.getString("msg"));
            token =  jsonObject.getString("msg");
        } catch (IOException e) {
            System.out.println("send msg error      ========" + e);
            logger.error("send msg error", e);
        }
        post.releaseConnection();

        return token;
    }

    public static class UploadEntity{
        public String appName;
        public String version;
        public String clientId;
        public String logoUrl;
        public String channel;
        public String platform;
        public String downloadUrl;
        public String updateLog;

        public UploadEntity(String appName, String version, String clientId, String logoUrl, String channel, String platform, String downloadUrl, String updateLog) {
            this.appName = appName;
            this.version = version;
            this.clientId = clientId;
            this.logoUrl = logoUrl;
            this.channel = channel;
            this.platform = platform;
            this.downloadUrl = downloadUrl;
            this.updateLog = updateLog;
        }
    }
}
