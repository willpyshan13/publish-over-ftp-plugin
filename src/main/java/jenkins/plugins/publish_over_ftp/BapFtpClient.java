/*
 * The MIT License
 *
 * Copyright (C) 2010-2011 by Anthony Robinson
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.plugins.publish_over_ftp;

import hudson.FilePath;
import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;
import jenkins.plugins.publish_over.BPBuildInfo;
import jenkins.plugins.publish_over.BPDefaultClient;
import jenkins.plugins.publish_over.BapPublisherException;
import jenkins.plugins.publish_over_ftp.dingding.DingMessage;
import jenkins.plugins.publish_over_ftp.upload.UploadUtils;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPListParseEngine;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

public class BapFtpClient extends BPDefaultClient<BapFtpTransfer> {

    private static final Log LOG = LogFactory.getLog(BapFtpClient.class);

    private BPBuildInfo buildInfo;
    private FTPClient ftpClient;
    private boolean disableMakeNestedDirs;

    private DingMessage message;

    public BapFtpClient(final FTPClient ftpClient, final BPBuildInfo buildInfo) {
        this.ftpClient = ftpClient;
        this.buildInfo = buildInfo;
        message = new DingMessage();
    }

    public void setDisableMakeNestedDirs(final boolean disableMakeNestedDirs) {
        this.disableMakeNestedDirs = disableMakeNestedDirs;
    }

    public void setDisableRemoteVerification(final boolean disableRemoteVerification) {
        if (disableRemoteVerification) {
            ftpClient.setRemoteVerificationEnabled(false);
        }
    }

    public FTPClient getFtpClient() {
        return ftpClient;
    }

    public void setFtpClient(final FTPClient ftpClient) {
        this.ftpClient = ftpClient;
    }

    public BPBuildInfo getBuildInfo() {
        return buildInfo;
    }

    public void setBuildInfo(final BPBuildInfo buildInfo) {
        this.buildInfo = buildInfo;
    }

    public boolean changeDirectory(final String directory) {
        try {
            return ftpClient.changeWorkingDirectory(directory);
        } catch (IOException ioe) {
            throw new BapPublisherException(Messages.exception_cwdException(directory), ioe);
        }
    }

    public boolean makeDirectory(final String directory) {
        try {
            if (disableMakeNestedDirs && directory.contains("/")) return false;
            return ftpClient.makeDirectory(directory);
        } catch (IOException ioe) {
            throw new BapPublisherException(Messages.exception_mkdirException(directory), ioe);
        }
    }

    public void deleteTree() throws IOException {
        ftpClient.setListHiddenFiles(true);
        delete();
    }

    private void delete() throws IOException {
        // use the extension if available
        if (ftpClient.hasFeature("MLST")) {
            for (FTPFile file : ftpClient.mlistDir()) {
                delete(file);
            }
        } else {
            final FTPListParseEngine listParser = ftpClient.initiateListParsing();
            if (listParser == null)
                throw new BapPublisherException(Messages.exception_client_listParserNull());
            while (listParser.hasNext())
                delete(listParser.getNext(1)[0]);
        }
    }

    private void delete(final FTPFile ftpFile) throws IOException {
        if (ftpFile == null)
            throw new BapPublisherException(Messages.exception_client_fileIsNull());
        final String entryName = ftpFile.getName();
        if (".".equals(entryName) || "..".equals(entryName))
            return;
        if (ftpFile.isDirectory()) {
            if (!changeDirectory(entryName))
                throw new BapPublisherException(Messages.exception_cwdException(entryName));
            delete();
            if (!ftpClient.changeToParentDirectory())
                throw new BapPublisherException(Messages.exception_client_cdup());
            if (!ftpClient.removeDirectory(entryName))
                throw new BapPublisherException(Messages.exception_client_rmdir(entryName));
        } else {
            if (!ftpClient.deleteFile(entryName))
                throw new BapPublisherException(Messages.exception_client_dele(entryName));
        }
    }

    public void beginTransfers(final BapFtpTransfer transfer) {
        if (!transfer.hasConfiguredSourceFiles())
            throw new BapPublisherException(Messages.exception_noSourceFiles());
        try {
            if (!setTransferMode(transfer))
                throw new BapPublisherException(Messages.exception_failedToSetTransferMode(ftpClient.getReplyString()));
        } catch (IOException ioe) {
            throw new BapPublisherException(Messages.exception_exceptionSettingTransferMode(), ioe);
        }
    }

    public void transferFile(final BapFtpTransfer client, final FilePath filePath, final InputStream content) throws IOException {
        if (ftpClient.storeFile(filePath.getName(), content)) {
            if (filePath.getName().endsWith(".apk") || filePath.getName().endsWith(".ipa")) {
                String[] packageInfo = filePath.getName().split("_");
                String clientId = filePath.getName().endsWith(".apk") ? "1" : "2";
                //先上传到应用平台获取token
                if (packageInfo.length >= 5) {
                    String token = UploadUtils.uploadInfo(client.getUploadUrl(), packageInfo[0], packageInfo[1], packageInfo[0], client.getLogoUrl(), packageInfo[3], clientId, "", client.getUpdateLog());
                    //再发送消息到钉钉
                    message.sendTextMessage(packageInfo[1], client.getDingToken(), client.getUpdateLog(), client.getPerson(), client.getPlatformInfo(), token);
                }
            }
        } else {
            throw new BapPublisherException(Messages.exception_failedToStoreFile(ftpClient.getReplyString()));
        }
    }

    public void disconnect() {
        if ((ftpClient != null) && ftpClient.isConnected()) {
            try {
                ftpClient.disconnect();
            } catch (IOException ioe) {
                throw new BapPublisherException(Messages.exception_exceptionOnDisconnect(ioe.getLocalizedMessage()), ioe);
            }
        }
    }

    public void disconnectQuietly() {
        try {
            disconnect();
        } catch (Exception e) {
            LOG.warn(Messages.log_disconnectQuietly(), e);
        }
    }

    private boolean setTransferMode(final BapFtpTransfer transfer) throws IOException {
        final int fileType = transfer.isAsciiMode() ? FTP.ASCII_FILE_TYPE : FTP.BINARY_FILE_TYPE;
        return ftpClient.setFileType(fileType);
    }

}
